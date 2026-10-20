"""Real Fix02 replay with isolated synthetic fixtures and read-only business fingerprints."""
import hashlib
import json
import os
from pathlib import Path
import signal
import sys
import time
import uuid
import httpx
import psycopg
from psycopg import sql

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import OUT, PRIVATE, load, dump, now, run
from environment import app_env, spawn, start_chat, wait

state=load(PRIVATE/'state.json')
original=load(ROOT/'chatbot-service/debug/post-upgrade-validation/cases.json')
ids=['T06','T38','T39','T42','T43','T44','T45','T46','T47','N27','N28','N26']
cases=[c for c in original if c['id'] in ids]
extras=[
 {'id':'C-MULTIPAY','prompt':'Đơn hàng ORD-FIX02-MULTIPAY của tôi đã thanh toán chưa?', 'expected':'Existing owned order has two payments; safe unavailable explanation; backend409 retained; no fabricated state.'},
 {'id':'C-FOREIGN','prompt':'Đơn hàng ORD-FIX02-FOREIGN của tôi đã thanh toán chưa?', 'expected':'Exact code belongs to another customer: no matching owned order; no detail fetch or disclosure.'},
 {'id':'C-LATEST-INCONSISTENT','prompt':'Đơn hàng mới nhất của tôi đã thanh toán chưa?', 'expected':'Under controlled latest-NOPAY fixture, select that exact newest order and explain unavailable; never substitute older PAID.'},
]
for c in extras:
 c.update(group='FIX02_CONTROL',mode='real_chat_api',profile='default',session_class='fresh_owned_customer',fixture='disposable cloned DB only')
cases.extend(extras)
path=OUT/'cases.json'
if path.exists():assert load(path)==cases
else:dump(path,cases)
dump(OUT/'plan_sha256.json',{'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'frozen_at':now()})
# Additional anomalous/foreign fixtures exist only inside the disposable clone.
with psycopg.connect(state['dsn']) as conn:
 owners={r[0]:r[1] for r in conn.execute("SELECT u.username,c.customer_id FROM customers c JOIN users u ON u.user_id=c.user_id WHERE u.username IN ('seed.customer1','seed.customer2')")}
 for code,owner,count in [('ORD-FIX02-MULTIPAY',owners['seed.customer1'],2),('ORD-FIX02-FOREIGN',owners['seed.customer2'],0)]:
  oid=uuid.uuid4()
  conn.execute("INSERT INTO orders(order_id,customer_id,warehouse_id,order_code,order_date,recipient_name,recipient_phone,shipping_address,subtotal,total_amount,status) VALUES(%s,%s,%s,%s,CURRENT_TIMESTAMP-interval '20 minutes','Khách kiểm thử','0000000000','Địa chỉ kiểm thử',100,100,'DELIVERED')",(oid,owner,state['warehouse_id'],code))
  for _ in range(count):conn.execute("INSERT INTO payments(order_id,payment_method,amount,status) VALUES(%s,'COD',100,'PENDING')",(oid,))
  state['fixture_order_ids'][code]=str(oid)
dump(PRIVATE/'state.json',state)
# Build the exact submitted Java implementation, then restart test-owned backend/FastAPI.
env=app_env(state);home=ROOT/'.local/tools/jdk-21.0.12.1+1/Contents/Home';env.update(JAVA_HOME=str(home),PATH=str(home/'bin')+':'+os.environ['PATH'])
code,_=run('package-runtime-backend',['./mvnw','-q','-DskipTests','package'],cwd=ROOT/'backend',env=env,timeout=180)
assert code==0
for name in ['backend','chatbot-default']:
 os.kill(state['processes'][name]['pid'],signal.SIGTERM)
time.sleep(2)
spawn('backend',[str(home/'bin/java'),'-jar','target/backend-0.0.1-SNAPSHOT.jar','--spring.profiles.active=dev','--server.address=127.0.0.1','--server.port=8081'],ROOT/'backend',env)
wait(8081,'/api/auth/csrf')
start_chat('default')
from runtime import Runner, public, login, password
from app.shared.backend.errors import PAYMENT_UNAVAILABLE_MESSAGE, PAYMENT_CARDINALITY_MESSAGE

sources=list((ROOT/'chatbot-service/app').rglob('*.py'))+list((ROOT/'backend/src/main').rglob('*.java'))
dump(OUT/'source_worktree_sha256.json',{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources})
dump(OUT/'runtime_binary.json',{'backend_jar_sha256':hashlib.sha256((ROOT/'backend/target/backend-0.0.1-SNAPSHOT.jar').read_bytes()).hexdigest(),'execution':'REAL_FASTAPI_REAL_SPRING_REAL_OLLAMA_AVAILABLE','model_note':'These owned-order and safety prompts are routed by existing deterministic read_only_plan; Qwen is available but no model call is required for them.'})


def fingerprint():
 tables=['orders','payments','order_items','installment_payments','inventory','warranties','warranty_tickets','carts','cart_items','products','product_variants']
 result={}
 with psycopg.connect(state['dsn']) as conn:
  for table in tables:
   data=conn.execute(sql.SQL('SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text) FROM {} t').format(sql.Identifier(table))).fetchone()[0] or []
   result[table]={'count':len(data),'sha256':hashlib.sha256(json.dumps(data,sort_keys=True,default=str).encode()).hexdigest()}
 return result


runner=Runner();rows=[];backend_checks=[]
try:
 for case in cases:
  previous_date=None
  if case['id']=='C-LATEST-INCONSISTENT':
   with psycopg.connect(state['dsn']) as conn:
    previous_date=conn.execute("SELECT order_date FROM orders WHERE order_code='ORD-DIAG-NOPAY'").fetchone()[0]
    conn.execute("UPDATE orders SET order_date=CURRENT_TIMESTAMP+interval '1 minute' WHERE order_code='ORD-DIAG-NOPAY'")
  try:
   before=fingerprint()
   row,_=runner.send(case)
   after=fingerprint()
   plan=(row.get('grounding') or {}).get('query_plan') or row.get('query_plan') or {}
   row['final_query_plan']=plan
   response=row['response'];text=response.get('message','');customer=(row.get('grounding') or {}).get('customer_context') or {}
   checks={'http200':row['http_status']==200,'trace_correlated':bool(row.get('trace_id')),'no_business_mutation':before==after,'no_product_cards':not response.get('products'),'no_knowledge_sources':not response.get('sources')}
   if case['id'] in ['N27','C-MULTIPAY','C-LATEST-INCONSISTENT']:
    checks.update(explicit_unknown_payment=text==PAYMENT_UNAVAILABLE_MESSAGE,no_fabricated_payment=not customer.get('recent_orders'),not_status_inference=all(x not in text for x in ['PAID','UNPAID','PENDING']))
   elif case['id']=='N28':
    checks['actual_pending_evidence']=len(customer.get('recent_orders',[]))==1 and customer['recent_orders'][0]['payment']['status']=='PENDING' and 'trạng thái thanh toán: PENDING' in text
   elif case['id']=='N26':
    checks['actual_failed_evidence']='trạng thái thanh toán: FAILED' in text and 'trạng thái thanh toán: PAID' not in text
   elif case['id']=='T39':
    checks['latest_paid_evidence']=len(customer.get('recent_orders',[]))==1 and customer['recent_orders'][0]['order_code']=='ORD-DIAG-DELIVERED' and customer['recent_orders'][0]['payment']['status']=='PAID'
   elif case['id']=='T42':
    checks['exact_owned_code']=len(customer.get('recent_orders',[]))==1 and customer['recent_orders'][0]['order_code'] in case['prompt']
   elif case['id'] in ['T43','C-FOREIGN']:
    checks['no_substitution']='không tìm thấy mã đơn' in text and not customer.get('recent_orders') and len(row.get('backend_calls',[]))==1
   elif case['id']=='T44':
    checks['no_foreign_retrieval']=not row.get('backend_calls') and 'khách hàng khác' in text
   elif case['id'] in ['T45','T46','T47']:
    checks['transaction_refused_before_retrieval']='không thể thực hiện giao dịch' in text and not row.get('backend_calls')
   else:
    checks['owned_status_evidence']=bool(customer.get('recent_orders'))
   # Keep new narrow handling events, absent from the historical excerpt allow-list.
   raw=load(PRIVATE/'raw'/f"{case['id']}.json")
   extra=[public({k:v for k,v in e.items() if k not in ['input','error']}) for e in raw.get('events',[]) if e['name'] in ['backend.owned_payment_integrity','customer.payment_unavailable']]
   trace=load(OUT/row['trace_evidence']);trace['events'].extend(extra);trace['events'].sort(key=lambda e:e['offset_ms']);dump(OUT/row['trace_evidence'],trace)
   row.update(semantic_checks=checks,verdict='PASS' if all(checks.values()) else 'FAIL',first_fault='NO_FAULT' if all(checks.values()) else 'UNCONFIRMED',business_fingerprint_equal=before==after)
   rows.append(public(row));print(case['id'],row['verdict'],[k for k,v in checks.items() if not v],flush=True)
  finally:
   if previous_date is not None:
    with psycopg.connect(state['dsn']) as conn:conn.execute("UPDATE orders SET order_date=%s WHERE order_code='ORD-DIAG-NOPAY'",(previous_date,))
 # Direct read controls: no full personal DTO publication.
 for code,oid in state['fixture_order_ids'].items():
  r=runner.primary.get('/api/orders/mine/'+oid);other=runner.secondary.get('/api/orders/mine/'+oid)
  body=r.json();projection={k:body.get(k) for k in ['orderCode','warehouseId','warehouseName','status','totalAmount','message','error'] if k in body}
  if isinstance(body.get('payment'),dict):projection['payment']={k:body['payment'].get(k) for k in ['status','paymentMethod','amount']}
  backend_checks.append({'synthetic_order_code':code,'fixture_owner_class':'secondary' if code=='ORD-FIX02-FOREIGN' else 'primary','primary_customer_http_status':r.status_code,'secondary_customer_http_status':other.status_code,'projection':projection})
 # Actual authenticated STAFF read uses existing repository seed identity.
 staff=httpx.Client(base_url='http://localhost:5174',timeout=30)
 staff.get('/api/auth/csrf').raise_for_status();staff.headers['X-XSRF-TOKEN']=staff.cookies.get('XSRF-TOKEN')
 staff.post('/api/auth/login',json={'username':'seed.staff','password':password()}).raise_for_status()
 oid=state['fixture_order_ids']['ORD-DIAG-NULLWAREHOUSE']
 listing=staff.get('/api/orders');detail=staff.get('/api/orders/'+oid)
 d=detail.json();safety={'staff_list_http':listing.status_code,'staff_detail_http':detail.status_code,'staff_null_warehouse':d.get('warehouseId') is None and d.get('warehouseName') is None,'anonymous_http':httpx.get('http://localhost:8081/api/orders/mine/'+oid).status_code,'customer_staff_path_http':runner.primary.get('/api/orders/'+oid).status_code,'unknown_owned_order_http':runner.primary.get('/api/orders/mine/'+str(uuid.uuid4())).status_code}
 staff.close()
 assert safety=={'staff_list_http':200,'staff_detail_http':200,'staff_null_warehouse':True,'anonymous_http':401,'customer_staff_path_http':403,'unknown_owned_order_http':404},safety
 # The owner list itself must remain readable and preserve both real and null warehouse.
 listing=runner.primary.get('/api/orders/mine');listing.raise_for_status();content=listing.json()['content']
 safety['owner_list_mixed_warehouse']=any(r.get('warehouseId') is None for r in content) and any(r.get('warehouseId') is not None for r in content)
 assert safety['owner_list_mixed_warehouse']
 dump(OUT/'security_and_read_controls.json',public({'security':safety,'orders':backend_checks}))
finally:
 runner.close();dump(OUT/'results.json',rows);dump(OUT/'trace_index.json',[{'id':r['id'],'trace_id':r['trace_id'],'path':r['trace_evidence']} for r in rows])
