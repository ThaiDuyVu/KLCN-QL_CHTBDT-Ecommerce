"""Smoke the user's dev UAT stack (5173 -> 8080, chatbot 8090) in real Chrome; read-only questions only.

Uses the documented synthetic seed account. Writes only chat history (the Lab's normal behaviour)
to the dev database; no cart/order/payment mutation is requested.
"""
import asyncio, json, re, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[6]
sys.path.insert(0, str(ROOT / 'chatbot-service/debug/post-upgrade-validation/harness'))
from common import OUT, dump, now, scrub
from runtime import password
from playwright.async_api import async_playwright, expect

BASE = 'http://localhost:5173'


async def main():
    rows, errors = [], []
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(executable_path='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless=True)
        page = await (await browser.new_context(viewport={'width': 1440, 'height': 1000})).new_page()
        page.on('pageerror', lambda err: errors.append(scrub(str(err))[:300]))
        await page.goto(BASE + '/chatbot-lab'); await expect(page).to_have_url(re.compile('/login'))
        await page.get_by_label('Tên đăng nhập', exact=True).fill('seed.customer1'); await page.get_by_label('Mật khẩu', exact=True).fill(password())
        await page.get_by_role('button', name='Đăng nhập', exact=True).click(); await expect(page).to_have_url(re.compile('/chatbot-lab'), timeout=20000)
        await expect(page.get_by_label('Câu hỏi của bạn')).to_be_enabled(timeout=20000)
        text = await page.locator('body').inner_text()
        rows.append({'check': 'status labels', 'ai_real': 'AI thật' in text, 'postgres': 'PostgreSQL' in text, 'mock_label': 'mock' in text.casefold()})
        await page.get_by_role('button', name='Cuộc trò chuyện mới', exact=True).click(); await expect(page.get_by_label('Câu hỏi của bạn')).to_be_enabled()
        for prompt in ['Tìm laptop dưới 20 triệu.', 'Cho tôi xem giỏ hàng của mình.', 'Bạn có thể đặt hàng giúp tôi không?']:
            before = await page.locator('.lab-message-assistant').count()
            await page.get_by_label('Câu hỏi của bạn').fill(prompt)
            async with page.expect_response(lambda r: r.request.method == 'POST' and r.url.endswith('/messages'), timeout=180000) as info:
                await page.get_by_role('button', name='Gửi câu hỏi', exact=True).click()
            response = await info.value
            await expect(page.locator('.lab-message-assistant')).to_have_count(before + 1, timeout=30000)
            body = await response.json() if response.status == 200 else {}
            rows.append({'prompt': prompt, 'http_status': response.status, 'cards': len(body.get('products') or []),
                         'answer_prefix': (body.get('message') or '')[:140]})
        await page.screenshot(path=str(OUT / 'evidence/uat/UAT-stack-5173.png'))
        await browser.close()
    dump(OUT / 'evidence/phase2_uat_stack_smoke.json', {'executed_at': now(), 'url': BASE + '/chatbot-lab', 'rows': rows, 'page_errors': errors})
    print(json.dumps(rows, ensure_ascii=False, indent=1), 'page_errors', len(errors))


asyncio.run(main())
