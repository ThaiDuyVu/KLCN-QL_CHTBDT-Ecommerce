"""Read-only final facts, stop disposable services and prove source data unchanged."""
import os,signal,time,json,subprocess,stat
from common import *
from runtime import login,public
from environment import source_snapshot
import psycopg
state=load(PRIVATE/'state.json');c,me=login();contracts=[]
for code,oid in state['fixture_order_ids'].items():
 r=c.get('/api/orders/mine/'+oid)
 try:body=r.json()
 except ValueError:body={}
 contracts.append({'synthetic_order_code':code,'http_status':r.status_code,'payment':{k:v for k,v in (body.get('payment') or {}).items() if k in ['status','paymentMethod','amount']},'error_class':body.get('error') or body.get('code'),'message':body.get('message')})
dump(OUT/'evidence/owned_read_contracts.json',public(contracts));c.close()
with psycopg.connect(state['dsn']) as conn:
 facts={'products':conn.execute('SELECT count(*) FROM products').fetchone()[0],'variants':conn.execute('SELECT count(*) FROM product_variants').fetchone()[0],'active_promotions':conn.execute("SELECT count(*) FROM promotions WHERE status='ACTIVE' AND start_date<=CURRENT_TIMESTAMP AND end_date>=CURRENT_TIMESTAMP").fetchone()[0],'brands':conn.execute('SELECT count(*) FROM brands').fetchone()[0],'category_names':[r[0] for r in conn.execute("SELECT category_name FROM categories WHERE status='ACTIVE' ORDER BY category_name")],'knowledge_titles':conn.execute('SELECT title,status,document_type FROM knowledge_documents ORDER BY title').fetchall(),'own_fixture_payment_counts':{r[0]:r[1] for r in conn.execute("SELECT o.order_code,count(p.payment_id) FROM orders o LEFT JOIN payments p ON p.order_id=o.order_id WHERE o.order_code LIKE 'ORD-DIAG-%%' GROUP BY o.order_code")}}
dump(OUT/'evidence/final_fixture_facts.json',facts)
repeats=load(OUT/'evidence/repeats/results.json');summary={}
for base in ['T07','T15','T30','T31','T51']:
 group=[r for r in repeats if r['repeated_case']==base];signatures=[((r.get('query_plan') or {}).get('intent'),(r.get('strategy') or {}).get('strategy'),tuple(x['variant_id'] for x in r['response'].get('products',[]))) for r in group]
 summary[base]={'runs':len(group),'http_200':sum(r['http_status']==200 for r in group),'same_intent_strategy_cards':len(set(signatures))==1,'trace_ids':[r['trace_id'] for r in group],'semantic_checks':[]}
 for r in group:
  s=(r.get('effective_query_plan') or {}).get('product_search') or {};products=r['response'].get('products',[]);query=r.get('query_plan') or {};message=r['response'].get('message','')
  correct=(s.get('min_ram_gb')==16 and float(s.get('max_price',0))==20000000 and len(products)==1) if base=='T07' else (len(products)==1 and products[0]['product_name']=='Xiaomi 15') if base=='T15' else (not products and r['history_count']==0 and bool(query.get('clarification'))) if base=='T30' else (len(r['response'].get('sources',[]))==1 and 'DEMO' in message and '7 ngày' in message) if base=='T31' else (not products and bool(query.get('clarification')))
  correct=correct and all(all(v for k,v in check.items() if k not in ['kind','product_id','variant_id','chunk_id']) for check in r['source_of_truth_checks']);summary[base]['semantic_checks'].append(bool(correct))
dump(OUT/'evidence/repeats/summary.json',summary)
# No app/user process from the normal5173/8080/8090 stack is stopped here.
for name,process in state['processes'].items():
 try:os.kill(process['pid'],signal.SIGTERM)
 except ProcessLookupError:pass
time.sleep(1)
removed=subprocess.run(['docker','rm','-f',state['container']],capture_output=True,text=True)
subprocess.run(['docker','start','ecommerce-postgres'],check=True,capture_output=True)
for i in range(40):
 try:after=source_snapshot();break
 except subprocess.CalledProcessError:time.sleep(.5)
before=load(OUT/'evidence/source-business-before.json');dump(OUT/'evidence/source-business-after.json',after)
if not state['source_was_running']:subprocess.run(['docker','stop','ecommerce-postgres'],check=True,capture_output=True)
report={'completed_at':now(),'temporary_container_removed':removed.returncode==0,'source_business_equal':before==after,'original_reports_unchanged':preserve_hashes()==load(OUT/'evidence/original_reports_sha256.json'),'original_source_db_running_state_restored':True,'normal_app_processes_not_modified':True,'disposable_fixture_rows_remaining':0 if removed.returncode==0 else 'UNKNOWN','isolated_process_ids_stopped':len(state['processes'])}
dump(OUT/'evidence/cleanup.json',report);assert report['source_business_equal'] and report['original_reports_unchanged'] and report['temporary_container_removed']
(PRIVATE/'synthetic-source.dump').unlink(missing_ok=True)
for key in ['dsn','pg_password','fixture_order_ids','runtime_sessions','browser_session','security_session']:state.pop(key,None)
state['cleaned_up']=True;dump(PRIVATE/'state.json',state)
os.chmod(PRIVATE,0o700)
for p in PRIVATE.rglob('*'):
 if p.is_file():os.chmod(p,0o600)
print('Cleanup verified; source business and original reports unchanged')
