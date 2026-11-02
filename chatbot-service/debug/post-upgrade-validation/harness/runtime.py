"""Real Qwen + real Spring API requests; publish only explicit evidence projections."""
import os,re,time,uuid,asyncio,fcntl
from decimal import Decimal
from contextlib import contextmanager
import httpx,psycopg
from common import *
STATE=load(PRIVATE/'state.json');CASES=load(OUT/'cases.json');RAW=PRIVATE/'raw';RAW.mkdir(exist_ok=True)
PORTS={'default':8091,'mixed_stock':8091,'empty_warehouse':8092,'no_warehouse':8093}

def password():
 value=os.getenv('DIAGNOSTIC_CUSTOMER_PASSWORD')
 if value:return value
 doc=(ROOT/'chatbot-service/docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md').read_text()
 return re.search(r'`(\d+)`',doc).group(1)  # documented synthetic development password only

def login(user='seed.customer1'):
 c=httpx.Client(base_url='http://localhost:5174',timeout=180)
 c.get('/api/auth/csrf').raise_for_status();c.headers['X-XSRF-TOKEN']=c.cookies.get('XSRF-TOKEN')
 c.post('/api/auth/login',json={'username':user,'password':password()}).raise_for_status()
 me=c.get('/api/auth/me').json();assert me['roleName']=='CUSTOMER' and me['customerId']!=me['userId']
 return c,me

def public(value):
 if isinstance(value,list):return [public(x) for x in value]
 if isinstance(value,dict):
  blocked={'customer_id','customerId','user_id','userId','display_name','displayName','fullName','recipientName','recipientPhone','shippingAddress','serial_number','serialNumber','imei_numbers','imeiNumbers','issue_description','issueDescription','address','cookies','cookie','authorization','password','embeddings','embedding','embedding_vector','vector'}
  private_ids={'session_id','sessionId','order_id','orderId','payment_id','paymentId','warranty_id','warrantyId','ticket_id','ticketId','cart_id','cartId','history_id','historyId'}
  return {k:('synthetic-'+k+'-'+hashlib.sha256(str(v).encode()).hexdigest()[:12] if k in private_ids and v is not None else public(v)) for k,v in value.items() if k not in blocked and k!='relevant_history' and k!='customer'}
 if isinstance(value,str):
  value=re.sub(r'(/api/orders/mine/)([0-9a-f-]{36})',lambda m:m[1]+'synthetic-order-'+hashlib.sha256(m[2].encode()).hexdigest()[:12],value)
  return scrub(value)
 return value

def persist(row):
 with (PRIVATE/'results.lock').open('a') as lease:
  fcntl.flock(lease,fcntl.LOCK_EX)
  path=OUT/'results.json';rows=load(path) if path.exists() else [];rows=[r for r in rows if r['id']!=row['id']];rows.append(row);dump(path,rows)
  fcntl.flock(lease,fcntl.LOCK_UN)

def get(c,path,port=None):
 r=c.get(f'http://localhost:{port}'+path if port else path);r.raise_for_status();return r.json()

def truth(c,ground,profile):
 checks=[];warehouse=None if profile=='no_warehouse' else STATE['empty_warehouse_id'] if profile=='empty_warehouse' else STATE['warehouse_id']
 for match in ground.get('products',[]):
  path='/api/v1/products/'+match['product_id']+'/detail';r=c.get(path,params={'warehouseId':warehouse} if warehouse else {});r.raise_for_status();detail=r.json()
  for v in match['eligible_variants']:
   row=next((x for x in detail['variants'] if x['variantId']==v['variant_id']),None)
   checks.append({'kind':'eligible_variant','product_id':match['product_id'],'variant_id':v['variant_id'],'identity':bool(row) and match['product_name']==detail['product']['productName'],'price':bool(row) and Decimal(str(v['effective_price']))==Decimal(str(row['effectivePrice'])),'quantity':bool(row) and v['available_quantity']==row.get('availableQuantity'),'configuration':bool(row) and all(v.get(k)==row.get(a) for k,a in [('sku','sku'),('ram','ram'),('storage','storage'),('color','color'),('warranty_months','warrantyMonths')])})
 for chunk in ground.get('knowledge_chunks',[]):
  with psycopg.connect(STATE['dsn']) as conn:
   row=conn.execute('SELECT c.document_id,c.content,d.status FROM knowledge_chunks c JOIN knowledge_documents d ON d.document_id=c.document_id WHERE c.chunk_id=%s',(chunk['chunk_id'],)).fetchone()
  checks.append({'kind':'knowledge_chunk','chunk_id':chunk['chunk_id'],'mapping':bool(row) and str(row[0])==chunk['source']['document_id'],'text':bool(row) and row[1]==chunk['content'],'eligible':bool(row) and row[2]=='PUBLISHED'})
 return checks

@contextmanager
def profile_fixture(profile):
 if profile!='mixed_stock':yield;return
 with psycopg.connect(STATE['dsn']) as conn:
  row=conn.execute("SELECT inventory_id,quantity,reserved_quantity FROM inventory i JOIN product_variants v ON i.variant_id=v.variant_id WHERE v.sku='DEV-X15-BLK-256' AND i.warehouse_id=%s",(STATE['warehouse_id'],)).fetchone();assert row
  conn.execute('UPDATE inventory SET quantity=0,reserved_quantity=0 WHERE inventory_id=%s',(row[0],))
 try:yield
 finally:
  with psycopg.connect(STATE['dsn']) as conn:conn.execute('UPDATE inventory SET quantity=%s,reserved_quantity=%s WHERE inventory_id=%s',(row[1],row[2],row[0]))

class Runner:
 def __init__(self):
  self.primary,self.me=login();self.secondary,_=login('seed.customer2');self.sessions={};self.latest=None
 def sid(self,key):
  if key not in self.sessions:self.sessions[key]=str(uuid.uuid4())
  return self.sessions[key]
 def send(self,case,sid=None,setup=False):
  id=case['id'];profile=case.get('profile','default');port=PORTS.get(profile,8091);sid=sid or self.sid(case['session_class'] if case['session_class'] in ['REG_A','REG_B'] else id);tid=str(uuid.uuid4());started=now();t=time.monotonic()
  r=self.primary.post(f'http://localhost:{port}/api/v1/chat/sessions/{sid}/messages',headers={'X-Chat-Trace-Id':tid},json={'message':case['prompt']})
  try:response=r.json()
  except ValueError:response={'detail':scrub(r.text[:500])}
  trace_r=self.primary.get(f'http://localhost:{port}/api/v1/chat/sessions/{sid}/traces/{tid}');trace=trace_r.json() if trace_r.status_code==200 else None
  if trace:dump(RAW/(id+'.json'),trace)
  row={'id':id,'prompt':case['prompt'],'expected':case['expected'],'started_at':started,'duration_ms':round((time.monotonic()-t)*1000,2),'execution':'REAL_QWEN_REAL_SPRING_ISOLATED_DB','customer_class':'synthetic_CUSTOMER_primary','session_class':case['session_class'] if case['session_class'] in ['REG_A','REG_B'] else 'fresh_or_explicit_owned_setup','profile':profile,'http_status':r.status_code,'response':public(response),'trace_id':tid if trace else None,'trace_evidence':f'evidence/traces/{id}.json' if trace else None,'verdict':'EXECUTED_PENDING_DIAGNOSIS','preconditions':case.get('setup',case['fixture'])}
  events=(trace or {}).get('events',[])
  def out(name):return next((e.get('output') for e in events if e['name']==name),None)
  ground=out('response.grounded_context') or {};queryctx=out('conversation.context') or {}
  row.update(query_plan=out('query.plan.initial'),effective_query_plan=out('query.plan.effective'),model_query_plan=out('query.validation'),history_count=len(queryctx.get('recent_messages',[])),strategy=out('response.strategy'),reference_resolution=out('conversation.reference_resolution'))
  if isinstance(row['strategy'],dict):row['strategy']={k:v for k,v in row['strategy'].items() if k!='messages'}
  row['grounding']=public(ground);row['source_of_truth_checks']=truth(self.primary,ground,profile)
  row['backend_calls']=[];row['stage_timings']=[]
  for e in events:
   if e['duration_ms'] is not None:row['stage_timings'].append({k:e.get(k) for k in ['name','offset_ms','duration_ms','status']})
   if e['name'] in ['backend.product_http','backend.customer_http']:
    args=e.get('input') or {};row['backend_calls'].append(public({k:args.get(k) for k in ['method','path','params','kwargs'] if k in args}))
  # Personal context contracts exclude recipient/address, but minimize further for publication.
  customer=(ground.get('customer_context') or {})
  row['customer_evidence']={k:public(v) for k,v in customer.items() if v is not None}
  safe_events=[]
  keep={'query.read_only_guard','query.validation','query.domain_validation','query.validation_failed','query.fallback','query.plan.initial','query.plan.effective','conversation.reference_resolution','product.search','product.identity_resolution','product.stock_evidence','product.resolution_failed','product.cards','product.evidence_refreshed','knowledge.retrieve','knowledge.vector_search','knowledge.relevance','knowledge.fallback','customer.order_selection','retrieval.summary','response.strategy'}
  for e in events:
   if e['name'] in keep:
    v={k:public(x) for k,x in e.items() if k not in ['input','error']}
    if e['name']=='response.strategy' and isinstance(v.get('output'),dict):v['output'].pop('messages',None)
    safe_events.append(v)
  if trace:dump(OUT/row['trace_evidence'],{'trace_id':tid,'status':trace.get('status'),'dropped_events':trace.get('dropped_events'),'events':safe_events,'stage_timings':row['stage_timings'],'backend_calls':row['backend_calls']})
  if not setup:persist(public(row))
  else:dump(OUT/f'evidence/setups/{id}.json',public(row))
  self.latest={'sid':sid,'tid':tid,'row':row};print(id,r.status_code,(row['query_plan'] or {}).get('intent'),len(response.get('products',[])) if isinstance(response,dict) else '?',flush=True)
  return row,sid
 def security(self):
  # Use an existing real response in the current process, before any restart.
  last=self.latest;sid,tid=last['sid'],last['tid'];self.sessions['SECURITY']=sid
  observations={
   'T57':{'unauthenticated_status':httpx.post(f'http://localhost:8091/api/v1/chat/sessions/{uuid.uuid4()}/messages',json={'message':'Giỏ hàng của tôi đang có những gì?'}).status_code},
   'T58':{'owner_status':self.primary.get(f'/api/v1/chat/sessions/{sid}/messages').status_code,'other_status':self.secondary.get(f'/api/v1/chat/sessions/{sid}/messages').status_code},
   'T60':{'owner_status':self.primary.get(f'/api/v1/chat/sessions/{sid}/traces/{tid}').status_code,'other_status':self.secondary.get(f'/api/v1/chat/sessions/{sid}/traces/{tid}').status_code,'other_list_count':len(self.secondary.get(f'/api/v1/chat/sessions/{sid}/traces').json())}}
  for id,obs in observations.items():
   case=next(c for c in CASES if c['id']==id);persist({'id':id,'prompt':case['prompt'],'expected':case['expected'],'execution':'REAL_SECURITY_API','customer_class':'anonymous' if id=='T57' else 'primary_and_secondary_synthetic_CUSTOMER','observations':obs,'trace_id':None,'verdict':'EXECUTED_PENDING_DIAGNOSIS','started_at':now()})
 def close(self):self.primary.close();self.secondary.close()

if __name__=='__main__':
 runner=Runner();start=now()
 dump(OUT/'evidence/runtime_boundary.json',{'started_at':start,'trace_file_offset':(ROOT/'.local/logs/chatbot-traces.jsonl').stat().st_size if (ROOT/'.local/logs/chatbot-traces.jsonl').exists() else 0,'plan_sha256':hashlib.sha256((OUT/'cases.json').read_bytes()).hexdigest()})
 try:
  for case in CASES:
   if case['mode']!='real_chat_api':continue
   try:
    sid=None
    if case.get('setup'):
     prompt='Xin chào' if case['id']=='N24' else 'Xiaomi 15 còn hàng không?' if case['id']=='N13' else case['setup']
     setup=dict(case,id=case['id']+'-setup',prompt=prompt,profile='default',expected='Setup establishes displayed evidence/session, not a counted case.')
     _,sid=runner.send(setup,setup=True)
     if case['id']=='N24':runner.primary.post(f'/api/v1/chat/sessions/{sid}/close').raise_for_status()
    with profile_fixture(case['profile']):runner.send(case,sid=sid)
   except Exception as exc:
    persist({'id':case['id'],'prompt':case['prompt'],'expected':case['expected'],'execution':'HARNESS_OR_DEPENDENCY_FAILURE','verdict':'BLOCKED_ENVIRONMENT','error_type':type(exc).__name__,'started_at':now()});print(case['id'],'BLOCKED',type(exc).__name__,flush=True)
  runner.security()
  # Keep only non-secret session/message correlation in the ignored local state.
  state=load(PRIVATE/'state.json');state['runtime_sessions']=runner.sessions;state['browser_session']=runner.sessions['REG_A'];state['security_session']=runner.sessions['SECURITY'];dump(PRIVATE/'state.json',state)
 finally:runner.close()
 dump(OUT/'evidence/runtime_window.json',{'started_at':start,'completed_at':now()})
