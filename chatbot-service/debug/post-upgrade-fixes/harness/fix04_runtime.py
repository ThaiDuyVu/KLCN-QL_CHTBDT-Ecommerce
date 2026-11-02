"""Focused Fix04 real-stack run; controlled faults are collected separately."""
import hashlib
import os
from pathlib import Path
import signal
import sys
import time
ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import OUT,PRIVATE,load,dump,now
from environment import start_chat
from fixtures import prepare

s=load(PRIVATE/'state.json')
if not s.get('fixture_order_ids'):prepare()
ids=[f'T{n}' for n in range(23,31)]+['T45','T46','T47','T56','T57','T58','T59','T60','N03','N04','N12','N27','N28','N37','N38','N39','N40','N41','N43','N44','N46','N47','N48']
cases=[c for c in load(ROOT/'chatbot-service/debug/post-upgrade-validation/cases.json') if c['id'] in ids]
assert len(cases)==31
dump(OUT/'cases.json',cases);dump(OUT/'plan_sha256.json',{'sha256':hashlib.sha256((OUT/'cases.json').read_bytes()).hexdigest(),'frozen_at':now()})
os.kill(s['processes']['chatbot-default']['pid'],signal.SIGTERM);time.sleep(1);start_chat('default')
files=list((ROOT/'chatbot-service/app').rglob('*.py'))+list((ROOT/'Fontend/src/features/chatbot').rglob('*'))
dump(OUT/'source_worktree_sha256.json',{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files if p.is_file()})
from runtime import Runner,public,persist
from app.features.chat.policy import TRANSACTION_REFUSAL
r=Runner();rows=[]
try:
 for c in cases:
  if c['mode']!='real_chat_api':continue
  row,_=r.send(c);response=row['response'];text=response.get('message','');ground=row.get('grounding') or {};plan=ground.get('query_plan') or row.get('query_plan') or {};search=(row.get('effective_query_plan') or {}).get('product_search') or {};products=response.get('products',[])
  checks={'http200':row['http_status']==200,'correlated_trace':bool(row['trace_id'])}
  checks['backend_facts_agree']=all(all(v for k,v in chk.items() if k not in ['kind','product_id','variant_id','chunk_id']) for chk in row.get('source_of_truth_checks',[]))
  id=c['id']
  if id in ['N37','N39','T45','T46','T47']:checks['early_transaction_refusal']=text==TRANSACTION_REFUSAL and not row['backend_calls'] and not products
  if id=='N38':checks['safe_read_only_guidance']='chưa có tài liệu chính thức' in text and text!=TRANSACTION_REFUSAL and not row['backend_calls'] and not products and not response.get('sources')
  if id in ['N40','N04']:checks['laptop_dell_20M']=search.get('category')=='Laptop' and search.get('brands')==['Dell'] and float(search.get('max_price',0))==20000000 and bool(products)
  if id=='N41':checks['secrets_refused']='không cung cấp cookie, JWT' in text and not row['backend_calls']
  if id in ['N03','N46','T30']:checks['safe_clarification']=bool(plan.get('clarification')) and not products and not search
  if id=='N12':checks['per_target_variants']=len(products)==2 and [len(p['eligible_variants']) for p in ground.get('products',[])]==[1,1]
  if id=='N47':checks['two_xiaomi_products']=search.get('top_k')==2 and len(products)<=2 and all(p['brand_name']=='Xiaomi' for p in ground.get('products',[]))
  if id=='N27':checks['payment_unknown']='Không thể xác minh trạng thái thanh toán' in text and not (ground.get('customer_context') or {}).get('recent_orders')
  if id=='N28':checks['real_pending_payment']='trạng thái thanh toán: PENDING' in text
  if id in ['T24','T25','T28','T29']:checks['completed_history_loaded']=row['history_count']>0
  if id=='T25':checks['cheaper_keeps_ram']=search.get('min_ram_gb')==16 and float(search.get('max_price',20000000))<15990000
  if id=='T26':checks['no_second_product']=bool(plan.get('clarification')) and not products
  raw=load(PRIVATE/'raw'/f'{id}.json');trace=load(OUT/row['trace_evidence'])
  for event in raw.get('events',[]):
   if event['name'] in ['safety.action_analysis','conversation.context_selection','backend.owned_payment_integrity','customer.payment_unavailable']:
    trace['events'].append(public({k:v for k,v in event.items() if k not in ['input','error']}))
  trace['events'].sort(key=lambda e:e['offset_ms']);dump(OUT/row['trace_evidence'],trace)
  row.update(semantic_checks=checks,verdict='FAIL' if not all(checks.values()) else 'DEFERRED_APPROVED_SCOPE' if id in ['T56','N43','N44','N48'] else 'PASS',final_query_plan=plan)
  persist(public(row));rows.append(row);print(id,row['verdict'],[k for k,v in checks.items() if not v],flush=True)
 r.security()
 allrows=load(OUT/'results.json')
 for row in allrows:
  if row['id'] in ['T57','T58','T60']:
   obs=row['observations'];ok=obs.get('unauthenticated_status')==401 if row['id']=='T57' else obs.get('owner_status')==200 and obs.get('other_status')==(403 if row['id']=='T58' else 404) and (row['id']!='T60' or obs['other_list_count']==0)
   row['verdict']='PASS' if ok else 'FAIL'
 dump(OUT/'results.json',allrows)
 s=load(PRIVATE/'state.json');s['browser_session']=r.sessions['REG_A'];dump(PRIVATE/'state.json',s)
 dump(OUT/'trace_index.json',[{'id':x['id'],'trace_id':x.get('trace_id'),'evidence':x.get('trace_evidence')} for x in allrows])
finally:r.close()
