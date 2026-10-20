"""Focused real-stack replay. Run only with the disposable diagnostic environment.

Uses the immutable post-upgrade harness for authenticated requests/sanitization;
expectations below are fixed before collecting any new runtime result.
"""
import hashlib
import json
import os
from pathlib import Path
import signal
import sys
import time

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'chatbot-service/debug/post-upgrade-validation/harness'))
from common import OUT, PRIVATE, dump, load, now
from environment import start_chat

OLD = ROOT / 'chatbot-service/debug/post-upgrade-validation'
IDS = [f'T{n:02}' for n in range(7, 31)] + [f'T{n}' for n in range(51, 57)] + ['N03', 'N04', 'N12', 'N46', 'N47']
cases = [dict(c, fixture='read-only synthetic development catalog in disposable PostgreSQL clone')
         for c in load(OLD / 'cases.json') if c['id'] in IDS]
assert len(cases) == 35
path = OUT / 'cases.json'
if path.exists():
    assert load(path) == cases, 'Never rewrite frozen expectations'
else:
    dump(path, cases)
dump(OUT / 'evidence/plan_sha256.json', {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'frozen_at': now()})

dump(OUT / 'evidence/source_worktree_sha256.json', {
    str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
    for p in (ROOT / 'chatbot-service/app').rglob('*.py')
})

# Reload test-owned FastAPI only, to ensure current working-tree code is executed.
s = load(PRIVATE / 'state.json')
os.kill(s['processes']['chatbot-default']['pid'], signal.SIGTERM)
time.sleep(1)
start_chat('default')
from runtime import Runner, public
from app.features.product_advisor.normalization import capacity_gb


def check(row):
    id = row['id']
    plan = row.get('query_plan') or {}
    effective = (row.get('effective_query_plan') or {}).get('product_search') or plan.get('product_search') or {}
    ground = row.get('grounding') or {}
    products = ground.get('products', [])
    cards = row['response'].get('products', [])
    text = row['response'].get('message', '')
    checks = {'http_200': row['http_status'] == 200, 'trace_correlated': bool(row.get('trace_id'))}
    checks['backend_evidence_agrees'] = all(all(v for k, v in c.items() if k not in {'kind', 'product_id', 'variant_id', 'chunk_id'}) for c in row.get('source_of_truth_checks', []))
    checks['cards_bind_eligible_variants'] = all(any(c['product_id'] == p['product_id'] and
        any(c['variant_id'] == v['variant_id'] and all(str(c.get(k)) == str(v.get(k)) for k in ['sku', 'effective_price', 'ram', 'storage', 'color', 'available_quantity']) for v in p['eligible_variants']) for p in products) for c in cards)
    checks['card_count_bound'] = len(cards) <= effective.get('top_k', 5)
    checks['text_only_eligible_skus'] = all(v['sku'] in text for p in products for v in p['eligible_variants'])
    checks['every_variant_eligible'] = all(
        (not effective.get('category') or p['category_name'] == effective['category']) and
        (not effective.get('brands') or p['brand_name'] in effective['brands']) and
        all((effective.get('max_price') is None or float(v['effective_price']) <= float(effective['max_price'])) and
            (effective.get('min_price') is None or float(v['effective_price']) >= float(effective['min_price'])) and
            (effective.get('min_ram_gb') is None or capacity_gb(v.get('ram')) >= effective['min_ram_gb']) and
            (effective.get('min_storage_gb') is None or capacity_gb(v.get('storage')) >= effective['min_storage_gb']) and
            (not effective.get('in_stock_only') or v['available_quantity'] > 0) for v in p['eligible_variants']) for p in products)
    if id in ['N03', 'N46', 'T14', 'T30', 'T51', 'T54', 'T56']:
        checks['clarification_no_retrieval'] = bool(plan.get('clarification')) and not cards and not products and not row.get('effective_query_plan')
    if id in ['N04', 'T07', 'T08', 'T10', 'T23', 'T55']:
        checks['laptop_price_hard_filters'] = effective.get('category') == 'Laptop' and float(effective.get('max_price', 0)) == 20000000
    if id in ['T07', 'T08', 'T23', 'T55']:
        checks['ram16_preserved'] = effective.get('min_ram_gb') == 16
    if id == 'T10':
        checks['ram32_preserved'] = effective.get('min_ram_gb') == 32
    if id in ['N04', 'T08', 'T55']:
        checks['dell_preserved'] = effective.get('brands') == ['Dell']
    if id == 'T55':
        checks['storage512_preserved'] = effective.get('min_storage_gb') == 512
    if id in ['T09', 'T10']:
        checks['no_matching_cards'] = not cards
    if id in ['T15', 'T16', 'T17', 'T18', 'T52']:
        checks['exact_xiaomi15_only'] = len(cards) == 1 and all(p['product_name'] == 'Xiaomi 15' for p in products)
    if id == 'T17':
        checks['exact_16_512'] = all(capacity_gb(v['ram']) == 16 and capacity_gb(v['storage']) == 512 for p in products for v in p['eligible_variants'])
    if id == 'T19':
        checks['exact_iphone15_only'] = len(cards) == 1 and all(p['product_name'] == 'iPhone 15' for p in products)
    if id == 'T20':
        checks['product_warranty'] = len(products) == 1 and products[0]['product_name'] == 'Samsung Galaxy A55' and 'WARRANTY' in plan['requested_fields'] and 'tháng' in text
    if id == 'T21':
        checks['exact_sku'] = len(cards) == 1 and cards[0]['sku'] == 'DEV-X15-BLK-256'
    if id == 'T22':
        checks['two_dell_targets'] = {p['product_name'] for p in products} == {'Dell Inspiron 15', 'Dell XPS 13'} and len(cards) == 2
    if id in ['T24', 'T28', 'T29']:
        checks['history_reference_used'] = row['history_count'] > 0 and bool(row.get('reference_resolution'))
    if id == 'T25':
        checks['cheaper_retains_ram_and_baseline'] = row['history_count'] > 0 and effective.get('min_ram_gb') == 16 and float(effective.get('max_price', 20000000)) < 15990000
    if id == 'T26':
        checks['nonexistent_second_clarifies'] = bool(plan.get('clarification')) and not cards
    if id == 'N12':
        checks['two_exact_targets'] = [(t.get('product_name'), t.get('ram_gb'), t.get('storage_gb')) for t in effective.get('targets', [])] == [('Xiaomi 15', 12, 256), ('iPhone 15', 6, 128)]
        checks['exactly_two_variants_cards'] = len(products) == 2 and [len(p['eligible_variants']) for p in products] == [1, 1] and len(cards) == 2
        checks['two_requested_skus'] = {c['sku'] for c in cards} == {'DEV-X15-BLK-256', 'DEV-IP15-BLK-128'}
    if id == 'N47':
        checks['two_distinct_xiaomi'] = effective.get('top_k') == 2 and 0 < len(cards) <= 2 and len({c['product_id'] for c in cards}) == len(cards) and all(p['brand_name'] == 'Xiaomi' for p in products)
    row['semantic_checks'] = checks
    row['verdict'] = 'FAIL' if not all(checks.values()) else 'DEFERRED_APPROVED_SCOPE' if id == 'T56' else 'PASS_WITH_WARNINGS' if id == 'T51' else 'PASS'
    row['first_fault'] = 'UNCONFIRMED' if row['verdict'] == 'FAIL' else 'UNSUPPORTED_CAPABILITY' if id in ['T51', 'T56'] else 'NO_FAULT'
    return row


runner = Runner()
rows = []
try:
    for case in cases:
        row, _ = runner.send(case)
        row = check(row)
        rows.append(public(row))
        print(case['id'], row['verdict'], [k for k, v in row['semantic_checks'].items() if not v], flush=True)
finally:
    runner.close()
    dump(OUT / 'results.json', rows)
    dump(OUT / 'trace_index.json', [{'id': r['id'], 'trace_id': r.get('trace_id'), 'path': r.get('trace_evidence')} for r in rows])
