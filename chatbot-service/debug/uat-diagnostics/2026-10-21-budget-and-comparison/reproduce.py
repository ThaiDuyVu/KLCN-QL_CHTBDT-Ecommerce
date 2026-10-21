"""Read original traces; reproduce in NEW synthetic sessions. No chatbot logic changes."""
from pathlib import Path
import sys,json,re,hashlib,subprocess,time,uuid
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[4];sys.path.insert(0,str(ROOT/'chatbot-service'))
import httpx
from app.features.query_understanding.normalization import DomainNormalizer,folded
from app.features.query_understanding.constraints import price_evidence,detect_reference
from app.features.conversation.references import resolve_reference
from app.features.conversation.postgres_repository import PostgresConversationRepository
from app.shared.config import Settings
from app.shared.contracts.context import QueryContext
from app.shared.contracts.query import QueryPlan,ProductSearchPlan,ProductSelector
from app.shared.contracts.enums import Intent
OUT=Path(__file__).resolve().parent;PRIVATE=ROOT/'.local/uat-screenshot-debug-20261021';PRIVATE.mkdir(mode=0o700,parents=True,exist_ok=True)
SESSION='bb0c432a-8efa-4b97-9224-1e5ae8fb67f3'
KEEP={'query.validation','query.domain_validation','query.plan.initial','query.plan.effective','conversation.reference_resolution','product.search','product.identity_resolution','product.cards','retrieval.summary','response.strategy'}
def dump(name,value):
 p=OUT/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2,default=str)+'\n')
def safe(value):
 if isinstance(value,list):return [safe(v) for v in value]
 if isinstance(value,dict):return {k:safe(v) for k,v in value.items() if k not in {'user_id','userId','customer_id','customerId','customer','session_id','sessionId','history_id','historyId','cookies','authorization','password','messages','recipientName','recipientPhone','shippingAddress','display_name'}}
 return value

def project_trace(t):
 events=t.get('events',[])
 def out(name):return next((e.get('output') for e in events if e['name']==name),None)
 ctx=out('conversation.context') or {};ground=out('response.grounded_context') or {};reply=out('chat.pipeline') or {}
 selected=[]
 for e in events:
  if e['name'] in KEEP:selected.append(safe({k:v for k,v in e.items() if k not in ['input','error']}))
  if e['name']=='query.model_attempt':selected.append({'name':e['name'],'offset_ms':e['offset_ms'],'duration_ms':e['duration_ms'],'raw_model_output':(e.get('output') or {}).get('raw_output')})
 return {'trace_id':t['trace_id'],'timestamp_utc':t.get('started_at'),'duration_ms':t.get('duration_ms'),'status':t.get('status'),'prompt':(out('request.accepted') or {}).get('message'),'history_count':len(ctx.get('recent_messages',[])),'history_excerpt':safe(ctx.get('recent_messages',[])[-4:]),'validated_model_plan':out('query.validation'),'normalized_plan':out('query.plan.initial'),'effective_search':out('query.plan.effective'),'grounded_product_count':len(ground.get('products') or []),'advisor_search_called':any(e['name']=='product.search' for e in events),'semantic_ranking_called':any(e['name']=='product.vector_ranking' for e in events),'backend_reads':[safe(e.get('input')) for e in events if e['name']=='backend.product_http'],'response':safe(reply),'events':selected}
paths=[p for p in (ROOT/'.local/logs').glob('chatbot-traces.jsonl*') if p.is_file()];original=[]
for p in paths:
 for line in p.open():
  try:t=json.loads(line)
  except ValueError:continue
  if t.get('session_id')==SESSION:original.append(project_trace(t))
original.sort(key=lambda t:t['timestamp_utc']);dump('original-session-excerpts.json',original)
files=['chatbot-service/app/features/query_understanding/normalization.py','chatbot-service/app/features/query_understanding/constraints.py','chatbot-service/app/features/query_understanding/ollama_service.py','chatbot-service/app/features/conversation/references.py','chatbot-service/app/features/chat/service.py','chatbot-service/app/features/response_generation/ollama_service.py','chatbot-service/app/features/customer_context/service.py']
hashes={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in files}
dump('source.json',{'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'status_before':subprocess.check_output(['git','status','--short'],cwd=ROOT,text=True).splitlines(),'file_hashes':hashes,'note':'Existing cart/customer changes are preserved; original traces date to screenshot session. Current source hash verifies controlled reproduction version.'})
password=re.search(r'`(\d+)`',(ROOT/'chatbot-service/docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md').read_text()).group(1)
client=httpx.Client(base_url='http://localhost:5173',timeout=180);client.get('/api/auth/csrf').raise_for_status();client.headers['X-XSRF-TOKEN']=client.cookies.get('XSRF-TOKEN');client.post('/api/auth/login',json={'username':'seed.customer1','password':password}).raise_for_status();me=client.get('/api/auth/me').json();assert me['roleName']=='CUSTOMER'
# Read only the owned screenshot session and inspect persisted ASSISTANT evidence.
repo=PostgresConversationRepository(Settings(_env_file=ROOT/'chatbot-service/.env').conversation_database_url)
try:
 snapshot=repo.get_snapshot(uuid.UUID(SESSION),uuid.UUID(me['customerId']))
 before_compare=[]
 with repo.connect() as conn:
  rows=conn.execute("SELECT created_at,message FROM chat_history WHERE session_id=%s AND customer_id=%s AND sender_type='ASSISTANT' AND created_at < %s ORDER BY created_at DESC,history_id DESC LIMIT 10",(SESSION,me['customerId'],'2026-10-21T06:23:49.953741+00:00')).fetchall()
 for timestamp,text in rows:
  _,s=repo.decode('ASSISTANT',text)
  if s and s.cards:
   before_compare.append({'persisted_at':timestamp,'snapshot':safe(s.model_dump(mode='json'))});break
 dump('owned-snapshot.json',{'owner_checked':True,'before_original_comparison':before_compare,'current_latest_snapshot':safe(snapshot.model_dump(mode='json')) if snapshot else None})
except Exception as exc:
 snapshot=None;dump('owned-snapshot.json',{'status':'UNAVAILABLE','error_type':type(exc).__name__})
# Existing owned business facts/catalog are read-only.
categories=client.get('/api/v1/categories');categories.raise_for_status();brands=client.get('/api/v1/brands',params={'page':0,'size':100});brands.raise_for_status();products=client.get('/api/v1/products',params={'page':0,'size':100});products.raise_for_status()
cat=categories.json();cat=cat if isinstance(cat,list) else cat['content'];br=brands.json();br=br if isinstance(br,list) else br['content'];prod=products.json()['content']
class CapturedCatalog:
 def taxonomy(self):return ([x['categoryName'] for x in cat if x.get('status')=='ACTIVE'],[x['brandName'] for x in br if x.get('status')=='ACTIVE'])
 def catalog_names(self):return prod
normalizer=DomainNormalizer(CapturedCatalog());ctx=lambda text:QueryContext(customer={'user_id':me['userId'],'customer_id':me['customerId'],'display_name':'Synthetic'},session_id=uuid.uuid4(),current_message=text,recent_messages=[])
probes=[]
for text in ['tôi đang có 20tr tư vấn giúp tôi một laptop học công nghệ thông tin','20tr , hoc cong nghe thong tin','Tìm laptop dưới 20 triệu để học công nghệ thông tin.','ngân sách 20 triệu','tầm 20 triệu']:
 ev=price_evidence(folded(text));probes.append({'prompt':text,'min_price':ev.min_price,'max_price':ev.max_price,'unresolved':[{'relation':r.value,'span':list(span)} for r,span in ev.unresolved]})
comparison='so sánh sản phẩm trên với macbook air m1';explicit='So sánh Dell Inspiron 15 với MacBook Air M1.'
plan=QueryPlan(intent=Intent.PRODUCT_COMPARE,product_search=ProductSearchPlan(targets=[ProductSelector(sku='DEV-DELL-I15-512'),ProductSelector(product_name='MacBook Air M1')]))
replays=[]
for text in [comparison,'so sánh máy vừa gợi ý với MacBook Air M1',explicit]:
 normalized=normalizer.normalize(plan,ctx(text));kind=detect_reference(folded(text));resolved=resolve_reference(text,normalized,snapshot)
 replays.append({'prompt':text,'model_plan_for_control':plan.model_dump(mode='json'),'detected_reference':kind.value if kind else None,'normalized':normalized.model_dump(mode='json'),'after_reference_resolver':resolved.model_dump(mode='json')})
dump('parser-and-normalizer-probes.json',{'execution':'CONTROLLED_REPLAY_WITH_CAPTURED_REAL_CATALOG_NOT_MODEL_E2E','price_probes':probes,'reference_probes':replays,'catalog_targets':[safe(x) for x in prod if x.get('productName') in ['Dell Inspiron 15','MacBook Air M1']]})
# Freeze actual test expectations BEFORE the independent requests.
plan_cases=[
 {'id':'A1','session':'A','prompt':'tôi đang có 20tr tư vấn giúp tôi một laptop học công nghệ thông tin','expected':'Recognize Laptop/20million/use-case; eligible advice or targeted budget confirmation, not repeated all-fields question.'},
 {'id':'A2','session':'A','prompt':'20tr , hoc cong nghe thong tin','expected':'Use completed previous context to clarify only unresolved slot; avoid repeating all-fields question.'},
 {'id':'A-control','session':'C','prompt':'Tìm laptop dưới 20 triệu để học công nghệ thông tin.','expected':'PRODUCT_DISCOVERY Laptop/max_price20000000 and eligible cards.'},
 {'id':'B-setup','session':'B','prompt':'Gợi ý cho tôi một laptop Dell RAM 16GB dưới 20 triệu.','expected':'One eligible Dell card <=20million, to establish exact antecedent.'},
 {'id':'B1','session':'B','prompt':comparison,'expected':'Compare previous Dell card with real MacBook Air M1; no unrelated targets or repeated request for both names when one snapshot card exists.'},
 {'id':'B-control','session':'D','prompt':explicit,'expected':'PRODUCT_COMPARE with exact Dell Inspiron15 and MacBookAirM1 and two product cards.'},
]
dump('reproduction-plan.json',plan_cases);sessions={};results=[]
try:
 for case in plan_cases:
  sid=sessions.setdefault(case['session'],str(uuid.uuid4()));tid=str(uuid.uuid4());start=time.monotonic()
  r=client.post(f'/api/v1/chat/sessions/{sid}/messages',headers={'X-Chat-Trace-Id':tid},json={'message':case['prompt']})
  trace=client.get(f'/api/v1/chat/sessions/{sid}/traces/{tid}');row=project_trace(trace.json()) if trace.status_code==200 else {'trace_id':tid}
  row.update(id=case['id'],expected=case['expected'],http_status=r.status_code,actual_http_response=safe(r.json()),http_duration_ms=round((time.monotonic()-start)*1000,2));results.append(row)
  dump('live-reproduction.json',results);print(case['id'],r.status_code,(row.get('normalized_plan') or {}).get('intent'),len((r.json() or {}).get('products',[])),flush=True)
finally:client.close()
assert all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==h for p,h in hashes.items()),'Source changed during diagnostic'
print('Diagnostic evidence collected; no business/code changes; screenshot session read only.')
