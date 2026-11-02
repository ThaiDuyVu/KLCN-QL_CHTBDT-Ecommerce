"""Run original case/session/warehouse semantics; enrich current trace/customer evidence."""
import sys,runpy,json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[6];OLD=ROOT/'chatbot-service/debug/post-upgrade-validation/harness';sys.path.insert(0,str(OLD))
from common import *
if '--enrich-only' not in sys.argv:runpy.run_path(str(OLD/'runtime.py'),run_name='__main__')
from runtime import login,public
import psycopg
from decimal import Decimal
client,me=login();s=load(PRIVATE/'state.json');rows=load(OUT/'results.json')
try:
 for row in rows:
  raw=PRIVATE/'raw'/f"{row['id']}.json"
  if not raw.exists():continue
  trace=load(raw);events=trace.get('events',[])
  row['source_commit']=load(OUT/'source_hashes.json')['source_commit']
  row['source_hash_manifest']='source_hashes.json'
  row['execution_class']=next(c['mode'] for c in load(OUT/'cases.json') if c['id']==row['id'])
  row['model_inference_executed']=any(e['name']=='query.model_attempt' for e in events)
  row['safety_actions']=[public(e.get('output')) for e in events if e['name']=='safety.action_analysis']
  row['context_selection']=[public(e.get('output')) for e in events if e['name']=='conversation.context_selection']
  if row.get('trace_evidence'):
   art=load(OUT/row['trace_evidence']);art['events'].extend(public({k:v for k,v in e.items() if k not in ['input','error']}) for e in events if e['name'] in ['safety.action_analysis','conversation.context_selection','backend.owned_payment_integrity','customer.payment_unavailable']);art['events'].sort(key=lambda e:e['offset_ms']);dump(OUT/row['trace_evidence'],art)
  context=row.get('customer_evidence') or {};checks=[]
  with psycopg.connect(s['dsn']) as conn:
   for order in context.get('recent_orders') or []:
    data=conn.execute('SELECT order_id,status,total_amount FROM orders WHERE order_code=%s AND customer_id=%s',(order['order_code'],me['customerId'])).fetchone()
    check={'kind':'owned_order','exists_and_owned':bool(data),'order_code':order['order_code'],'status':bool(data) and data[1]==order['status'],'total_amount':bool(data) and Decimal(str(data[2]))==Decimal(str(order['total_amount']))}
    if order.get('payment'):
     payments=conn.execute('SELECT status,payment_method,amount FROM payments WHERE order_id=%s',(data[0],)).fetchall() if data else []
     p=order['payment'];check['payment']=len(payments)==1 and payments[0][0]==p['status'] and payments[0][1]==p['payment_method'] and Decimal(str(payments[0][2]))==Decimal(str(p['amount']))
    checks.append(check)
  if context.get('cart'):
   response=client.get('/api/cart');response.raise_for_status();cart=response.json();items=cart.get('items',[]) if isinstance(cart,dict) else cart
   checks.append({'kind':'cart','item_count':len(items)==len(context['cart']['items']),'items':all(any(x['sku']==i['sku'] and x['quantity']==i['quantity'] and Decimal(str(x['effectivePrice']))==Decimal(str(i['effective_price'])) for x in items) for i in context['cart']['items'])})
  for key,path,codekey in [('warranties','/api/warranties/mine','sku'),('warranty_tickets','/api/warranties/mine/tickets','ticket_code')]:
   if context.get(key):
    response=client.get(path);response.raise_for_status();body=response.json();body=body if isinstance(body,list) else body.get('content',[])
    checks.append({'kind':key,'count':len(body)==len(context[key]),'status_and_identity':all(any(x.get('sku' if key=='warranties' else 'ticketCode')==i[codekey] and x['status']==i['status'] for x in body) for i in context[key])})
  row['personal_source_of_truth_checks']=checks
 dump(OUT/'results.json',rows)
finally:client.close()
print('Real runtime and read-only source-of-truth projections collected.')
