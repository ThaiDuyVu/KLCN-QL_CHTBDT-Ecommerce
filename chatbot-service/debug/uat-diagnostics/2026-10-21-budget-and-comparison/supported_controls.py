"""Disentangle unavailable M1 from reference-resolution failure using catalog-present M2."""
from pathlib import Path
import sys,re,json,uuid,time,hashlib
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[4];sys.path.insert(0,str(ROOT/'chatbot-service'))
import httpx
from app.shared.contracts.context import QueryContext
from app.shared.contracts.query import QueryPlan,ProductSearchPlan,ProductSelector
from app.shared.contracts.enums import Intent
from app.features.query_understanding.normalization import DomainNormalizer,folded,capacity
from app.features.query_understanding.constraints import detect_reference
from app.features.conversation.references import resolve_reference
from app.features.conversation.postgres_repository import PostgresConversationRepository
from app.shared.config import Settings
OUT=Path(__file__).resolve().parent
def dump(name,data):(OUT/name).write_text(json.dumps(data,ensure_ascii=False,indent=2,default=str)+'\n')
def safe(v):
 if isinstance(v,list):return [safe(x) for x in v]
 if isinstance(v,dict):return {k:safe(x) for k,x in v.items() if k not in ['customer','customer_id','customerId','user_id','userId','session_id','sessionId','display_name','messages','cookies','authorization']}
 return v
pw=re.search(r'`(\d+)`',(ROOT/'chatbot-service/docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md').read_text()).group(1)
with httpx.Client(base_url='http://localhost:5173',timeout=180) as c:
 c.get('/api/auth/csrf');c.headers['X-XSRF-TOKEN']=c.cookies.get('XSRF-TOKEN');c.post('/api/auth/login',json={'username':'seed.customer1','password':pw}).raise_for_status();me=c.get('/api/auth/me').json()
 raw=c.get('/api/v1/products',params={'page':0,'size':100}).json();products=raw['content'];assert len(products)==raw['totalElements'] and raw['totalPages']==1
 targets=[x for x in products if 'macbook' in x['productName'].casefold() or x['productName']=='Dell Inspiron 15']
 original=[]
 for line in (ROOT/'.local/logs/chatbot-traces.jsonl').open():
  t=json.loads(line)
  if t['trace_id']=='94e19c12-5e80-4dab-a874-89fddb77e252':
   for e in t['events']:
    if e['name']=='backend.product_result' and (e.get('output') or {}).get('path')=='/api/v1/products':
     body=e['output']['body'];original=body.get('content',[]) if isinstance(body,dict) else body
 dump('catalog-evidence.json',{'checked_at_utc':datetime.now(timezone.utc).isoformat(),'current_total':raw['totalElements'],'current_total_pages':raw['totalPages'],'current_relevant_products':targets,'original_comparison_trace_id':'94e19c12-5e80-4dab-a874-89fddb77e252','original_catalog_names':[x['productName'] for x in original],'m1_present_in_original':any('air m1' in x['productName'].casefold() for x in original),'m1_present_now':any('air m1' in x['productName'].casefold() for x in products)})
 categories=c.get('/api/v1/categories').json();brands=c.get('/api/v1/brands',params={'page':0,'size':100}).json()['content']
 class CapturedCatalog:
  def taxonomy(self):return [x['categoryName'] for x in categories],[x['brandName'] for x in brands]
  def catalog_names(self):return products
 normalizer=DomainNormalizer(CapturedCatalog());repo=PostgresConversationRepository(Settings(_env_file=ROOT/'chatbot-service/.env').conversation_database_url);snapshot=repo.get_snapshot(uuid.UUID('bb0c432a-8efa-4b97-9224-1e5ae8fb67f3'),uuid.UUID(me['customerId']))
 model_plan=QueryPlan(intent=Intent.PRODUCT_COMPARE,product_search=ProductSearchPlan(targets=[ProductSelector(sku='DEV-DELL-I15-512'),ProductSelector(product_name='MacBook Air M2')]))
 probes=[]
 for prompt in ['so sánh sản phẩm trên với MacBook Air M2','so sánh máy vừa gợi ý với MacBook Air M2','So sánh Dell Inspiron 15 với MacBook Air M2.']:
  context=QueryContext(customer={'user_id':me['userId'],'customer_id':me['customerId'],'display_name':'Synthetic'},session_id=uuid.uuid4(),current_message=prompt,recent_messages=[])
  norm=normalizer.normalize(model_plan,context);resolved=resolve_reference(prompt,norm,snapshot);kind=detect_reference(folded(prompt))
  probes.append({'prompt':prompt,'controlled_input_plan':model_plan.model_dump(mode='json'),'reference_kind':kind.value if kind else None,'normalized':norm.model_dump(mode='json'),'after_resolver':resolved.model_dump(mode='json')})
 dump('supported-normalizer-replays.json',{'execution':'CONTROLLED_PLAN_WITH_REAL_CAPTURED_CATALOG_AND_OWNED_SNAPSHOT_NOT_MODEL_E2E','probes':probes,'ram_notation_probes':{text:capacity(folded(text),'ram') for text in ['16G ram','RAM 16GB','16GB RAM']}})
 plan=[{'id':'C-setup','session':'C','prompt':'Gợi ý cho tôi một laptop Dell RAM 16GB dưới 20 triệu.','expected':'One Dell card as unambiguous antecedent.'},{'id':'C-ref','session':'C','prompt':'so sánh sản phẩm trên với MacBook Air M2','expected':'Two grounded targets: previous single Dell + catalog-present MacBook Air M2.'},{'id':'C-last','session':'C','prompt':'so sánh máy vừa gợi ý với MacBook Air M2','expected':'Supported LAST phrase must allow compare, or clarify only truly missing information.'},{'id':'C-explicit','session':'D','prompt':'So sánh Dell Inspiron 15 với MacBook Air M2.','expected':'PRODUCT_COMPARE, two catalog-present names, two cards.'}]
 dump('supported-control-plan.json',plan);sessions={};result=[]
 for case in plan:
  sid=sessions.setdefault(case['session'],str(uuid.uuid4()));tid=str(uuid.uuid4());start=time.monotonic();r=c.post(f'/api/v1/chat/sessions/{sid}/messages',headers={'X-Chat-Trace-Id':tid},json={'message':case['prompt']});tr=c.get(f'/api/v1/chat/sessions/{sid}/traces/{tid}');trace=tr.json() if tr.status_code==200 else {};events=trace.get('events',[])
  out=lambda n:next((e.get('output') for e in events if e['name']==n),None)
  row={**case,'trace_id':tid,'http_status':r.status_code,'duration_ms':round((time.monotonic()-start)*1000,2),'response':safe(r.json()),'validated_model_plan':out('query.validation'),'domain_validation':out('query.domain_validation'),'normalized':out('query.plan.initial'),'effective':out('query.plan.effective'),'history_count':len((out('conversation.context') or {}).get('recent_messages',[])),'reference_resolution':out('conversation.reference_resolution'),'advisor_search_called':any(e['name']=='product.search' for e in events)}
  result.append(row);dump('supported-live-controls.json',result);print(case['id'],r.status_code,(row['normalized'] or {}).get('intent'),len(r.json().get('products',[])),flush=True)
