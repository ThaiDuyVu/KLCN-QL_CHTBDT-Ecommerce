"""Real-browser smoke for the customer chatbot page (/chatbot) on the running dev stack.

Real Chrome -> Vite 5173 -> Spring 8080 / FastAPI 8090 (real Qwen, PostgreSQL). Read-only questions only;
creates chat sessions for the synthetic seed customer as normal usage. The network-failure step is a
browser-level route abort (the request never reaches the server), labelled as a controlled fault.
Run: chatbot-service/.venv/bin/python Fontend/docs/customer-chatbot-evidence/browser_e2e.py
"""
import asyncio, json, re, time
from datetime import datetime, timezone
from pathlib import Path
from playwright.async_api import async_playwright, expect

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
BASE = 'http://localhost:5173'
PASSWORD = re.search(r'`(\d+)`', (ROOT / 'chatbot-service/docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md').read_text()).group(1)
rows, page_errors, console_errors, http_failures = [], [], [], []


def record(step, ok, note, shot=None):
    rows.append({'step': step, 'verdict': 'PASS' if ok else 'FAIL', 'note': note, 'screenshot': shot})
    print(step, 'PASS' if ok else 'FAIL', '-', note, flush=True)


async def main():
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(executable_path='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless=True)
        context = await browser.new_context(viewport={'width': 1440, 'height': 1000})
        page = await context.new_page()
        page.on('pageerror', lambda e: page_errors.append(str(e)[:300]))
        page.on('console', lambda m: console_errors.append(m.text[:300]) if m.type == 'error' else None)
        # Path + status only (no query strings or bodies) for every failed HTTP response.
        page.on('response', lambda r: http_failures.append({'status': r.status, 'method': r.request.method, 'path': re.sub(r'[0-9a-f]{8}-[0-9a-f-]{27}', '<uuid>', r.url.split('?')[0].replace(BASE, ''))}) if r.status >= 400 else None)

        async def shot(name):
            path = OUT / f'{name}.png'; await page.screenshot(path=str(path)); return path.name

        async def ask(text):
            before = await page.locator('.cchat-message-assistant').count()
            await page.get_by_label('Câu hỏi cho Trợ lý AI').fill(text)
            async with page.expect_response(lambda r: r.request.method == 'POST' and r.url.endswith('/messages'), timeout=180000) as info:
                await page.get_by_role('button', name='Gửi', exact=True).click()
            response = await info.value
            await expect(page.locator('.cchat-message-assistant')).to_have_count(before + 1, timeout=30000)
            return response.status, await response.json()

        # 1-4: open site, log in, use the tab, same-site page.
        await page.goto(BASE + '/')
        await page.get_by_role('navigation', name='Điều hướng cửa hàng').get_by_role('link', name='Trợ lý AI').click()
        await expect(page).to_have_url(re.compile('/login'))
        await page.get_by_label('Tên đăng nhập', exact=True).fill('seed.customer1'); await page.get_by_label('Mật khẩu', exact=True).fill(PASSWORD)
        await page.get_by_role('button', name='Đăng nhập', exact=True).click()
        await expect(page).to_have_url(re.compile(r'/chatbot$'), timeout=20000)
        await expect(page.get_by_role('heading', name='Trợ lý mua sắm Điện Việt')).to_be_visible()
        tab = page.get_by_role('navigation', name='Điều hướng cửa hàng').get_by_role('link', name='Trợ lý AI')
        same_tab = len(context.pages) == 1 and await tab.get_attribute('aria-current') == 'page'
        record('01-04 open via tab after login', same_tab and await page.locator('.storefront-footer').is_visible(),
               'guest tab -> login -> back to /chatbot in the same tab; storefront header/footer visible', await shot('01-chatbot-page'))
        if await page.get_by_role('button', name='Cuộc trò chuyện mới', exact=True).is_enabled():
            await page.get_by_role('button', name='Cuộc trò chuyện mới', exact=True).click()

        # 5-6: laptop advice with product cards.
        status, body = await ask('Tìm laptop dưới 20 triệu.')
        cards = page.locator('.cchat-product')
        ok = status == 200 and await cards.count() == len(body.get('products') or []) and await cards.count() > 0
        prices_ok = all(p.get('effective_price') is None or float(p['effective_price']) <= 20000000 for p in body.get('products') or [])
        record('05-06 laptop advice + cards', ok and prices_ok, f'HTTP {status}; {await cards.count()} cards; all prices <= 20M: {prices_ok}', await shot('05-laptop-cards'))
        first_name = (body.get('products') or [{}])[0].get('product_name')

        # 7-8: detail page and back.
        await page.locator('.cchat-product').first.get_by_role('link', name=re.compile('^Xem chi tiết')).click()
        await expect(page).to_have_url(re.compile('/products/'), timeout=20000)
        await expect(page.locator('h1')).to_contain_text(first_name or '', timeout=20000)
        detail_shot = await shot('07-product-detail')
        await page.get_by_role('navigation', name='Điều hướng cửa hàng').get_by_role('link', name='Trợ lý AI').click()
        await expect(page.get_by_text('Tìm laptop dưới 20 triệu.', exact=True)).to_be_visible(timeout=20000)
        first_session = await page.evaluate("Object.entries(sessionStorage).find(([k]) => k.startsWith('storefront-chatbot.session'))?.[1]")
        record('07-08 detail and back', True, f'detail h1 contains "{first_name}"; back on /chatbot the history was reloaded from the API', detail_shot)

        # 9-11: second conversation, then back to the first.
        await page.get_by_role('button', name='Cuộc trò chuyện mới', exact=True).click()
        await expect(page.get_by_role('heading', name='Bạn cần tư vấn gì hôm nay?')).to_be_visible()
        status2, _ = await ask('Giỏ hàng của tôi.')
        second_text = (await page.locator('.cchat-message-assistant').last.inner_text())[:200]
        no_mix = await page.get_by_text('Tìm laptop dưới 20 triệu.', exact=True).count() == 0
        record('09-10 second conversation', status2 == 200 and no_mix, f'cart answer: {second_text[:90]!r}; first conversation text absent', await shot('09-second-conversation'))
        await expect(page.locator('.cchat-session')).not_to_have_count(0)
        sessions_count = await page.locator('.cchat-session').count()
        target = page.locator(f'.cchat-session:not(.is-active)').first
        await target.click()
        await expect(page.get_by_text('Tìm laptop dưới 20 triệu.', exact=True)).to_be_visible(timeout=20000)
        back_ok = await page.get_by_text('Giỏ hàng của tôi.', exact=True).count() == 0
        record('11 reopen first conversation', back_ok, f'{sessions_count} sessions listed; first transcript shown without second-session messages', await shot('11-first-again'))

        # 12: refresh restores selected session and history (text-only).
        await page.reload(); await expect(page.get_by_text('Tìm laptop dưới 20 triệu.', exact=True)).to_be_visible(timeout=20000)
        restored = await page.evaluate("Object.entries(sessionStorage).find(([k]) => k.startsWith('storefront-chatbot.session'))?.[1]")
        record('12 refresh restores history', restored == first_session and await page.locator('.cchat-product').count() == 0,
               'same session restored; history text only (cards are not persisted by the history API)', await shot('12-after-refresh'))

        # 14: controlled network failure on POST, then explicit history sync.
        await page.get_by_role('button', name='Cuộc trò chuyện mới', exact=True).click()
        await page.route('**/api/v1/chat/sessions/*/messages', lambda route: route.abort() if route.request.method == 'POST' else route.continue_())
        await page.get_by_label('Câu hỏi cho Trợ lý AI').fill('Xin chào')
        await page.get_by_role('button', name='Gửi', exact=True).click()
        await expect(page.get_by_role('alert')).to_contain_text('Mất kết nối', timeout=20000)
        await expect(page.get_by_role('button', name='Đồng bộ lịch sử')).to_be_visible()
        locked = await page.get_by_role('button', name='Gửi', exact=True).is_disabled()
        draft_kept = await page.get_by_label('Câu hỏi cho Trợ lý AI').input_value() == 'Xin chào'
        fault_shot = await shot('14-network-failure')
        await page.unroute('**/api/v1/chat/sessions/*/messages')
        await page.get_by_role('button', name='Đồng bộ lịch sử').click()
        await expect(page.get_by_role('button', name='Gửi', exact=True)).to_be_enabled(timeout=20000)
        status3, _ = await ask('Xin chào')
        record('14 network failure + sync (CONTROLLED_BROWSER_FAULT)', locked and draft_kept and status3 == 200,
               'send locked until sync; draft kept; one successful POST after sync', fault_shot)

        # 13: close the session.
        await page.get_by_role('button', name='Kết thúc cuộc trò chuyện').click()
        await page.get_by_role('group', name='Xác nhận kết thúc cuộc trò chuyện').get_by_role('button', name='Kết thúc', exact=True).click()
        await expect(page.get_by_text('Cuộc trò chuyện này đã kết thúc. Bạn vẫn có thể đọc lại lịch sử.')).to_be_visible(timeout=20000)
        record('13 close session', await page.get_by_label('Câu hỏi cho Trợ lý AI').is_disabled(), 'closed via API; composer disabled; history still visible', await shot('13-closed'))

        # 15: responsive widths on a conversation with cards.
        await page.get_by_role('button', name='Cuộc trò chuyện mới', exact=True).click()
        await ask('Tìm điện thoại Xiaomi.')
        for label, (w, h) in {'mobile': (390, 844), 'tablet': (768, 1024), 'desktop': (1440, 1000)}.items():
            await page.set_viewport_size({'width': w, 'height': h}); await page.wait_for_timeout(300)
            overflow = await page.evaluate('document.documentElement.scrollWidth - window.innerWidth')
            send = page.get_by_role('button', name='Gửi', exact=True)
            await send.scroll_into_view_if_needed()
            visible = await send.is_visible() and await page.get_by_label('Câu hỏi cho Trợ lý AI').is_visible()
            toggle = await page.get_by_role('button', name=re.compile('Danh sách trò chuyện|Ẩn danh sách')).is_visible()
            name = await shot(f'15-{label}')
            await page.locator('.cchat-products').first.scroll_into_view_if_needed(); full = await shot(f'15-{label}-cards')
            record(f'15 responsive {label} {w}px', overflow <= 1 and visible and (toggle == (w <= 900)),
                   f'horizontal overflow {overflow}px; input/send visible {visible}; list toggle shown {toggle}', name + ', ' + full)
        await browser.close()
    result = {'executed_at': datetime.now(timezone.utc).isoformat(), 'execution': 'REAL_CHROME_REAL_QWEN_REAL_SPRING_DEV_DB',
              'url': BASE + '/chatbot', 'account': 'seed.customer1 (synthetic seed)', 'rows': rows,
              'page_errors': page_errors, 'console_errors': console_errors, 'http_failures': http_failures}
    (OUT / 'browser_e2e.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print('SUMMARY', {v: sum(r['verdict'] == v for r in rows) for v in ('PASS', 'FAIL')}, 'page_errors', len(page_errors), 'console_errors', len(console_errors))


asyncio.run(main())
