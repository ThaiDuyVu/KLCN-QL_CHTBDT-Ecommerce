import asyncio,os,signal,time,subprocess
from common import *
from runtime import login,password,persist,get,public
from environment import start_chat,wait
from playwright.async_api import async_playwright

async def main():
 state=load(PRIVATE/'state.json');sid=state['browser_session'];c,me=login();url='/api/v1/chat/sessions/'+sid
 before=c.get(url+'/messages').json();assistant=next(m for m in before if m['sender_type']=='ASSISTANT')
 c.post(url+'/messages/'+assistant['id']+'/feedback',json={'rating':1}).raise_for_status()
 rating_before=next(m for m in c.get(url+'/messages').json() if m['id']==assistant['id'])['rating']
 # Fresh trace in default instance; historical cache may have evicted early cases.
 response=c.post(url+'/messages',json={'message':'Xin chào'});response.raise_for_status();tid=response.headers['X-Chat-Trace-Id'];assert c.get(url+'/traces/'+tid).status_code==200
 before=c.get(url+'/messages').json()
 async with async_playwright() as p:
  browser=await p.chromium.launch(executable_path='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless=True)
  ctx=await browser.new_context(viewport={'width':1400,'height':1000});page=await ctx.new_page();await page.goto('http://localhost:5174')
  await page.evaluate('''async ({username,password})=>{await fetch('/api/auth/csrf');const csrf=decodeURIComponent(document.cookie.split('; ').find(x=>x.startsWith('XSRF-TOKEN=')).split('=').slice(1).join('='));let r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':csrf},body:JSON.stringify({username,password})});if(!r.ok)throw Error('auth failed')}''',{'username':'seed.customer1','password':password()})
  await page.evaluate('''({key,sid})=>sessionStorage.setItem(key,sid)''',{'key':f"chatbot-lab.session.v2.{me['userId']}",'sid':sid})
  await page.goto('http://localhost:5174/chatbot-lab');await page.locator('.lab-message-assistant > p').last.wait_for()
  texts=await page.locator('.lab-message > p').all_text_contents();assert texts==[m['message'] for m in before]
  await page.reload();await page.locator('.lab-message-assistant > p').last.wait_for();reloaded=await page.locator('.lab-message > p').all_text_contents()
  # Restart only the disposable diagnostic chatbot, never the user's app processes.
  pid=load(PRIVATE/'state.json')['processes']['chatbot-default']['pid'];os.kill(pid,signal.SIGTERM)
  for i in range(40):
   try:os.kill(pid,0);time.sleep(.1)
   except ProcessLookupError:break
  start_chat('default');await page.reload();await page.locator('.lab-message-assistant > p').last.wait_for();restarted=await page.locator('.lab-message > p').all_text_contents()
  history=c.get(url+'/messages').json();rating_after=next(m for m in history if m['id']==assistant['id'])['rating'];trace_after=c.get(url+'/traces/'+tid).status_code
  obs={'browser_reload_text_equal':texts==reloaded,'real_fastapi_restart_text_equal':texts==restarted,'same_owned_session':True,'persisted_message_count':len(history),'history_ids_equal':[m['id'] for m in before]==[m['id'] for m in history],'feedback_rating_before':rating_before,'feedback_rating_after':rating_after,'trace_status_before':200,'trace_status_after_restart':trace_after,'restored_product_cards':await page.locator('.lab-product').count(),'expected_text_only_restore':True}
  case=next(x for x in load(OUT/'cases.json') if x['id']=='T59');persist({'id':'T59','prompt':case['prompt'],'expected':case['expected'],'started_at':now(),'execution':'REAL_CHROME_AND_FASTAPI_RESTART','observations':obs,'trace_id':None,'verdict':'EXECUTED_PENDING_DIAGNOSIS'})
  dump(OUT/'evidence/browser.json',obs);print('T59 reload/restart',obs,flush=True);await browser.close()
 c.close()

if __name__=='__main__':asyncio.run(main())
