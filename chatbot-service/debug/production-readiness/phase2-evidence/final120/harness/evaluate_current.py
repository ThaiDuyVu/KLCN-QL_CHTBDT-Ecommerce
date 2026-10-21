import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[6]
sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
"""Evaluate frozen expectations. Adds observations/verdicts, never rewrites cases.json."""
from decimal import Decimal
from common import *
from runtime import public
import re
rows=load(OUT/'results.json');assert len(rows)==120
by={r['id']:r for r in rows};cases={c['id']:c for c in load(OUT/'cases.json')}
plan_hash=load(OUT/'evidence/plan_sha256.json')['cases_sha256'];assert hashlib.sha256((OUT/'cases.json').read_bytes()).hexdigest()==plan_hash

def search(r):return ((r.get('effective_query_plan') or {}).get('product_search') or (r.get('query_plan') or {}).get('product_search') or {})
def products(r):return (r.get('grounding') or {}).get('products') or []
def variants(r):return [v for p in products(r) for v in p['eligible_variants']]
def cards(r):return (r.get('response') or {}).get('products') or []
def text(r):return str((r.get('response') or {}).get('message',''))
def chunks(r):return (r.get('grounding') or {}).get('knowledge_chunks') or []
def intent(r):return (r.get('query_plan') or {}).get('intent')
def customer(r):return r.get('customer_evidence') or {}
def no_cards(r):return not cards(r)
def clarify(r):
 final=(r.get('grounding') or {}).get('query_plan') or r.get('query_plan') or {}
 return bool(final.get('clarification')) and no_cards(r)
def names(r):return {p['product_name'] for p in products(r)}
def cap(v):
 m=re.search(r'(\d+(?:\.\d+)?)\s*(GB|TB)',str(v),re.I)
 return float(m[1])*(1024 if m[2].upper()=='TB' else 1) if m else None

def money(v):return Decimal(str(v))
def stock(r):return all((v.get('available_quantity') or 0)>0 for v in variants(r))
def range_check(r,max_price=None,min_ram=None,min_storage=None,brand=None,category=None):
 s=search(r)
 return (max_price is None or s.get('max_price') is not None and money(s['max_price'])==max_price) and (min_ram is None or s.get('min_ram_gb')==min_ram) and (min_storage is None or s.get('min_storage_gb')==min_storage) and (brand is None or brand in s.get('brands',[])) and (category is None or s.get('category')==category)
def demo(r):return bool(chunks(r)) and all('DEMO' in c['source']['title'] for c in chunks(r)) and 'không phải chính sách' in text(r)
def return_source(r):return len(chunks(r))==1 and 'đổi trả' in chunks(r)[0]['source']['title'].casefold()
def ords(r):return customer(r).get('recent_orders') or []
def no_retrieval(r):return not r.get('backend_calls') and no_cards(r) and not chunks(r)
def record(r,label,condition):r.setdefault('acceptance_checks',[]).append({'check':label,'passed':bool(condition)})

for r in rows:
 id=r['id'];r['acceptance_checks']=[]
 if r.get('verdict')=='BLOCKED_ENVIRONMENT':continue
 if id.startswith('B'):
  record(r,'Controlled expectation evaluated from explicit transport/DB/API observation',r.get('observations',{}).get('expected_satisfied',False))
  continue
 if id in ['T57','T58','T59','T60']:
  o=r['observations']
  if id=='T57':ok=o['unauthenticated_status']==401
  elif id=='T58':ok=o['owner_status']==200 and o['other_status']==403
  elif id=='T59':ok=o['browser_reload_text_equal'] and o['real_fastapi_restart_text_equal'] and o['history_ids_equal'] and o['feedback_rating_before']==1 and o['feedback_rating_after']==0 and o['trace_status_after_restart']==404
  else:ok=o['owner_status']==200 and o['other_status']==404 and o['other_list_count']==0
  record(r,'Authorization or browser restart assertion',ok);continue
 expected_http=409 if id=='N24' else 200
 record(r,'Expected HTTP status',r.get('http_status')==expected_http)
 checks=r.get('source_of_truth_checks') or []
 record(r,'Every retrieved product variant / chunk matches actual backend or DB',all(all(v for k,v in c.items() if k not in ['kind','product_id','variant_id','chunk_id']) for c in checks))
 record(r,'Cards agree with grounded identity/variant/price/quantity',all(any(p['product_id']==c['product_id'] and p['product_name']==c['product_name'] and any(v['variant_id']==c['variant_id'] and money(v['effective_price'])==money(c['effective_price']) and v['available_quantity']==c.get('available_quantity') for v in p['eligible_variants']) for p in products(r)) for c in cards(r)))
 record(r,'Product text names every grounded SKU; sources map to quoted chunks',all(v['sku'] in text(r) for v in variants(r)) and {s['chunk_id'] for s in (r.get('response') or {}).get('sources',[])}=={c['chunk_id'] for c in chunks(r)} and all(c['content'] in text(r) for c in chunks(r)))
 s=search(r)
 eligible=all((s.get('min_price') is None or money(v['effective_price'])>=money(s['min_price'])) and (s.get('max_price') is None or money(v['effective_price'])<=money(s['max_price'])) and (s.get('min_ram_gb') is None or cap(v.get('ram')) is not None and cap(v['ram'])>=s['min_ram_gb']) and (s.get('min_storage_gb') is None or cap(v.get('storage')) is not None and cap(v['storage'])>=s['min_storage_gb']) and (not s.get('in_stock_only') or (v.get('available_quantity') or 0)>0) for v in variants(r))
 record(r,'All grounded variants satisfy effective hard filters',eligible)
 if id in ['T01','T02']:ok=intent(r)=='GREETING' and no_retrieval(r) and r.get('strategy',{}).get('reason')=='greeting'
 elif id=='T03':ok=intent(r)=='HELP' and no_cards(r)
 elif id=='T04':ok=range_check(r,category='Điện thoại') and bool(products(r)) and all(p['category_name']=='Điện thoại' for p in products(r))
 elif id=='T05':ok=return_source(r) and demo(r)
 elif id in ['T06','T38']:ok=intent(r)=='ORDER_STATUS' and bool(ords(r)) and all(o['order_code'] in text(r) and o['status'] in text(r) for o in ords(r)) and 'chưa có dữ liệu thanh toán' in text(r)
 elif id in ['T07','T23']:ok=range_check(r,20000000,16,category='Laptop') and bool(cards(r))
 elif id=='T08':ok=range_check(r,20000000,16,brand='Dell',category='Laptop')
 elif id=='T09':ok=range_check(r,1000000,brand='Apple',category='Điện thoại') and no_cards(r)
 elif id=='T10':ok=range_check(r,20000000,32,category='Laptop') and no_cards(r)
 elif id=='T11':ok=range_check(r,20000000,brand='Xiaomi',category='Điện thoại') and bool(cards(r))
 elif id=='T12':ok=range_check(r,min_ram=12,category='Điện thoại')
 elif id=='T13':ok=range_check(r,min_ram=16,category='Laptop') and s.get('in_stock_only') and stock(r)
 elif id=='T14':ok=clarify(r) and not s
 elif id in ['T15','T52']:ok=intent(r)=='PRODUCT_DETAIL' and names(r)=={'Xiaomi 15'} and len(variants(r))==3 and 'STOCK' in r['query_plan']['requested_fields']
 elif id=='T16':ok=names(r)=={'Xiaomi 15'} and len(variants(r))==3 and 'PRICE' in r['query_plan']['requested_fields']
 elif id=='T17':ok=len(variants(r))==1 and variants(r)[0]['sku']=='DEV-X15-GRN-512'
 elif id=='T18':ok=names(r)=={'Xiaomi 15'} and {'Đen','Trắng','Xanh lá'}=={v['color'] for v in variants(r)}
 elif id=='T19':ok=names(r)=={'iPhone 15'} and len(cards(r))==1
 elif id=='T20':ok=names(r)=={'Samsung Galaxy A55'} and all(v['warranty_months']==12 for v in variants(r)) and intent(r)=='PRODUCT_DETAIL'
 elif id=='T21':ok=len(variants(r))==1 and variants(r)[0]['sku']=='DEV-X15-BLK-256'
 elif id=='T22':ok=intent(r)=='PRODUCT_COMPARE' and names(r)=={'Dell Inspiron 15','Dell XPS 13'} and len(cards(r))==2
 elif id=='T24':ok=r['history_count']>0 and len(cards(r))==1 and cards(r)[0]['variant_id']==cards(by['T23'])[0]['variant_id']
 elif id=='T25':ok=r['history_count']>0 and range_check(r,15989999,16,category='Laptop')
 elif id=='T26':ok=clarify(r) and (r.get('reference_resolution') or {}).get('status')=='NO_REFERENCE'
 elif id=='T27':ok=range_check(r,brand='Xiaomi',category='Điện thoại') and len(cards(r))>=2
 elif id=='T28':ok=r['history_count']>0 and len(cards(r))==1 and cards(r)[0]['variant_id']==cards(by['T27'])[0]['variant_id']
 elif id=='T29':ok=cards(r)[0]['product_id']==cards(by['T28'])[0]['product_id'] and all(cap(v['storage'])==512 for v in variants(r)) and s.get('min_ram_gb') is None
 elif id=='T30':ok=r['history_count']==0 and clarify(r)
 elif id in ['T31','T32','T33']:ok=return_source(r) and demo(r) and '7 ngày kể từ lúc nhận hàng' in text(r)
 elif id=='T34':ok=intent(r)=='KNOWLEDGE_QA' and len(chunks(r))==1 and 'thanh toán' in chunks(r)[0]['source']['title'].casefold() and not ords(r)
 elif id in ['T35','T50']:ok=demo(r) and return_source(r) and '30 ngày' not in text(r)
 elif id=='T36':ok=intent(r)=='KNOWLEDGE_QA' and not chunks(r) and 'chưa đủ dữ liệu' in text(r)
 elif id=='T37':ok=bool(customer(r).get('cart',{}).get('items')) and all(i['sku'] in text(r) and str(i['quantity']) in text(r) for i in customer(r)['cart']['items']) and 'Tạm tính' in text(r)
 elif id=='T39':ok=len(ords(r))==1 and ords(r)[0]['order_code']=='ORD-DIAG-DELIVERED' and ords(r)[0]['payment']['status']=='PAID' and 'trạng thái thanh toán: PAID' in text(r)
 elif id in ['T40','N29']:ok=len(customer(r).get('warranties',[]))>=2 and 'ACTIVE' in text(r) and 'EXPIRED' in text(r)
 elif id in ['T41','N30']:ok=len(customer(r).get('warranty_tickets',[]))==1 and 'BH-DIAG-RECEIVED' in text(r) and 'RECEIVED' in text(r)
 elif id=='T42':ok=len(ords(r))==1 and ords(r)[0]['order_code']=='ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29' and ords(r)[0]['payment']['status']=='PENDING'
 elif id in ['T43','N25']:ok=not ords(r) and 'không tìm thấy mã đơn' in text(r)
 elif id in ['T44','T48']:ok=intent(r)=='UNKNOWN' and no_retrieval(r) and 'không cung cấp' in text(r)
 elif id in ['T45','T46','T47','N37','N39']:ok='không thể thực hiện giao dịch' in text(r) and no_retrieval(r)
 elif id=='T49':ok=names(r)=={'Xiaomi 15'} and '100 máy' not in text(r)
 elif id=='T51':ok=clarify(r) and 'chưa xác định chính xác' in text(r)
 elif id=='T53':ok=range_check(r,brand='Xiaomi',category='Điện thoại') and s.get('in_stock_only') and stock(r)
 elif id=='T54':ok=clarify(r) and no_cards(r)
 elif id=='T55':ok=range_check(r,20000000,16,512,'Dell','Laptop')
 elif id in ['T56','N43']:ok=clarify(r)
 elif id=='N01':ok=range_check(r,20000000,brand='Xiaomi',category='Điện thoại')
 elif id=='N02':ok=range_check(r,25000000,brand='Apple',category='Điện thoại')
 elif id=='N03':ok=clarify(r) and no_cards(r) and not search(r)
 elif id=='N04':ok=range_check(r,20000000,brand='Dell',category='Laptop')
 elif id=='N05':ok=range_check(r,min_ram=12,min_storage=1024,category='Điện thoại')
 elif id=='N06':ok=range_check(r,0,category='Điện thoại') and no_cards(r)
 elif id=='N07':ok=len(variants(r))==1 and variants(r)[0]['sku']=='DEV-X15-WHT-512'
 elif id=='N08':ok=clarify(r) and no_cards(r)
 elif id=='N09':ok=len(variants(r))==1 and variants(r)[0]['sku']=='DEV-X15-WHT-512'
 elif id=='N10':ok=len(variants(r))==2 and all(cap(v['storage'])>=512 and cap(v['ram'])>=12 for v in variants(r))
 elif id=='N11':ok=s.get('min_price') is not None and money(s['min_price'])==16000000 and range_check(r,40000000,16,category='Laptop')
 elif id=='N12':ok=clarify(r) or len(variants(r))==2 and {v['sku'] for v in variants(r)}=={'DEV-X15-BLK-256','DEV-IP15-BLK-128'}
 elif id=='N13':ok=bool(variants(r)) and all(v['available_quantity']==0 for v in variants(r)) and 'hết hàng tại kho được chọn' in text(r)
 elif id=='N14':ok=bool(variants(r)) and all(v['available_quantity'] is None for v in variants(r)) and 'chưa chọn kho' in text(r)
 elif id=='N15':ok=any(v['available_quantity']==0 for v in variants(r)) and any((v['available_quantity'] or 0)>0 for v in variants(r))
 elif id=='N16':ok=len(variants(r))==1 and variants(r)[0]['sku']=='DEV-X15-BLK-256' and variants(r)[0]['available_quantity']==0
 elif id=='N17':ok=s.get('in_stock_only') and stock(r) and all(v['sku']!='DEV-X15-BLK-256' for v in variants(r))
 elif id=='N18':ok='tại kho được chọn' in text(r) and 'tất cả các kho đều' not in text(r)
 elif id in ['N19','N20','N23']:ok=clarify(r)
 elif id=='N21':ok=names(r)=={'Dell Inspiron 15'} and r['history_count']>0 and len(variants(r))==1
 elif id=='N22':ok=names(r)=={'Xiaomi 15'} and r['history_count']>0 and all(cap(v['storage'])==512 for v in variants(r))
 elif id=='N24':ok=r['http_status']==409 and 'closed' in str(r.get('response','')).lower()
 elif id=='N26':ok=len(ords(r))==1 and ords(r)[0]['order_code']=='ORD-DIAG-FAILED' and 'trạng thái thanh toán: FAILED' in text(r)
 elif id=='N27':ok='Không thể xác minh trạng thái thanh toán' in text(r) and not ords(r) and all(x not in text(r) for x in ['PAID','UNPAID','PENDING'])
 elif id=='N28':ok=r['http_status']==200  # frozen expectation: schema-valid nullable row must not NPE
 elif id=='N31':ok=return_source(r) and demo(r) and 'lúc nhận hàng' in text(r)
 elif id=='N32':ok=return_source(r) and demo(r) and 'hóa đơn' in text(r) and 'phụ kiện' in text(r)
 elif id=='N33':
  ok='không xác nhận' in text(r) and demo(r)
  record(r,'Citations must not include unrelated payment evidence for home-exchange question',not any('thanh toán' in c['source']['title'].casefold() for c in chunks(r)))
 elif id=='N34':ok=not chunks(r) and 'chưa đủ dữ liệu' in text(r)
 elif id=='N35':ok=return_source(r) and demo(r) and '7 ngày' in text(r)
 elif id=='N36':ok=not chunks(r) and 'chưa đủ dữ liệu' in text(r)
 elif id=='N38':ok='không thể thực hiện giao dịch' not in text(r) and no_cards(r)
 elif id=='N40':ok=range_check(r,20000000,brand='Dell',category='Laptop') and bool(cards(r))
 elif id=='N41':ok='không cung cấp' in text(r) or 'chưa đủ dữ liệu' in text(r)
 elif id=='N42':ok=clarify(r) and not cards(r) and '1 đồng' not in text(r) or names(r)=={'iPhone 15'} and all(money(v['effective_price'])>1 for v in variants(r))
 elif id=='N44':ok=clarify(r) or ('chỉ' in text(r) and 'so sánh' in text(r))
 elif id=='N45':ok=clarify(r) and 'Sản phẩm tồn tại' in text(r)
 elif id=='N46':ok=clarify(r) and not s
 elif id=='N47':ok=clarify(r) or len(cards(r))==2 and all(p['brand_name']=='Xiaomi' for p in products(r))
 elif id=='N48':ok=clarify(r) or 'từng' in text(r) or 'chưa' in text(r)  # deferred actual scope separately recorded
 else:raise AssertionError('Case lacks semantic acceptance check: '+id)
 record(r,'Frozen per-case semantic expectation',ok)
 r['verdict']='PASS' if all(c['passed'] for c in r['acceptance_checks']) else 'FAIL';r['first_fault']='NO_FAULT' if r['verdict']=='PASS' else 'UNCONFIRMED'


from collections import Counter
for r in rows:
 id=r['id'];checks=r.get('acceptance_checks',[])
 if r.get('personal_source_of_truth_checks'):
  record(r,'Authenticated backend/DB personal evidence agrees with grounded context',all(all(v for k,v in c.items() if k not in ['kind','order_code']) for c in r['personal_source_of_truth_checks']))
 if r.get('verdict')=='BLOCKED_ENVIRONMENT':
  r.update(observed_verdict='BLOCKED_ENVIRONMENT',release_scope_disposition='BLOCKED_ENVIRONMENT',first_fault='CONFIGURATION');continue
 observed='PASS' if checks and all(c['passed'] for c in checks) else 'FAIL'
 if observed=='PASS' and id in ['T51','T59','N18']:observed='PASS_WITH_WARNINGS'
 r['observed_verdict']=observed
 r['release_scope_disposition']='SUPPORTED' if observed=='PASS' else 'KNOWN_LIMITATION' if observed=='PASS_WITH_WARNINGS' else 'OPEN_DEFECT'
 if id in ['T56','N43','N44','N48']:
  r['release_scope_disposition']='DEFERRED_MULTI_INTENT';r['verdict']='DEFERRED_APPROVED_SCOPE';r['first_fault']='UNSUPPORTED_CAPABILITY'
 else:
  r['verdict']=observed
  r['first_fault']='NO_FAULT' if observed=='PASS' else 'UNCONFIRMED'
 if id in ['N33','N34'] and observed=='FAIL':r['release_scope_disposition']='OPEN_DEFERRED_RAG';r['first_fault']='KNOWLEDGE_RETRIEVAL'
 if observed=='PASS_WITH_WARNINGS':r['first_fault']='UNSUPPORTED_CAPABILITY'
 r['source_commit']=load(OUT/'source_hashes.json')['source_commit'];r['frozen_case']=cases[id]
 r['execution_class']=cases[id]['mode']
 raw=PRIVATE/'raw'/(id+'.json')
 if raw.exists():
  trace=load(raw);r['trace_status']=trace.get('status');r['dropped_trace_events']=trace.get('dropped_events')
  r['upstream_statuses']=[public(e.get('output')) for e in trace['events'] if e['name'] in ['backend.http_status','ollama.http_status']]
rows.sort(key=lambda r:(0 if r['id'].startswith('T') else 1 if r['id'].startswith('N') else 2,int(r['id'][1:])))
assert len(rows)==120 and len({r['id'] for r in rows})==120
dump(OUT/'results.json',rows)
dump(OUT/'verdict_summary.json',{'total_distinct_cases':120,'release_verdicts':dict(Counter(r['verdict'] for r in rows)),'observed_execution_verdicts':dict(Counter(r['observed_verdict'] for r in rows)),'release_scope_dispositions':dict(Counter(r['release_scope_disposition'] for r in rows)),'historical_comparison_only':{'PASS':99,'PASS_WITH_WARNINGS':8,'FAIL':9,'DEFERRED_APPROVED_SCOPE':4}})
dump(OUT/'trace_index.json',[{'case_id':r['id'],'trace_id':r.get('trace_id'),'path':r.get('trace_evidence'),'execution':r['execution'],'stage_timings':r.get('stage_timings',[])} for r in rows])
print('Final120',dict(Counter(r['verdict'] for r in rows)))
print('Observed failure checks',[(r['id'],[c['check'] for c in r['acceptance_checks'] if not c['passed']]) for r in rows if r['observed_verdict']=='FAIL'])
