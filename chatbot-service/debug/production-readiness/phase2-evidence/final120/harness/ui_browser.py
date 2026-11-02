"""Actual Chrome UI audit. Controlled browser response faults are explicitly labelled."""
import asyncio,json,os,sys,uuid,time,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[6];sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import *
from runtime import login,password,public
from playwright.async_api import async_playwright,expect
from app.features.conversation.postgres_repository import PostgresConversationRepository
from app.features.customer_context.models import AuthenticatedCustomer

PLAN=[
 ('UI01','Anonymous /chatbot-lab redirects to login; synthetic CUSTOMER can log in with actual UI'),
 ('UI02','Chatbot Lab accessible directly; status shows real AI and PostgreSQL'),
 ('UI03','Actual product POST renders valid card/price/RAM/storage/SKU/stock and answer'),
 ('UI04','Product card navigates to real detail page'),
 ('UI05','DEMO Knowledge source shown as DEMO; absent official instructions explained'),
 ('UI06','Fresh session, owned session switch, closed session are usable'),
 ('UI07','History reload and cursor pagination deduplicate persisted text; no invented cards'),
 ('UI08','Session list pagination advances exactly one page'),
 ('UI09','503 before persistence displays error, preserves draft, safe retry has one displayed USER'),
 ('UI10','Switching sessions while POST pending cannot overwrite selected history'),
 ('UI11','Double-submit while pending sends one POST; loading state visible'),
 ('UI12','Feedback controls call real endpoint and show selected state'),
 ('UI13','Developer trace panel loads real trace and filters events'),
 ('UI14','JSON export downloads correct synthetic transcript'),
 ('UI15','Unknown/malformed 200 ChatResponse is handled without uncaught UI crash'),
 ('UI16','Mobile390/tablet768/desktop1440 render without horizontal page overflow'),
 ('UI17','Basic labelled input, log live region and keyboard submit usable'),
 ('UI18','No unexpected browser page errors during normal UI flows'),
 ('UI19','Storefront navigation/widget availability is independently inventoried, not assumed'),
]
dump(OUT/'evidence/ui/plan.json',[{'id':id,'expected':expected} for id,expected in PLAN])
CLIENT,ME=login();CUSTOMER=AuthenticatedCustomer.from_backend_user(ME);STATE=load(PRIVATE/'state.json');repo=PostgresConversationRepository(STATE['dsn'])
# Approved synthetic conversation-only fixtures, all in disposable database.
long=repo.create_session(CUSTOMER.customer_id)
for i in range(53):repo.add_message(long.id,'USER',f'UAT cursor question {i}',CUSTOMER.customer_id);repo.save_assistant(long.id,CUSTOMER.customer_id,f'UAT cursor answer {i}',None)
for _ in range(23):repo.create_session(CUSTOMER.customer_id)
dump(OUT/'evidence/ui/fixtures.json',{'scope':'DISPOSABLE_POSTGRES_ONLY','cursor_rows':106,'additional_empty_sessions':23,'business_records_modified':False})
rows=[];screens=[];errors=[];console=[];current_test='normal'
async def main():
 global current_test
 async with async_playwright() as pw:
  browser=await pw.chromium.launch(executable_path='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless=True)
  context=await browser.new_context(viewport={'width':1440,'height':1000},accept_downloads=True);page=await context.new_page()
  page.on('pageerror',lambda err:errors.append({'case':current_test,'message':scrub(str(err))[:600]}))
  page.on('console',lambda msg:console.append({'case':current_test,'type':msg.type,'message':scrub(msg.text)[:350]}) if msg.type in ['error','warning'] else None)
  async def check(id,body,mode='REAL_BROWSER_REAL_STACK'):
   global current_test
   current_test=id;start=now();t=time.monotonic()
   try:
    detail=await body();row={'id':id,'expected':dict(PLAN)[id],'status':'PASS','execution':mode,'observations':detail or {}}
   except Exception as exc:
    row={'id':id,'expected':dict(PLAN)[id],'status':'FAIL','execution':mode,'error_type':type(exc).__name__,'message':scrub(str(exc))[:1000]}
    shot=OUT/f'evidence/ui/{id}-failure.png';await page.screenshot(path=str(shot),full_page=True);row['screenshot']=str(shot.relative_to(OUT))
   row.update(started_at=start,duration_ms=round((time.monotonic()-t)*1000,2));rows.append(row);dump(OUT/'ui_inventory.json',public(rows));print(id,row['status'],flush=True)
  async def ready():
   await expect(page.get_by_label('Câu hỏi của bạn')).to_be_enabled(timeout=20000)
  async def fresh():
   await page.get_by_role('button',name='Cuộc trò chuyện mới',exact=True).click();await ready()
  async def send(prompt):
   before=await page.locator('.lab-message-assistant').count()
   await page.get_by_label('Câu hỏi của bạn').fill(prompt)
   async with page.expect_response(lambda r:'/api/v1/chat/sessions/' in r.url and r.request.method=='POST' and r.url.endswith('/messages'),timeout=180000) as response:
    await page.get_by_role('button',name='Gửi câu hỏi',exact=True).click()
   r=await response.value;assert r.status==200
   await expect(page.locator('.lab-message-assistant')).to_have_count(before+1,timeout=20000);await ready();return await r.json()
  async def login_ui():
   await page.goto('http://localhost:5174/chatbot-lab');await expect(page).to_have_url(re.compile('/login'))
   await page.get_by_label('Tên đăng nhập',exact=True).fill('seed.customer1');await page.get_by_label('Mật khẩu',exact=True).fill(password())
   await page.get_by_role('button',name='Đăng nhập',exact=True).click();await expect(page).to_have_url(re.compile('/chatbot-lab'));await ready()
   return {'anonymous_redirect':'/login','login_form_real':True,'authenticated_route':'/chatbot-lab'}
  await check('UI01',login_ui)
  async def status():
   await expect(page.get_by_text('AI thật',exact=True)).to_be_visible();await expect(page.get_by_text('Lịch sử lưu trong PostgreSQL',exact=True)).to_be_visible();return {'ai_real':True,'storage':'postgres'}
  await check('UI02',status)
  async def product():
   result=await send('Xiaomi 15 bản RAM 16GB, dung lượng 512GB còn hàng không?');card=page.locator('.lab-product').last
   await expect(card).to_contain_text('DEV-X15-GRN-512');await expect(card).to_contain_text('16GB');await expect(card).to_contain_text('512GB');await expect(card).to_contain_text('Tồn kho: 2')
   assert result['products'][0]['sku']=='DEV-X15-GRN-512';return {'card':public(result['products'][0]),'message':result['message']}
  await check('UI03',product)
  async def navigation():
   href=await page.locator('.lab-product a').last.get_attribute('href');await page.locator('.lab-product a').last.click();await expect(page).to_have_url(re.compile('/products/'));await expect(page.locator('h1')).to_contain_text('Xiaomi 15',timeout=20000)
   await page.goto('http://localhost:5174/chatbot-lab');await ready();return {'href':href,'real_product_detail_loaded':True}
  await check('UI04',navigation)
  async def knowledge():
   result=await send('Theo tài liệu DEMO, điều kiện đổi trả trong bao nhiêu ngày?');assert result['sources'] and 'DEMO' in result['sources'][0]['title'];await expect(page.locator('.lab-sources').last).to_contain_text('DEMO')
   guideline=await send('Giúp tôi xem cách hủy đơn hàng.');assert 'chưa có tài liệu chính thức' in guideline['message'];return {'demo_sources':result['sources'],'guidance_message':guideline['message']}
  await check('UI05',knowledge)
  async def sessions():
   await fresh();await send('Xin chào');await page.get_by_role('button',name='Đóng phiên',exact=True).click();await expect(page.get_by_label('Câu hỏi của bạn')).to_be_disabled();await expect(page.get_by_text('Phiên đã đóng. Bạn có thể đọc lịch sử hoặc tạo phiên mới.',exact=True)).to_be_visible()
   await fresh();target=str(long.id)
   # Direct page reload selects a real owned fixture outside the first list page.
   await page.evaluate('({key,id})=>sessionStorage.setItem(key,id)',{'key':f'chatbot-lab.session.v2.{ME["userId"]}','id':target});await page.reload();await ready();await expect(page.get_by_label('Lịch sử phiên')).to_have_value(target)
   return {'closed_disables_send':True,'fresh_works':True,'selected_old_owned_session':True}
  await check('UI06',sessions)
  async def history():
   await expect(page.locator('.lab-message')).to_have_count(100);await page.get_by_role('button',name='Tải tin nhắn cũ hơn',exact=True).click();await expect(page.locator('.lab-message')).to_have_count(106)
   texts=await page.locator('.lab-message > p').all_text_contents();assert len(texts)==len(set(texts))==106
   await page.get_by_role('button',name='Đọc lại lịch sử',exact=True).click();await expect(page.locator('.lab-message')).to_have_count(100);assert await page.locator('.lab-product,.lab-sources').count()==0
   return {'cursor_rows_after_older':106,'unique_texts':106,'reload_window':100,'historical_cards':0}
  await check('UI07',history)
  async def pages():
   before=await page.get_by_label('Lịch sử phiên').locator('option').count()
   async with page.expect_response(lambda r:'/api/v1/chat/sessions?page=1' in r.url) as r:await page.get_by_role('button',name='Tải thêm phiên cũ',exact=True).click()
   response=await r.value;assert response.status==200;await page.wait_for_timeout(200)
   after=await page.get_by_label('Lịch sử phiên').locator('option').count();assert after>before;return {'option_count_before':before,'after':after,'requested_page':1}
  await check('UI08',pages)
  async def retry():
   await fresh()
   async def fail(route):await route.fulfill(status=503,content_type='application/json',body=json.dumps({'detail':'Controlled UAT send unavailable'}))
   await page.route('**/api/v1/chat/sessions/*/messages',fail,times=1)
   await page.get_by_label('Câu hỏi của bạn').fill('Xin chào');await page.get_by_role('button',name='Gửi câu hỏi',exact=True).click();await expect(page.get_by_role('alert')).to_contain_text('Controlled UAT send unavailable');await ready()
   assert await page.locator('.lab-message-user').count()==0
   await page.get_by_role('button',name='Gửi câu hỏi',exact=True).click();await expect(page.locator('.lab-message-assistant')).to_have_count(1,timeout=20000);await ready();assert await page.locator('.lab-message-user').count()==1
   return {'injected_response':503,'server_not_called_for_failure':True,'retry_real_post':True,'displayed_users':1}
  await check('UI09',retry,'REAL_BROWSER_CONTROLLED_503_THEN_REAL_RETRY')
  async def switch_pending():
   old_text='Xin chào';await fresh();hold=asyncio.Event();started=asyncio.Event()
   async def delayed(route):
    if route.request.method!='POST':await route.continue_();return
    started.set();await hold.wait();await route.continue_()
   await page.route('**/api/v1/chat/sessions/*/messages',delayed)
   await page.get_by_label('Câu hỏi của bạn').fill(old_text);await page.get_by_role('button',name='Gửi câu hỏi',exact=True).click();await asyncio.wait_for(started.wait(),10)
   selected=await page.get_by_label('Lịch sử phiên').input_value()
   await expect(page.get_by_label('Lịch sử phiên')).to_be_disabled()
   hold.set();await expect(page.locator('.lab-message-assistant')).to_have_count(1,timeout=20000);await ready()
   await page.unroute('**/api/v1/chat/sessions/*/messages',delayed)
   options=await page.get_by_label('Lịch sử phiên').locator('option').evaluate_all('(rows)=>rows.filter(x=>x.textContent.includes("Đang mở")).map(x=>x.value)')
   target=next(o for o in options if o!=selected)
   await page.get_by_label('Lịch sử phiên').select_option(target);await ready()
   await expect(page.get_by_label('Lịch sử phiên')).to_have_value(target)
   return {'switch_disabled_during_post':True,'switch_after_completion':True,'selected_session_kept':True,'note':'UI prevents switching during POST; component tests separately cover stale close responses.'}
  await check('UI10',switch_pending,'REAL_BROWSER_CONTROLLED_PENDING_NETWORK')
  async def double():
   await fresh();hold=asyncio.Event();started=asyncio.Event();count=0
   async def delayed(route):
    nonlocal count
    if route.request.method!='POST':await route.continue_();return
    count+=1;started.set();await hold.wait();await route.continue_()
   await page.route('**/api/v1/chat/sessions/*/messages',delayed)
   await page.get_by_label('Câu hỏi của bạn').fill('Xin chào');await page.get_by_role('button',name='Gửi câu hỏi',exact=True).dblclick();await asyncio.wait_for(started.wait(),10)
   await expect(page.get_by_role('button',name='Đang chờ…',exact=True)).to_be_disabled();await expect(page.locator('.lab-working')).to_be_visible();hold.set();await expect(page.locator('.lab-message-assistant')).to_have_count(1,timeout=20000);await ready();await page.unroute('**/api/v1/chat/sessions/*/messages',delayed);assert count==1
   return {'post_count':count,'loading_visible':True}
  await check('UI11',double,'REAL_BROWSER_CONTROLLED_DELAY_REAL_SERVER_RESPONSE')
  async def feedback():
   button=page.get_by_role('button',name='Câu trả lời đúng',exact=True).last
   async with page.expect_response(lambda r:r.url.endswith('/feedback') and r.request.method=='POST') as r:await button.click()
   response=await r.value;assert response.status==200;await expect(button).to_have_attribute('aria-pressed','true');return {'http_status':response.status,'selected':True,'persistence':'RAM only'}
  await check('UI12',feedback)
  async def tracing():
   await expect(page.locator('.trace-summary')).to_be_visible(timeout=20000);assert await page.locator('.trace-event').count()>0
   await page.get_by_placeholder('intent, SKU, prompt, max_price…').fill('query.plan');await expect(page.locator('.trace-event')).to_have_count(1,timeout=3000);await page.get_by_placeholder('intent, SKU, prompt, max_price…').fill('')
   return {'real_trace_visible':True,'filter_works':True}
  await check('UI13',tracing)
  async def export():
   async with page.expect_download() as d:await page.get_by_role('button',name='Xuất kết quả JSON',exact=True).click()
   download=await d.value;path=await download.path();data=json.loads(Path(path).read_text());assert data['messages'] and data['runtime']['conversation_storage']=='postgres'
   dump(OUT/'evidence/ui/export_sanitized.json',public(data));return {'downloaded':True,'transcript_rows':len(data['messages']),'storage':'postgres'}
  await check('UI14',export)
  async def malformed():
   await fresh();before=len(errors)
   async def malformed_response(route):await route.fulfill(status=200,content_type='application/json',body='{"message":"Controlled incomplete DTO","products":{},"sources":[]}')
   await page.route('**/api/v1/chat/sessions/*/messages',malformed_response,times=1)
   await page.get_by_label('Câu hỏi của bạn').fill('Xin chào');await page.get_by_role('button',name='Gửi câu hỏi',exact=True).click();await page.wait_for_timeout(700)
   assert len(errors)==before,'Uncaught UI pageerror on malformed response';assert await page.locator('.chatbot-lab').count()==1
   return {'malformed_200_did_not_crash':True,'malformed_products_type':'object'}
  await check('UI15',malformed,'REAL_BROWSER_CONTROLLED_MALFORMED_RESPONSE')
  await page.unroute_all(behavior='wait');await page.goto('http://localhost:5174/chatbot-lab');await ready();await fresh();await send('Tìm laptop Dell dưới 20 triệu.')
  async def responsive():
   observations=[]
   for width in [390,768,1440]:
    await page.set_viewport_size({'width':width,'height':1000});await page.wait_for_timeout(250)
    dimensions=await page.evaluate('({viewport:innerWidth,width:document.documentElement.scrollWidth})');overflow=dimensions['width']>width
    shot=OUT/f'evidence/ui/viewport-{width}.png';await page.screenshot(path=str(shot),full_page=True);screens.append(str(shot.relative_to(OUT)));observations.append({'width':width,'page_scroll_width':dimensions['width'],'horizontal_overflow':overflow,'screenshot':str(shot.relative_to(OUT))})
   dump(OUT/'evidence/ui/responsive.json',observations);assert not any(o['horizontal_overflow'] for o in observations)
   return {'viewports':observations}
  await check('UI16',responsive)
  async def keyboard():
   await page.set_viewport_size({'width':1440,'height':1000});await page.get_by_label('Câu hỏi của bạn').focus();await page.keyboard.press('ControlOrMeta+A');await page.keyboard.type('Xin chào');await page.keyboard.press('Tab');assert await page.get_by_role('button',name='Gửi câu hỏi',exact=True).evaluate('(e)=>e===document.activeElement')
   await page.keyboard.press('Enter');await expect(page.get_by_label('Câu hỏi của bạn')).to_be_enabled(timeout=20000);assert await page.get_by_role('log').get_attribute('aria-live')=='polite';return {'labelled_textarea':True,'keyboard_submit':True,'transcript_live_region':'polite','scope':'basic only; no WCAG certification'}
  await check('UI17',keyboard)
  async def no_errors():
   normal=[e for e in errors if e['case']!='UI15'];dump(OUT/'evidence/ui/browser_errors.json',{'page_errors':errors,'console':console});assert not normal
   return {'unexpected_normal_pageerrors':len(normal),'controlled_malformed_errors':[e for e in errors if e['case']=='UI15'],'console_count':len(console)}
  await check('UI18',no_errors)
  async def storefront():
   await page.goto('http://localhost:5174/products');await page.wait_for_timeout(1000);links=await page.locator('a[href="/chatbot-lab"]').count();widgets=await page.locator('.chatbot-lab').count();shot=OUT/'evidence/ui/storefront.png';await page.screenshot(path=str(shot),full_page=True)
   return {'storefront_chatbot_lab_links':links,'storefront_chat_widget_present':widgets>0,'classification':'NEW_FEATURE_REQUIRING_APPROVAL','UI_STOREFRONT_READY':False}
  await check('UI19',storefront)
  await browser.close()
 dump(OUT/'evidence/ui/browser_errors.json',{'page_errors':errors,'console':console});dump(OUT/'ui_inventory.json',public(rows));CLIENT.close()
 print('UI inventory complete',len(rows),'rows',flush=True)
if __name__=='__main__':asyncio.run(main())
