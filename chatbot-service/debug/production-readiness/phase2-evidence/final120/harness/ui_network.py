"""Supplemental real-browser network attribution and readable viewport captures."""
import asyncio,sys,re
from pathlib import Path
from urllib.parse import urlsplit
ROOT=Path(__file__).resolve().parents[6];sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import *
from runtime import password,public
from playwright.async_api import async_playwright,expect
async def main():
 errors=[];responses=[]
 async with async_playwright() as pw:
  browser=await pw.chromium.launch(executable_path='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless=True)
  page=await browser.new_page(viewport={'width':1440,'height':1000})
  page.on('pageerror',lambda e:errors.append(scrub(str(e))))
  def response(r):
   if r.status>=400:
    path=urlsplit(r.url).path;path=re.sub(r'(/sessions/)[0-9a-f-]{36}',r'\1synthetic-session',path);responses.append({'http_status':r.status,'path':path,'method':r.request.method})
  page.on('response',response)
  await page.goto('http://localhost:5174/chatbot-lab');await page.get_by_label('Tên đăng nhập',exact=True).fill('seed.customer1');await page.get_by_label('Mật khẩu',exact=True).fill(password());await page.get_by_role('button',name='Đăng nhập',exact=True).click();await expect(page.get_by_label('Câu hỏi của bạn')).to_be_enabled(timeout=20000)
  await page.get_by_label('Câu hỏi của bạn').fill('Tìm laptop Dell dưới 20 triệu.');await page.get_by_role('button',name='Gửi câu hỏi',exact=True).click();await expect(page.locator('.lab-product')).to_have_count(1,timeout=180000);await expect(page.get_by_label('Câu hỏi của bạn')).to_be_enabled()
  for width in [390,768,1440]:
   await page.set_viewport_size({'width':width,'height':1000});await page.evaluate('scrollTo(0,0)');await page.screenshot(path=str(OUT/f'evidence/ui/top-{width}.png'),full_page=False)
  await page.set_viewport_size({'width':1440,'height':1000});await page.locator('.lab-product a').click();await expect(page.locator('h1')).to_contain_text('Dell Inspiron 15',timeout=20000);await page.wait_for_timeout(1000)
  await browser.close()
 dump(OUT/'evidence/ui/network-attribution.json',{'execution':'SUPPLEMENTAL_REAL_BROWSER_NOT_COUNTED_IN_120','http_error_responses':responses,'uncaught_page_errors':errors,'notes':'Network observations are from this supplemental login/product navigation run; earlier generic console messages without URL remain unattributed unless reproduced here.'})
 print('Supplemental network and viewport screenshots captured')
asyncio.run(main())
