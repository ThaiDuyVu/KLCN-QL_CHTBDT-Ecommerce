"""Read-only catalog diagnostics; live chat uses fresh synthetic sessions only."""
from pathlib import Path
import sys,json,re,uuid,time,hashlib,subprocess
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'chatbot-service'))
import httpx
from app.features.query_understanding.normalization import DomainNormalizer,folded,contains
from app.features.query_understanding.constraints import validate_brands,price_evidence,requested_count
from app.shared.contracts.query import QueryPlan
from app.shared.contracts.context import QueryContext
OUT=Path(__file__).resolve().parent
TRACE='ebb3233d-704f-4c6f-a6bd-f9c41d84c3c9'
def dump(name,x): (OUT/name).write_text(json.dumps(x,ensure_ascii=False,indent=2,default=str)+'\n')
def safe(v):
 if isinstance(v,list): return [safe(x) for x in v]
 if isinstance(v,dict): return {k:safe(x) for k,x in v.items() if k.lower() not in {'customer','customer_id','customerid','user_id','userid','session_id','sessionid','display_name','authorization','password','cookies','messages','history_id','historyid'}}
 return v
def project(t):
 events=t.get('events',[])
 def out(n): return next((e.get('output') for e in events if e['name']==n),None)
 return {'trace_id':t['trace_id'],'timestamp_utc':t.get('started_at'),'duration_ms':t.get('duration_ms'),'status':t.get('status'),'prompt':(out('request.accepted') or {}).get('message'),'history_count':len((out('conversation.context') or {}).get('recent_messages',[])),'validated_model_plan':out('query.validation'),'normalized_plan':out('query.plan.initial'),'effective_search':out('query.plan.effective'),'response':safe(out('chat.pipeline')),'backend_reads':[safe(e.get('input')) for e in events if e['name']=='backend.product_http'],'product_search_called':any(e['name']=='product.search' for e in events),'semantic_ranking_called':any(e['name']=='product.vector_ranking' for e in events),'events':[safe({k:v for k,v in e.items() if k not in {'input','error'}}) for e in events if e['name'] in {'query.validation','query.domain_validation','query.plan.initial','query.plan.effective','response.strategy','product.search','product.cards','retrieval.summary'}],'stage_timings':[{'name':e['name'],'duration_ms':e.get('duration_ms')} for e in events if e.get('duration_ms') is not None]}
original=None
for p in (ROOT/'.local/logs').glob('chatbot-traces.jsonl*'):
 for line in p.open():
  try: t=json.loads(line)
  except ValueError: continue
  if t.get('trace_id')==TRACE: original=project(t)
assert original
dump('original-trace.json',original)
files=['chatbot-service/app/features/query_understanding/constraints.py','chatbot-service/app/features/query_understanding/normalization.py','chatbot-service/app/features/query_understanding/ollama_service.py','chatbot-service/app/features/chat/service.py','chatbot-service/app/features/customer_context/service.py','chatbot-service/app/features/response_generation/ollama_service.py']
hashes={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in files}
dump('source.json',{'commit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'branch':subprocess.check_output(['git','branch','--show-current'],text=True).strip(),'status_before':subprocess.check_output(['git','status','--short'],text=True).splitlines(),'file_sha256':hashes,'at_utc':datetime.now(timezone.utc).isoformat()})
pw=re.search(r'`(\d+)`',(ROOT/'chatbot-service/docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md').read_text()).group(1)
cases=[{'id':'original-repeat','prompt':original['prompt'],'expected':'Laptop discovery, price 20–25 million, request 2 distinct products; report shortage if fewer eligible; no brand clarification.'},{'id':'spelling-only','prompt':original['prompt'].replace('khoản','khoảng'),'expected':'Same constraints; spelling correction alone must not change identity requirements.'},{'id':'remove-generic-noun','prompt':original['prompt'].replace('sản phẩm ',''),'expected':'Same constraints and request 2; eligible cards only.'}]
dump('reproduction-plan.json',cases)
with httpx.Client(base_url='http://localhost:5173',timeout=180) as c:
 c.get('/api/auth/csrf').raise_for_status();c.headers['X-XSRF-TOKEN']=c.cookies.get('XSRF-TOKEN')
 c.post('/api/auth/login',json={'username':'seed.customer1','password':pw}).raise_for_status()
 me=c.get('/api/auth/me').json()
 categories=c.get('/api/v1/categories').json();brands=c.get('/api/v1/brands',params={'page':0,'size':100}).json();catalog=c.get('/api/v1/products',params={'page':0,'size':100}).json()
 br=brands['content'];products=catalog['content'];assert len(products)==catalog['totalElements'] and catalog['totalPages']==1
 class CapturedCatalog:
  def taxonomy(self): return [x['categoryName'] for x in categories if x.get('status')=='ACTIVE'],[x['brandName'] for x in br if x.get('status')=='ACTIVE']
  def catalog_names(self): return products
 normalizer=DomainNormalizer(CapturedCatalog());model=QueryPlan.model_validate(original['validated_model_plan'])
 probes=[]
 for prompt in [x['prompt'] for x in cases]+['Tìm laptop Lenovo dưới 25 triệu.','Gợi ý 2 sản phẩm điện thoại từ 20 đến 25 triệu.']:
  captures=[]
  def watch(frame,event,arg):
   if frame.f_code is validate_brands.__code__ and event=='exception':
    captures.append({key:frame.f_locals.get(key) for key in ['token','stated','name','known']})
   return watch
  context=QueryContext(customer={'user_id':me['userId'],'customer_id':me['customerId'],'display_name':'Synthetic'},session_id=uuid.uuid4(),current_message=prompt,recent_messages=[])
  previous=sys.gettrace();sys.settrace(watch)
  try: normalized=normalizer.normalize(model,context)
  finally: sys.settrace(previous)
  price=price_evidence(folded(prompt))
  probes.append({'prompt':prompt,'execution':'CONTROLLED_ORIGINAL_MODEL_PLAN_WITH_CAPTURED_REAL_CATALOG_NOT_NEW_MODEL_E2E','brand_exception_locals':captures,'requested_count':requested_count(folded(prompt)),'parsed_price':{'min':price.min_price,'max':price.max_price,'unresolved':price.unresolved},'normalized':normalized.model_dump(mode='json')})
 dump('brand-token-probes.json',probes)
 dump('catalog-taxonomy.json',{'total_products':catalog['totalElements'],'total_pages':catalog['totalPages'],'categories':[x['categoryName'] for x in categories],'brands':[x['brandName'] for x in br],'product_names':[x['productName'] for x in products]})
 results=[]
 for case in cases:
  sid=str(uuid.uuid4());tid=str(uuid.uuid4());start=time.monotonic()
  r=c.post(f'/api/v1/chat/sessions/{sid}/messages',headers={'X-Chat-Trace-Id':tid},json={'message':case['prompt']})
  elapsed=round((time.monotonic()-start)*1000,2)
  trace=c.get(f'/api/v1/chat/sessions/{sid}/traces/{tid}');trace.raise_for_status()
  row={**case,**project(trace.json()),'http_status':r.status_code,'http_duration_ms':elapsed,'actual_response':safe(r.json())}
  results.append(row);dump('live-reproduction.json',results)
  print(case['id'],r.status_code,row['normalized_plan']['intent'],len(r.json().get('products',[])),flush=True)
 # Verify returned card identities/prices against independent read-only backend detail.
 evidence=[]
 for row in results:
  for card in row['actual_response'].get('products',[]):
   pid=card.get('product_id') or card.get('productId');res=c.get(f'/api/v1/products/{pid}')
   evidence.append({'case':row['id'],'card':card,'backend_detail_http_status':res.status_code,'backend_detail':safe(res.json())})
 dump('catalog-card-verification.json',evidence)
assert all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==h for p,h in hashes.items()),'Application files changed during diagnostic'
dump('source-unchanged.json',{'verified':True,'files':files})
print('Evidence saved. No application logic or existing session changed.')
