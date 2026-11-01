"""Multi-turn context diagnostic for the live chatbot (Vite 5173 -> chatbot 8090 / Spring 8080 / Ollama).

Each case runs in a fresh chat session as a synthetic seed customer. Every turn is graded against the live
catalog (read-only SELECT) and the dev trace (effective filters, reference resolution), so a failure says
*where* context was lost, not only that it was. Only chat sessions are written; no cart/order writes.

Run:  chatbot-service/.venv/bin/python chatbot-service/debug/context-eval/run_context_eval.py [--holdout] [C01 C05 ...]
      --holdout runs cases_holdout.json: phrasings never used while fixing, to check fixes generalize.
"""
import json, re, subprocess, sys, time, uuid
from datetime import datetime, timezone
from pathlib import Path

import httpx

HERE = Path(__file__).resolve().parent
SERVICE = HERE.parents[1]
sys.path.insert(0, str(SERVICE))
from app.features.query_understanding.constraints import (  # noqa: E402
    GENERAL_CLARIFICATION, NO_ANTECEDENT_CLARIFICATION, PENDING_REFERENCE_CLARIFICATION,
)

BASE = 'http://localhost:5173'
USER = 'seed.customer1'
# Application-controlled "I lost the thread" replies; matching one on a context turn is CONTEXT_LOST.
LOST = (GENERAL_CLARIFICATION, NO_ANTECEDENT_CLARIFICATION, PENDING_REFERENCE_CLARIFICATION,
        'Mình chưa có sản phẩm tham chiếu', 'Bạn vui lòng nêu tên', 'Có nhiều sản phẩm vừa được gợi ý')
TRACE_EVENTS = {'query.plan.initial', 'query.domain_validation', 'query.plan.effective',
                'conversation.reference_resolution', 'response.strategy', 'conversation.context_selection'}
CATALOG_SQL = """SELECT c.category_name, b.brand_name, p.product_name, v.ram, v.storage, v.price::bigint
FROM product_variants v JOIN products p USING(product_id) JOIN categories c ON c.category_id=p.category_id
LEFT JOIN brands b ON b.brand_id=p.brand_id WHERE p.status='ACTIVE' AND v.status='ACTIVE'"""


def gb(text):
    m = re.match(r'\s*(\d+)\s*(GB|TB)', text or '', re.I)
    return None if not m else int(m[1]) * (1024 if m[2].upper() == 'TB' else 1)


def load_catalog():
    out = subprocess.check_output(['docker', 'exec', 'ecommerce-postgres', 'psql', '-U', 'ecommerce_user', '-d', 'ecommerce_db',
                                   '-At', '-F', '|', '-c', CATALOG_SQL], text=True)
    rows = [line.split('|') for line in out.splitlines() if line]
    return [{'category': c, 'brand': b, 'product': p, 'ram': gb(r), 'storage': gb(s), 'price': int(price)} for c, b, p, r, s, price in rows]


def login():
    password = re.search(r'`(\d+)`', (SERVICE / 'docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md').read_text()).group(1)
    client = httpx.Client(base_url=BASE, timeout=180)
    client.get('/api/auth/csrf').raise_for_status()
    client.headers['X-XSRF-TOKEN'] = client.cookies.get('XSRF-TOKEN')
    client.post('/api/auth/login', json={'username': USER, 'password': password}).raise_for_status()
    client.headers['X-XSRF-TOKEN'] = client.cookies.get('XSRF-TOKEN')
    return client


def send(client, session_id, text):
    trace_id = uuid.uuid4()
    started = time.monotonic()
    response = client.post(f'/api/v1/chat/sessions/{session_id}/messages', json={'message': text},
                           headers={'X-Chat-Trace-Id': str(trace_id)})
    elapsed = round(time.monotonic() - started, 2)
    if response.status_code != 200:
        return {'status': response.status_code, 'message': response.text[:300], 'cards': [], 'trace': [], 'seconds': elapsed}
    body = response.json()
    cards = [{'product': p['product_name'], 'sku': p.get('sku'), 'ram': gb(p.get('ram')), 'storage': gb(p.get('storage')),
              'price': int(float(p['effective_price'])) if p.get('effective_price') is not None else None} for p in body['products']]
    trace = []
    t = client.get(f'/api/v1/chat/sessions/{session_id}/traces/{trace_id}')
    if t.status_code == 200:
        trace = [{'name': e['name'], 'output': e.get('output')} for e in t.json()['events'] if e['name'] in TRACE_EVENTS]
    return {'status': 200, 'message': body['message'], 'cards': cards, 'trace': trace, 'seconds': elapsed}


def matches(row, exp, product_of):
    """Static constraints of one expectation applied to one catalog row or card (card fields are a subset)."""
    info = product_of(row['product'])
    checks = [
        ('category', lambda v: info and info['category'] == v),
        ('brand', lambda v: info and (info['brand'] or '').lower() == v.lower()),
        ('product', lambda v: row['product'] == v),
        ('min_ram', lambda v: row['ram'] is not None and row['ram'] >= v),
        ('min_storage', lambda v: row['storage'] is not None and row['storage'] >= v),
        ('max_price', lambda v: row['price'] is not None and row['price'] <= v),
    ]
    return [key for key, ok in checks if key in exp and not ok(exp[key])]


def grade(exp, result, history, catalog, product_of):
    """Return (verdict, failure_types, notes). history = earlier turn results of this case."""
    if not exp:
        return 'INFO', [], ['không chấm (lượt chen ngang)']
    msg, cards, fails, notes = result['message'], result['cards'], [], []
    if result['status'] != 200:
        return 'FAIL', ['HTTP_ERROR'], [f"HTTP {result['status']}"]
    lost = any(msg.startswith(p) or p in msg for p in LOST)
    previous = next((h for h in reversed(history) if h['cards']), None)

    # Price comparison baseline: the previous turn that showed cards, or an explicit turn index.
    direction, base = None, None
    if 'price_vs_prev' in exp and previous:
        direction, base = exp['price_vs_prev'], previous
    if 'price_vs' in exp:
        turn, direction = exp['price_vs'].split(':')
        base = history[int(turn.removeprefix('turn')) - 1]
    base_prices = [c['price'] for c in (base['cards'] if base else []) if c['price'] is not None]

    def comparable(price):
        if not direction or not base_prices:
            return True
        return price < min(base_prices) if direction == 'lt' else price > max(base_prices)

    referent = None
    if 'referent' in exp:
        _, where, index = exp['referent'].split(':')
        source = previous if where == 'prev' else history[int(where.removeprefix('turn')) - 1]
        position = int(index) - 1
        referent = source['cards'][position]['product'] if source and len(source['cards']) > position else None
        if referent is None:
            notes.append('lượt gốc không có đủ thẻ để tham chiếu; không chấm được referent')

    truth = [r for r in catalog if not matches(r, exp, product_of) and comparable(r['price'])
             and (referent is None or r['product'] == referent)]
    if exp.get('products'):
        if not cards:
            if lost:
                fails.append('CONTEXT_LOST')
            elif truth:
                fails.append('MISSED')
                notes.append(f"catalog có {len({r['product'] for r in truth})} sản phẩm phù hợp, vd {truth[0]['product']}")
            else:
                notes.append('catalog không có sản phẩm phù hợp; trả lời rỗng là đúng')
        elif lost:
            fails.append('CONTEXT_LOST')
    elif lost and exp:
        fails.append('CONTEXT_LOST')

    for card in cards:
        broken = matches(card, exp, product_of)
        if broken:
            fails.append('WRONG_FILTER')
            notes.append(f"{card['product']} {card['ram']}GB/{card['storage']}GB {card['price']:,} sai: {', '.join(broken)}")
        if not comparable(card['price']):
            fails.append('WRONG_DIRECTION')
            notes.append(f"{card['product']} {card['price']:,} không {'rẻ' if direction == 'lt' else 'đắt'} hơn mốc {min(base_prices) if direction == 'lt' else max(base_prices):,}")
    if referent and cards and any(c['product'] != referent for c in cards):
        fails.append('WRONG_REFERENT')
        notes.append(f"mong đợi {referent}, nhận {sorted({c['product'] for c in cards})}")
    if 'product_set' in exp and {c['product'] for c in cards} != set(exp['product_set']):
        fails.append('WRONG_REFERENT')
        notes.append(f"mong đợi đúng {exp['product_set']}, nhận {sorted({c['product'] for c in cards})}")
    if 'within_products' in exp and any(c['product'] not in exp['within_products'] for c in cards):
        fails.append('LEFT_SCOPE')
        notes.append(f"ra ngoài phạm vi đang so sánh: {sorted({c['product'] for c in cards} - set(exp['within_products']))}")
    if 'min_cards' in exp and len({c['product'] for c in cards}) < exp['min_cards']:
        fails.append('TOO_FEW')
        notes.append(f"chỉ {len({c['product'] for c in cards})} sản phẩm")
    if exp.get('exclude_prev') and previous and {c['product'] for c in cards} & {c['product'] for c in previous['cards']}:
        fails.append('REPEATED_PREVIOUS')
        notes.append('lặp lại sản phẩm đã gợi ý')
    if 'min_price_band' in exp:
        low, high = exp['min_price_band']
        outside = [c for c in cards if not low <= (c['price'] or 0) <= high]
        if outside:
            fails.append('WRONG_FILTER')
            notes.append(f"ngoài khoảng {low:,}–{high:,}: {[(c['product'], c['price']) for c in outside]}")
    if 'bundle_categories' in exp:
        got = sorted((product_of(c['product']) or {}).get('category') for c in cards)
        if got != sorted(exp['bundle_categories']):
            fails.append('WRONG_BUNDLE')
            notes.append(f"mong đợi 1 sản phẩm mỗi loại {exp['bundle_categories']}, nhận {got}")
        total = sum(c['price'] or 0 for c in cards)
        if 'max_total' in exp and total > exp['max_total']:
            fails.append('OVER_BUDGET')
            notes.append(f"tổng {total:,} vượt {exp['max_total']:,}")
        elif cards:
            notes.append(f"tổng combo {total:,}")
    if exp.get('no_products') and cards:
        fails.append('UNEXPECTED_PRODUCTS')
        notes.append(f"không được trả thẻ, nhận {sorted({c['product'] for c in cards})}")
    if 'text_any' in exp and not any(word.lower() in msg.lower() for word in exp['text_any']):
        fails.append('TEXT_MISSING')
        notes.append(f"câu trả lời không nhắc {exp['text_any']}")
    return ('FAIL' if fails else 'PASS'), sorted(set(fails)), notes


def effective_filters(trace):
    plan = next((e['output'] for e in trace if e['name'] == 'query.plan.effective'), None) or {}
    search = plan.get('product_search') or {}
    keep = ('category', 'brands', 'min_price', 'max_price', 'min_ram_gb', 'min_storage_gb', 'targets', 'top_k')
    return {k: search[k] for k in keep if search.get(k) not in (None, [], '')}


def main(selected, holdout=False):
    cases = json.loads((HERE / ('cases_holdout.json' if holdout else 'cases.json')).read_text())
    cases = [c for c in cases if not selected or c['id'] in selected]
    catalog = load_catalog()
    by_product = {}
    for r in catalog:
        by_product.setdefault(r['product'], {'category': r['category'], 'brand': r['brand']})
    client = login()
    run_dir = HERE / 'runs' / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + ('-holdout' if holdout else ''))
    run_dir.mkdir(parents=True)
    rows = []
    try:
        for case in cases:
            session_id, history = uuid.uuid4(), []
            for n, turn in enumerate(case['turns'], 1):
                result = send(client, session_id, turn['say'])
                verdict, fails, notes = grade(turn['expect'], result, history, catalog, by_product.get)
                history.append(result)
                row = {'case': case['id'], 'dimension': case['dimension'], 'title': case['title'], 'turn': n, 'say': turn['say'],
                       'expect': turn['expect'], 'verdict': verdict, 'failures': fails, 'notes': notes, 'reply': result['message'],
                       'cards': result['cards'], 'effective_filters': effective_filters(result['trace']),
                       'trace': result['trace'], 'seconds': result['seconds']}
                rows.append(row)
                print(f"{case['id']}.{n} {verdict:4} {','.join(fails):28} {turn['say'][:45]!r:48} -> {[c['product'] for c in result['cards']][:3]}", flush=True)
    finally:
        client.close()
    (run_dir / 'results.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n')
    (run_dir / 'REPORT.md').write_text(report(rows, run_dir.name))
    print('\nWrote', run_dir.relative_to(SERVICE.parent))


def report(rows, run_id):
    graded = [r for r in rows if r['verdict'] != 'INFO']
    passed = sum(r['verdict'] == 'PASS' for r in graded)
    lines = [f'# Context eval — run {run_id}', '',
             f'Tài khoản: `{USER}` (seed). Lượt được chấm: {len(graded)}, PASS {passed}, FAIL {len(graded) - passed} '
             f'({passed / len(graded):.0%} pass).', '', '## Theo nhóm năng lực ngữ cảnh', '',
             '| Nhóm | Lượt chấm | PASS | FAIL | Loại lỗi |', '|---|---|---|---|---|']
    for dim in sorted({r['dimension'] for r in graded}):
        group = [r for r in graded if r['dimension'] == dim]
        kinds = sorted({f for r in group for f in r['failures']})
        lines.append(f"| {dim} | {len(group)} | {sum(r['verdict'] == 'PASS' for r in group)} | {sum(r['verdict'] == 'FAIL' for r in group)} | {', '.join(kinds) or '—'} |")
    counts = {}
    for r in graded:
        for f in r['failures']:
            counts[f] = counts.get(f, 0) + 1
    lines += ['', '## Loại lỗi', '', '| Loại | Số lượt |', '|---|---|'] + [f'| {k} | {v} |' for k, v in sorted(counts.items(), key=lambda x: -x[1])]
    lines += ['', '## Chi tiết từng lượt', '']
    for case in dict.fromkeys(r['case'] for r in rows):
        group = [r for r in rows if r['case'] == case]
        lines += [f"### {case} — {group[0]['title']} (`{group[0]['dimension']}`)", '',
                  '| # | Người dùng | Kết quả | Thẻ trả về | Bộ lọc thực tế | Ghi chú |', '|---|---|---|---|---|---|']
        for r in group:
            cards = ', '.join(dict.fromkeys(f"{c['product']} ({c['price']:,})" for c in r['cards'])) or ('_' + r['reply'][:70].replace('\n', ' ').replace('|', '/') + '_')
            filters = json.dumps(r['effective_filters'], ensure_ascii=False).replace('|', '/') if r['effective_filters'] else '—'
            verdict = r['verdict'] + (f" {','.join(r['failures'])}" if r['failures'] else '')
            lines.append(f"| {r['turn']} | {r['say']} | {verdict} | {cards} | `{filters}` | {'; '.join(r['notes']).replace('|', '/')} |")
        lines.append('')
    return '\n'.join(lines) + '\n'


if __name__ == '__main__':
    args = sys.argv[1:]
    main({a for a in args if a != '--holdout'}, holdout='--holdout' in args)
