"""Real-browser check of the mandatory branch gate on the dev stack (Vite 5173 -> Spring 8080).

Non-destructive: confirms the cart's existing branch (no API write), opens the clear-cart warning and
keeps the cart, dismisses the header's native confirm. Any cart write request is recorded and must be 0.
Adding items / switching the cart branch would modify dev data and is NOT_RUN (covered by unit tests).
Accounts: synthetic seed users. Run: chatbot-service/.venv/bin/python Fontend/docs/branch-selection-evidence/browser_check.py
"""
import asyncio, json, re
from datetime import datetime, timezone
from pathlib import Path
from playwright.async_api import async_playwright, expect

ROOT = Path(__file__).resolve().parents[3]; OUT = Path(__file__).resolve().parent; BASE = 'http://localhost:5173'
PASSWORD = re.search(r'`(\d+)`', (ROOT / 'chatbot-service/docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md').read_text()).group(1)
rows, page_errors, cart_writes, product_requests = [], [], [], []


def record(step, ok, note, shot=None):
    rows.append({'step': step, 'verdict': 'PASS' if ok else 'FAIL', 'note': note, 'screenshot': shot}); print(step, 'PASS' if ok else 'FAIL', '-', note, flush=True)


async def main():
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(executable_path='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless=True)

        async def new_page(viewport=(1440, 1000)):
            context = await browser.new_context(viewport={'width': viewport[0], 'height': viewport[1]})
            page = await context.new_page()
            page.on('pageerror', lambda e: page_errors.append(str(e)[:300]))
            page.on('request', lambda r: cart_writes.append(f'{r.method} {r.url.replace(BASE, "")}') if '/api/cart' in r.url and r.method != 'GET' else None)
            page.on('request', lambda r: product_requests.append(r.url.replace(BASE, '')) if re.search(r'/api/v1/products(\?|/[^/]+/detail)', r.url) else None)
            return context, page

        async def login(page, username):
            await page.goto(BASE + '/login')
            await page.get_by_label('Tên đăng nhập', exact=True).fill(username); await page.get_by_label('Mật khẩu', exact=True).fill(PASSWORD)
            await page.get_by_role('button', name='Đăng nhập', exact=True).click()

        async def shot(page, name):
            await page.screenshot(path=str(OUT / f'{name}.png')); return f'{name}.png'

        dialog_name = 'Chọn chi nhánh mua sắm'
        context, page = await new_page()
        await page.goto(BASE + '/')
        await page.wait_for_timeout(800)
        record('guest home', await page.get_by_role('dialog').count() == 0, 'guest sees the shop; no dialog, no protected branch call', await shot(page, '01-guest'))

        await login(page, 'seed.customer1')
        modal = page.get_by_role('dialog', name=dialog_name)
        await expect(modal).to_be_visible(timeout=20000)
        radios = modal.get_by_role('radio')
        await expect(radios.first).to_be_visible(timeout=20000)
        texts = [t.replace('\n', ' / ') for t in await modal.locator('.branch-option').all_inner_texts()]
        checked = await modal.locator('input[type=radio]:checked').count()
        shop_hidden = await page.get_by_role('navigation', name='Điều hướng cửa hàng').count() == 0
        record('customer login shows mandatory dialog', shop_hidden and await radios.count() == 2 and checked == 1,
               f'options: {texts}; cart branch preselected; shop not mounted behind dialog', await shot(page, '02-dialog'))

        await page.keyboard.press('Escape'); await page.mouse.click(5, 5)
        record('Escape / backdrop do not dismiss', await modal.is_visible(), 'dialog still open after Escape and outside click')

        await modal.get_by_role('radio', name=re.compile('Chi nhánh dự phòng')).check()
        await modal.get_by_role('button', name='Tiếp tục mua sắm').click()
        warning = modal.locator('.branch-modal-confirm')
        await expect(warning).to_be_visible()
        warn_text = await warning.inner_text(); warn_shot = await shot(page, '03-clear-warning')
        await modal.get_by_role('button', name='Giữ giỏ hàng hiện tại').click()
        record('switching branch with items warns in-dialog; keeping cart makes no request', not cart_writes and 'xóa toàn bộ' in warn_text,
               f'warning: {warn_text.splitlines()[0][:120]}; cart writes so far: {len(cart_writes)}', warn_shot)

        await modal.get_by_role('radio', name=re.compile('Kho phát triển')).check()
        await modal.get_by_role('button', name='Tiếp tục mua sắm').click()
        await expect(page.get_by_role('dialog')).to_have_count(0, timeout=20000)
        header = await page.locator('.storefront-context').inner_text()
        focused_main = await page.evaluate("document.activeElement?.id")
        record('confirm cart branch opens the shop', 'Chi nhánh mua sắm' in header and not cart_writes,
               f'header: {header.replace(chr(10), " / ")[:160]}; focus -> #{focused_main}; cart writes: {len(cart_writes)}', await shot(page, '04-shop-open'))

        await page.get_by_role('navigation', name='Điều hướng cửa hàng').get_by_role('link', name='Cửa hàng').click()
        await page.wait_for_timeout(1500)
        selected = await page.locator('#shopping-warehouse').input_value()
        with_branch = [u for u in product_requests if f'warehouseId={selected}' in u]
        record('catalog uses the confirmed branch, no dialog on navigation', await page.get_by_role('dialog').count() == 0 and bool(with_branch),
               f'{len(with_branch)} product requests carry warehouseId of the selected branch', await shot(page, '05-catalog'))

        await page.reload(); await page.wait_for_timeout(1500)
        record('refresh in same session', await page.get_by_role('dialog').count() == 0, 'no dialog after reload (verified marker + cart branch + ACTIVE list)')

        page.once('dialog', lambda d: asyncio.ensure_future(d.dismiss()))
        await page.locator('#shopping-warehouse').select_option(label='Chi nhánh dự phòng')
        await page.wait_for_timeout(800)
        after = await page.locator('#shopping-warehouse').input_value()
        record('header selector keeps native clear-cart confirmation', after == selected and not cart_writes,
               f'confirm dismissed -> branch unchanged ({after == selected}); cart writes: {len(cart_writes)}', await shot(page, '06-header'))

        product = await page.evaluate("fetch('/api/v1/products?page=0&size=1', {credentials: 'include'}).then(r => r.json()).then(r => r.content[0].productId)")
        await page.get_by_role('button', name='Đăng xuất').click(); await page.wait_for_timeout(800)
        await login(page, 'seed.customer1')
        await expect(page.get_by_role('dialog', name=dialog_name)).to_be_visible(timeout=20000)
        record('logout then login asks again', True, 'dialog shown again after a new login')
        await context.close()

        context, page = await new_page()
        await login(page, 'seed.customer1'); await expect(page.get_by_role('dialog', name=dialog_name)).to_be_visible(timeout=20000)
        await page.goto(f'{BASE}/products/{product}?from=link')
        modal = page.get_by_role('dialog', name=dialog_name); await expect(modal.get_by_role('radio').first).to_be_visible(timeout=20000)
        await modal.get_by_role('button', name='Tiếp tục mua sắm').click()
        await expect(page.get_by_role('dialog')).to_have_count(0, timeout=20000)
        record('new browser session + direct product URL', page.url.endswith(f'/products/{product}?from=link') and not cart_writes,
               f'new context asked again; after confirming stayed on {page.url.replace(BASE, "")}', await shot(page, '07-direct-detail'))
        await context.close()

        for label, (w, h) in {'mobile': (390, 844), 'tablet': (768, 1024)}.items():
            context, page = await new_page((w, h))
            await login(page, 'seed.customer2'); modal = page.get_by_role('dialog', name=dialog_name)
            await expect(modal.get_by_role('radio').first).to_be_visible(timeout=20000)
            overflow = await page.evaluate('document.documentElement.scrollWidth - window.innerWidth')
            button = modal.get_by_role('button', name='Tiếp tục mua sắm')
            name = await shot(page, f'08-{label}')
            record(f'{label} {w}px dialog', overflow <= 1 and await button.is_visible(), f'page overflow {overflow}px; continue button visible', name)
            await context.close()

        context, page = await new_page()
        await login(page, 'seed.staff'); await page.wait_for_timeout(1500)
        await page.goto(BASE + '/inventory'); await page.wait_for_timeout(1500)
        record('STAFF management area has no shopping dialog', await page.get_by_role('dialog').count() == 0 and await page.get_by_role('navigation', name='Điều hướng quản trị').count() == 1,
               'management layout, no branch dialog', await shot(page, '09-staff'))
        await context.close(); await browser.close()

    rows.append({'step': 'add to cart / real branch switch with clearItems', 'verdict': 'NOT_RUN', 'note': 'would modify dev cart data; covered by BranchSelectionGate.test.jsx (C. Cart synchronization)'})
    (OUT / 'browser_check.json').write_text(json.dumps({'executed_at': datetime.now(timezone.utc).isoformat(), 'url': BASE, 'accounts': 'synthetic seed users',
        'rows': rows, 'page_errors': page_errors, 'cart_write_requests': cart_writes}, ensure_ascii=False, indent=2) + '\n')
    print('SUMMARY', {v: sum(r['verdict'] == v for r in rows) for v in ('PASS', 'FAIL', 'NOT_RUN')}, 'page_errors', len(page_errors), 'cart writes', cart_writes)


asyncio.run(main())
