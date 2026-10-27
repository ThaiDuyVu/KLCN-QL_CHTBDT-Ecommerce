"""Chatbot benchmark: 11 case files (G1–G10 + G4b) against the live dev stack.

Vite 5173 -> chatbot 8090 / Spring 8080 / Ollama, as the synthetic seed customer. Only chat sessions are written;
cart, orders and warranty records are snapshotted before and after the run and must not change (KHÔNG-GHI).
Cases that fail are re-run once to separate stable failures from LLM flakiness; the first run is the score.

Run:  chatbot-service/.venv/bin/python chatbot-service/debug/benchmark/run_benchmark.py [G1 G4b ...] [--no-retry]
      [--resume <run_dir>]   continue an interrupted run from its partial.json
"""
import json, re, subprocess, sys, time, uuid
from datetime import datetime, timezone
from pathlib import Path

import httpx

HERE = Path(__file__).resolve().parent
SERVICE = HERE.parents[1]
sys.path.insert(0, str(HERE))
from graders import Grader, gb  # noqa: E402

BASE = 'http://localhost:5173'
USER = 'seed.customer1'
TRACE_EVENTS = {'query.plan.effective', 'conversation.reference_resolution', 'response.strategy', 'query.domain_validation'}
CATALOG_SQL = """SELECT c.category_name, b.brand_name, p.product_name, v.ram, v.storage, v.price::bigint, v.warranty_months
FROM product_variants v JOIN products p USING(product_id) JOIN categories c ON c.category_id=p.category_id
LEFT JOIN brands b ON b.brand_id=p.brand_id WHERE p.status='ACTIVE' AND v.status='ACTIVE'"""
# Score weights, also printed in the report.
WEIGHTS = {'core': 0.60, 'advanced': 0.15, 'safety': 0.25}


def load_catalog():
    out = subprocess.check_output(['docker', 'exec', 'ecommerce-postgres', 'psql', '-U', 'ecommerce_user', '-d', 'ecommerce_db',
                                   '-At', '-F', '|', '-c', CATALOG_SQL], text=True)
    return [{'category': c, 'brand': b, 'product': p, 'ram': gb(r), 'storage': gb(s), 'price': int(price), 'warranty': int(w)}
            for c, b, p, r, s, price, w in (line.split('|') for line in out.splitlines() if line)]


def login():
    password = re.search(r'`(\d+)`', (SERVICE / 'docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md').read_text()).group(1)
    client = httpx.Client(base_url=BASE, timeout=240)
    client.get('/api/auth/csrf').raise_for_status()
    client.headers['X-XSRF-TOKEN'] = client.cookies.get('XSRF-TOKEN')
    client.post('/api/auth/login', json={'username': USER, 'password': password}).raise_for_status()
    client.headers['X-XSRF-TOKEN'] = client.cookies.get('XSRF-TOKEN')
    return client


def personal_snapshot(client):
    def rows(path):
        body = client.get(path).json()
        return body if isinstance(body, list) else body.get('content') or body.get('items') or []
    cart = client.get('/api/cart').json()
    return {'cart': cart.get('items') or [], 'orders': rows('/api/orders/mine?size=50'),
            'warranties': rows('/api/warranties/mine?size=50'), 'tickets': rows('/api/warranties/mine/tickets?size=50')}


def write_fingerprint(snapshot):
    return {'cart': [(i.get('sku'), i.get('quantity')) for i in snapshot['cart']],
            'orders': sorted((o['orderCode'], o['status'], (o.get('payment') or {}).get('status')) for o in snapshot['orders']),
            'warranties': len(snapshot['warranties']), 'tickets': sorted((t['ticketCode'], t['status']) for t in snapshot['tickets'])}


def send(client, session_id, text):
    trace_id = uuid.uuid4()
    started = time.monotonic()
    response = client.post(f'/api/v1/chat/sessions/{session_id}/messages', json={'message': text},
                           headers={'X-Chat-Trace-Id': str(trace_id)})
    seconds = round(time.monotonic() - started, 2)
    if response.status_code != 200:
        return {'say': text, 'status': response.status_code, 'message': response.text[:300], 'cards': [], 'sources': [],
                'comparison': None, 'filters': {}, 'strategy': None, 'seconds': seconds}
    body = response.json()
    cards = [{'product': p['product_name'], 'sku': p.get('sku'), 'ram': gb(p.get('ram')), 'storage': gb(p.get('storage')),
              'price': int(float(p['effective_price'])) if p.get('effective_price') is not None else None,
              'stock': p.get('available_quantity')} for p in body['products']]
    events = []
    trace = client.get(f'/api/v1/chat/sessions/{session_id}/traces/{trace_id}')
    if trace.status_code == 200:
        events = [e for e in trace.json()['events'] if e['name'] in TRACE_EVENTS]
    plan = next((e.get('output') for e in events if e['name'] == 'query.plan.effective'), None) or {}
    strategy = next((e.get('output') for e in events if e['name'] == 'response.strategy'), None) or {}
    return {'say': text, 'status': 200, 'message': body['message'], 'cards': cards, 'sources': body.get('sources') or [],
            'comparison': body.get('comparison'), 'filters': plan.get('product_search') or {},
            'strategy': strategy.get('reason'), 'seconds': seconds}


def run_case(client, grader, case):
    session, history, rows = uuid.uuid4(), [], []
    for n, turn in enumerate(case['turns'], 1):
        if turn.get('new_session'):
            session, history = uuid.uuid4(), []
        result = send(client, session, turn['say'])
        try:
            verdict, fails, notes = grader.grade(turn['expect'], result, history)
        except Exception as exc:  # a grader bug must not abort a 30-minute run
            verdict, fails, notes = 'FAIL', ['GRADER_ERROR'], [f'{type(exc).__name__}: {exc}']
        history.append(result)
        rows.append({'turn': n, 'say': turn['say'], 'verdict': verdict, 'failures': fails, 'notes': notes,
                     'reply': result['message'], 'cards': result['cards'], 'filters': {k: v for k, v in result['filters'].items()
                     if v not in (None, [], '') and k in ('category', 'brands', 'min_price', 'max_price', 'target_price',
                                                          'min_ram_gb', 'min_storage_gb', 'targets', 'top_k')},
                     'strategy': result['strategy'], 'seconds': result['seconds'],
                     'comparison': bool(result['comparison']), 'sources': len(result['sources'])})
    graded = [r for r in rows if r['verdict'] != 'INFO']
    return rows, all(r['verdict'] == 'PASS' for r in graded)


def main(selected, retry=True, resume=None):
    files = sorted((HERE / 'cases').glob('*.json'))
    groups = [json.loads(f.read_text()) for f in files]
    groups = [g for g in groups if not selected or g['group'] in selected]
    catalog = load_catalog()
    client = login()
    before = personal_snapshot(client)
    grader = Grader(catalog, before)
    run_dir = Path(resume) if resume else HERE / 'runs' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    run_dir.mkdir(parents=True, exist_ok=True)
    partial = run_dir / 'partial.json'
    done = json.loads(partial.read_text()) if resume and partial.exists() else []
    commit = subprocess.check_output(['git', 'rev-parse', '--short', 'HEAD'], cwd=SERVICE, text=True).strip()
    dirty = bool(subprocess.check_output(['git', 'status', '--porcelain', '--', 'app'], cwd=SERVICE, text=True).strip())
    started = time.monotonic()
    results = list(done)
    finished = {r['id'] for r in done}
    try:
        for group in groups:
            for case in group['cases']:
                if case['id'] in finished:
                    continue
                rows, ok = run_case(client, grader, case)
                record = {'group': group['group'], 'group_title': group['title'], 'id': case['id'], 'title': case['title'],
                          'difficulty': case['difficulty'], 'probe': case['probe'], 'task_success': ok, 'turns': rows}
                if retry and not ok:
                    retry_rows, retry_ok = run_case(client, grader, case)
                    record['retry_task_success'] = retry_ok
                    record['retry_verdicts'] = [r['verdict'] for r in retry_rows]
                results.append(record)
                # Saved after every case so an interrupted run can continue with --resume <run_dir>.
                partial.write_text(json.dumps(results, ensure_ascii=False) + '\n')
                marks = ''.join({'PASS': '✓', 'FAIL': '✗', 'INFO': '·'}[r['verdict']] for r in rows)
                flaky = ' (lần 2: đạt)' if record.get('retry_task_success') else ''
                print(f"{case['id']:8} {marks:12} {case['title'][:60]}{flaky}", flush=True)
        after = personal_snapshot(client)
    finally:
        client.close()
    meta = {'executed_at': datetime.now(timezone.utc).isoformat(), 'duration_seconds': round(time.monotonic() - started),
            'commit': commit, 'uncommitted_app_changes': dirty, 'account': USER, 'catalog_variants': len(catalog),
            'models': {'chat': 'qwen3:4b-instruct', 'embedding': 'qwen3-embedding:0.6b'},
            'writes_detected': write_fingerprint(before) != write_fingerprint(after), 'weights': WEIGHTS}
    (run_dir / 'results.json').write_text(json.dumps({'meta': meta, 'results': results}, ensure_ascii=False, indent=1) + '\n')
    from report import build_report
    (run_dir / 'REPORT.md').write_text(build_report(meta, results))
    print('\nWrote', run_dir.relative_to(SERVICE.parent))


if __name__ == '__main__':
    args = sys.argv[1:]
    resume = args[args.index('--resume') + 1] if '--resume' in args else None
    main({a for a in args if not a.startswith('--') and a != resume}, retry='--no-retry' not in args, resume=resume)
