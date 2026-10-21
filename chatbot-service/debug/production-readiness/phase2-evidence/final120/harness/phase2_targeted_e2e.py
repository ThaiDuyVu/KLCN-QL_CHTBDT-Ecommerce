"""Phase 2 targeted real E2E: cart hotfix cases A/B/C and Group E order-code probes.

Real Qwen + real Spring (8081 via 5174 proxy) + chatbot default profile (8091) + disposable clone only.
Case A needs a CUSTOMER without any cart: one synthetic account is inserted into the clone
(users/customers/user_roles, password hash copied from seed.customer2) and removed at the end.
No cart/order/payment row is written by this script; their counts are checked before/after.
"""
import sys, uuid, json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[6]
sys.path.insert(0, str(ROOT / 'chatbot-service/debug/post-upgrade-validation/harness'))
from common import *
from runtime import login, public
import psycopg

S = load(PRIVATE / 'state.json')
assert S['container'].startswith('chatbot-post-upgrade-'), 'disposable clone only'
CHAT = 'http://localhost:8091'
USERNAME = 'phase2.nocart.' + uuid.uuid4().hex[:8]


def business_counts(conn):
    return {t: conn.execute(f'select count(*) from {t}').fetchone()[0]
            for t in ['carts', 'cart_items', 'orders', 'payments', 'order_items', 'inventory']}


def ask(client, message, session=None):
    session = session or str(uuid.uuid4()); trace_id = str(uuid.uuid4())
    r = client.post(f'{CHAT}/api/v1/chat/sessions/{session}/messages', headers={'X-Chat-Trace-Id': trace_id}, json={'message': message})
    trace = client.get(f'{CHAT}/api/v1/chat/sessions/{session}/traces/{trace_id}')
    events = trace.json().get('events', []) if trace.status_code == 200 else []
    calls = [public(e.get('output')) for e in events if e['name'] in ('backend.http_status', 'backend.product_result')]
    plan = next((e.get('output') for e in events if e['name'] == 'query.plan.initial'), None) or {}
    body = r.json() if r.headers.get('content-type', '').startswith('application/json') else {'detail': r.text[:300]}
    return {'prompt': message, 'http_status': r.status_code, 'message': body.get('message', body.get('detail')),
            'cards': len(body.get('products', []) or []), 'intent': plan.get('intent'),
            'references': plan.get('references'), 'backend_calls': [(c or {}).get('path') for c in calls],
            'trace_id': trace_id, 'strategy': [public(e.get('output')) for e in events if e['name'] == 'response.strategy'][-1:]}


def main():
    out = {'executed_at': now(), 'execution': 'REAL_QWEN_REAL_SPRING_ISOLATED_DB', 'chat_port': 8091, 'cases': {}}
    with psycopg.connect(S['dsn']) as conn:
        before = business_counts(conn)
        hashed, role = conn.execute("select u.password, ur.role_id from users u join user_roles ur on ur.user_id=u.user_id where u.username='seed.customer2'").fetchone()
        uid, cid = uuid.uuid4(), uuid.uuid4()
        conn.execute("insert into users(user_id,username,password,email,display_name,status) values(%s,%s,%s,%s,%s,'ACTIVE')",
                     (uid, USERNAME, hashed, USERNAME + '@example.test', 'Khách kiểm thử chưa có giỏ'))
        conn.execute('insert into customers(customer_id,user_id,full_name) values(%s,%s,%s)', (cid, uid, 'Khách kiểm thử chưa có giỏ'))
        conn.execute('insert into user_roles(user_id,role_id) values(%s,%s)', (uid, role))
    try:
        nocart, me = login(USERNAME); c1, me1 = login('seed.customer1'); c2, me2 = login('seed.customer2')
        try:
            raw_cart = nocart.get('/api/cart'); raw_cart.raise_for_status(); body = raw_cart.json()
            out['backend_contract_case_a'] = {'http_status': raw_cart.status_code, 'cartId_is_null': body.get('cartId') is None,
                                              'items': body.get('items'), 'subtotal': body.get('subtotal'), 'keys': sorted(body)}
            prompt = 'Cho tôi xem giỏ hàng của mình.'
            out['cases']['CART_A_never_created'] = ask(nocart, prompt)
            out['cases']['CART_B_initialized_empty'] = ask(c2, prompt)
            out['cases']['CART_C_with_items'] = ask(c1, prompt)
            cart1 = c1.get('/api/cart').json()
            out['cart_c_source_of_truth'] = [{'sku': i['sku'], 'quantity': i['quantity'], 'effectivePrice': i['effectivePrice']} for i in cart1['items']] + [{'subtotal': cart1['subtotal']}]
            out['cases']['ORDER_code_without_word_don_hang'] = ask(c1, 'ORD-DIAG-NOPAY thanh toán chưa?')
            out['cases']['ORDER_lowercase_code'] = ask(c1, 'Đơn hàng ord-diag-nopay của tôi đã thanh toán chưa?')
            out['cases']['ORDER_foreign_code'] = ask(c2, 'Đơn hàng ORD-DIAG-DELIVERED của tôi đã thanh toán chưa?')
            out['cases']['ORDER_none_for_new_customer'] = ask(nocart, 'Đơn hàng gần nhất của tôi thế nào?')
            with psycopg.connect(S['dsn']) as conn:
                out['case_a_carts_after'] = conn.execute('select count(*) from carts where customer_id=%s', (cid,)).fetchone()[0]
            out['latest_order_code_customer1'] = next(iter(sorted(
                [(o['orderDate'], o['orderCode']) for o in (c1.get('/api/orders/mine').json().get('content') or [])], reverse=True)), None)
        finally:
            for client in (nocart, c1, c2): client.close()
    finally:
        with psycopg.connect(S['dsn']) as conn:
            sessions = [r[0] for r in conn.execute('select session_id from chat_sessions where customer_id=%s', (cid,)).fetchall()]
            for sid in sessions:
                conn.execute('delete from chat_history where session_id=%s', (sid,))
            conn.execute('delete from chat_sessions where customer_id=%s', (cid,))
            conn.execute('delete from refresh_tokens where user_id=%s', (uid,))
            conn.execute('delete from user_roles where user_id=%s', (uid,))
            conn.execute('delete from customers where customer_id=%s', (cid,))
            conn.execute('delete from users where user_id=%s', (uid,))
            after = business_counts(conn)
            remaining = conn.execute('select count(*) from users where username=%s', (USERNAME,)).fetchone()[0]
        out['business_counts_before'] = before; out['business_counts_after'] = after
        out['business_counts_unchanged'] = before == after; out['synthetic_account_removed'] = remaining == 0
        out['synthetic_account'] = 'phase2.nocart.<random> (clone only, removed)'
        dump(OUT / 'evidence/phase2_targeted_e2e.json', out)
    print(json.dumps({k: (v['http_status'], v['intent'], (v['message'] or '')[:90]) for k, v in out['cases'].items()}, ensure_ascii=False, indent=1))
    print('case_a_backend', out.get('backend_contract_case_a'), 'carts_after', out.get('case_a_carts_after'), 'unchanged', out['business_counts_unchanged'])


if __name__ == '__main__':
    main()
