"""Real-browser check of the searchable category filter on the goods receipt form (dev stack).

Read-only: opens create/edit forms, filters and selects SKUs, types quantity/cost, but never presses
"Lưu DRAFT", so no receipt is created or updated. Account: synthetic seed staff user.
Run: chatbot-service/.venv/bin/python Fontend/docs/goods-receipt-category-evidence/browser_check.py
"""
import asyncio, json, re
from datetime import datetime, timezone
from pathlib import Path
from playwright.async_api import async_playwright, expect

ROOT = Path(__file__).resolve().parents[3]; OUT = Path(__file__).resolve().parent; BASE = 'http://localhost:5173'
PASSWORD = re.search(r'`(\d+)`', (ROOT / 'chatbot-service/docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md').read_text()).group(1)
rows, errors, writes = [], [], []


def record(step, ok, note, shot=None):
    rows.append({'step': step, 'verdict': 'PASS' if ok else 'FAIL', 'note': note, 'screenshot': shot}); print(step, 'PASS' if ok else 'FAIL', '-', note, flush=True)


async def main():
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(executable_path='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless=True)
        page = await (await browser.new_context(viewport={'width': 1440, 'height': 1000})).new_page()
        page.on('pageerror', lambda e: errors.append(str(e)[:300]))
        page.on('request', lambda r: writes.append(f'{r.method} {r.url.replace(BASE, "")}') if r.method in ('POST', 'PUT', 'PATCH', 'DELETE') and 'goods-receipts' in r.url else None)
        variant_calls = []
        page.on('request', lambda r: variant_calls.append(r.url) if '/api/v1/product-variants?' in r.url else None)

        async def shot(name):
            await page.screenshot(path=str(OUT / f'{name}.png')); return f'{name}.png'

        await page.goto(BASE + '/login')
        await page.get_by_label('Tên đăng nhập', exact=True).fill('seed.staff'); await page.get_by_label('Mật khẩu', exact=True).fill(PASSWORD)
        await page.get_by_role('button', name='Đăng nhập', exact=True).click(); await page.wait_for_url(re.compile('/account|/$'), timeout=20000)
        await page.goto(BASE + '/goods-receipts/new'); await expect(page.get_by_role('heading', name='Tạo phiếu nhập')).to_be_visible(timeout=20000)
        line1 = page.get_by_role('group', name='Dòng 1')
        await expect(line1.locator('.variant-picker-result').first).to_be_visible(timeout=20000)

        trigger = line1.get_by_role('button', name=re.compile('Danh mục'))
        await trigger.focus(); await page.keyboard.press('Enter')
        search = page.get_by_role('combobox', name='Tìm danh mục')
        total_options = await page.get_by_role('listbox').get_by_role('option').count()
        record('open dropdown via keyboard', await search.evaluate('el => el === document.activeElement'), f'search box focused; {total_options} options incl. "Tất cả"', await shot('01-open'))
        before = len(variant_calls)
        await search.type('dien thoai', delay=40)
        names = await page.get_by_role('listbox').get_by_role('option').all_inner_texts()
        await page.wait_for_timeout(500)
        record('accent-insensitive search, no SKU request while typing', len(variant_calls) == before and any('Điện thoại' in n for n in names),
               f'options: {[n.replace("✓", "").strip() for n in names]}; SKU requests while typing: {len(variant_calls) - before}', await shot('02-search'))
        # Two real categories match ("Điện thoại & Máy tính bảng", "Điện thoại"): arrow to the exact one.
        for _ in range(10):
            await page.keyboard.press('ArrowDown')
            active = await search.get_attribute('aria-activedescendant')
            if active and (await page.locator(f'[id="{active}"]').inner_text()).replace('✓', '').strip() == 'Điện thoại':
                break
        await page.keyboard.press('Enter')
        await expect(trigger).to_have_text(re.compile(r'^Điện thoại\s*▾$'))
        await page.wait_for_timeout(800)
        cats = await line1.locator('.variant-picker-result small').all_inner_texts()
        if not cats:
            raise RuntimeError('no SKU results for exact category')
        record('select via keyboard filters SKUs by categoryId', bool(cats) and all('Điện thoại' in c for c in cats) and 'categoryId=' in variant_calls[-1],
               f'{len(cats)} SKU results, all "Điện thoại"; request uses categoryId param', await shot('03-filtered'))
        sku = (await line1.locator('.variant-picker-result strong').first.inner_text())
        await line1.locator('.variant-picker-result').first.click()
        await line1.get_by_label('Số lượng').fill('2'); await line1.get_by_label('Giá vốn').fill('1000000')
        await page.get_by_role('button', name='Thêm dòng').click()
        line2 = page.get_by_role('group', name='Dòng 2')
        await expect(line2.get_by_role('button', name=re.compile('Danh mục'))).to_contain_text('Tất cả')
        await line2.get_by_role('button', name=re.compile('Danh mục')).click()
        await page.mouse.click(5, 5)
        closed = await page.get_by_role('listbox').count() == 0
        kept = await line1.locator('.variant-selected-card strong').inner_text() == sku and await line1.get_by_label('Số lượng').input_value() == '2'
        record('multi-line isolation + click outside', closed and kept, f'line 1 keeps SKU {sku}, qty 2; line 2 shows "Tất cả"; outside click closed popup', await shot('04-multiline'))

        for label, (w, h) in {'mobile': (390, 844), 'tablet': (768, 1024)}.items():
            await page.set_viewport_size({'width': w, 'height': h})
            await line2.get_by_role('button', name=re.compile('Danh mục')).scroll_into_view_if_needed()
            await line2.get_by_role('button', name=re.compile('Danh mục')).click()
            await page.wait_for_timeout(200)
            box = await page.locator('.category-select-popup').bounding_box()
            overflow = await page.evaluate('document.documentElement.scrollWidth - window.innerWidth')
            name = await shot(f'05-{label}')
            await page.keyboard.press('Escape')
            record(f'{label} {w}px', box and box['x'] >= 0 and box['x'] + box['width'] <= w + 1 and overflow <= 1, f'popup x={box and round(box["x"])} w={box and round(box["width"])}; page overflow {overflow}px', name)
        await page.set_viewport_size({'width': 1440, 'height': 1000})
        parent = await page.evaluate("""(async () => {
            const cats = await (await fetch('/api/v1/categories', {credentials: 'include'})).json();
            const c = cats.find((x) => x.categoryName === 'Điện thoại & Máy tính bảng');
            if (!c) return null;
            const r = await (await fetch('/api/v1/product-variants?page=0&size=20&categoryId=' + c.categoryId, {credentials: 'include'})).json();
            return { parentCategoryId: c.parentCategoryId ?? c.parentId ?? null, totalElements: r.totalElements };
        })()""")
        rows.append({'step': 'observation: parent category', 'verdict': 'INFO', 'note': f'"Điện thoại & Máy tính bảng" -> {parent}; API filters by exact categoryId (unchanged behaviour)'})

        receipts = await page.evaluate("fetch('/api/goods-receipts?page=0&size=50', {credentials: 'include'}).then(r => r.json())")
        draft = next((r for r in receipts.get('content', []) if r.get('status') == 'DRAFT'), None)
        if draft:
            await page.goto(f"{BASE}/goods-receipts/{draft['receiptId']}/edit"); await expect(page.get_by_role('heading', name='Sửa phiếu nhập')).to_be_visible(timeout=20000)
            edit1 = page.get_by_role('group', name='Dòng 1'); original = await edit1.locator('.variant-selected-card strong').inner_text()
            await edit1.get_by_role('button', name='Đổi SKU').click()
            await edit1.get_by_role('button', name=re.compile('Danh mục')).click(); await page.get_by_role('combobox', name='Tìm danh mục').type('lap')
            await page.get_by_role('listbox').get_by_role('option').nth(1).click()
            await edit1.get_by_role('button', name='Giữ SKU hiện tại').click()
            record('edit screen keeps existing SKU', await edit1.locator('.variant-selected-card strong').inner_text() == original, f'existing SKU {original} kept after browsing a category (not saved)', await shot('06-edit'))
        else:
            rows.append({'step': 'edit screen', 'verdict': 'NOT_RUN', 'note': 'no DRAFT receipt exists in the dev DB; creating one would write business data (edit flow covered by component test)'})
        await browser.close()
    (OUT / 'browser_check.json').write_text(json.dumps({'executed_at': datetime.now(timezone.utc).isoformat(), 'url': BASE, 'account': 'seed.staff (synthetic seed)',
        'rows': rows, 'page_errors': errors, 'receipt_write_requests': writes}, ensure_ascii=False, indent=2) + '\n')
    print('SUMMARY', {v: sum(r['verdict'] == v for r in rows) for v in ('PASS', 'FAIL')}, 'page_errors', len(errors), 'receipt writes', writes)


asyncio.run(main())
