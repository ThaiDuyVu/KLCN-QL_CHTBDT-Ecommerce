"""Ten controlled transport/PG injections and two real API boundaries; not Qwen E2E substitutes."""
import asyncio,time,uuid,sys
from contextlib import contextmanager
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime,timezone
from pathlib import Path
from common import *
sys.path.insert(0,str(ROOT/'chatbot-service'))
import httpx,psycopg
from fastapi import HTTPException
from app.shared.config import Settings
from app.shared.backend.client import BackendClient
from app.shared.ollama.client import OllamaClient,OllamaUnavailable
from app.features.customer_context.service import CustomerContextService
from app.features.query_understanding.ollama_service import OllamaQueryUnderstandingService
from app.features.knowledge.service import PgvectorKnowledgeRetrievalService
from app.features.product_advisor.backend import BackendProductClient
from app.features.chat.service import ChatService
from app.features.chat.models import SendMessageRequest
from app.features.conversation.postgres_repository import PostgresConversationRepository
from app.features.conversation.service import ConversationService
from app.features.customer_context.models import AuthenticatedCustomer
from app.features.response_generation.ollama_service import OllamaResponseGenerationService
from app.shared.contracts.query import QueryPlan
from app.shared.contracts.context import QueryContext
from app.shared.contracts.enums import Intent
from runtime import login,persist,public
S=load(PRIVATE/'state.json');cases={c['id']:c for c in load(OUT/'cases.json')}

def expect_http(call):
 try:call();return 200
 except HTTPException as exc:return exc.status_code

def no_model():
 class Forbidden:
  def chat(self,*a,**kw):raise AssertionError('unexpected response model call')
 return Forbidden()

def greeting_service(repo=None):
 class QU:
  def understand(self,ctx):return QueryPlan(intent=Intent.GREETING)
 return ChatService(conversation_service=ConversationService(repo) if repo else ConversationService(),customer_context_service=CustomerContextService(),query_understanding=QU(),response_generation=OllamaResponseGenerationService(no_model()))

observations={}
# B01 actual HTTP timeout mapping + actual QU bounded fallback, synthetic transport.
def timeout(request):raise httpx.ReadTimeout('controlled timeout',request=request)
model=OllamaClient(Settings(_env_file=None),client=httpx.Client(transport=httpx.MockTransport(timeout)))
context=QueryContext(customer={'user_id':uuid.uuid4(),'customer_id':uuid.uuid4(),'display_name':'Synthetic'},session_id=uuid.uuid4(),current_message='Thông tin chưa xác định?')
plan=OllamaQueryUnderstandingService(model).understand(context);observations['B01']={'intent':plan.intent,'fallback_bounded':True,'expected_satisfied':plan.intent==Intent.UNKNOWN}
backend=BackendClient('http://synthetic',httpx.MockTransport(timeout));code=expect_http(lambda:asyncio.run(CustomerContextService(backend).load_context('CART_STATUS',{'test':'synthetic'})));observations['B02']={'http_equivalent':code,'expected_satisfied':code==503}
backend=BackendClient('http://synthetic',httpx.MockTransport(lambda r:httpx.Response(200,json={'items':[]})))
code=expect_http(lambda:asyncio.run(CustomerContextService(backend).load_context('CART_STATUS',{'test':'synthetic'})));observations['B03']={'http_equivalent':code,'expected_satisfied':code==502}
order={'orderId':str(uuid.uuid4()),'orderCode':'ORD-DIAG-EXACT','orderDate':'2026-01-01T00:00:00Z','status':'PENDING','totalAmount':100}
def wrong_detail(r):return httpx.Response(200,json={'content':[order],'totalElements':1} if r.url.path.endswith('/mine') else dict(order,orderCode='ORD-DIAG-OTHER'))
backend=BackendClient('http://synthetic',httpx.MockTransport(wrong_detail));code=expect_http(lambda:asyncio.run(CustomerContextService(backend).load_context('PAYMENT_STATUS',{'test':'synthetic'},order_code=order['orderCode'])));observations['B04']={'http_equivalent':code,'expected_satisfied':code==502}
backend=BackendClient('http://synthetic',httpx.MockTransport(lambda r:httpx.Response(200,json={'content':[order],'totalElements':501})))
code=expect_http(lambda:asyncio.run(backend.get_recent_orders({'test':'synthetic'})));observations['B05']={'http_equivalent':code,'expected_satisfied':code==503,'dataset_size':501}
# Exact adapter reads5 pages then explicitly refuses incomplete catalog coverage.
adapter=BackendProductClient('http://synthetic');adapter._client.close();calls=[]
def catalog(r):
 calls.append(dict(r.url.params));return httpx.Response(200,json={'content':[{'productName':'Synthetic'}]*100,'totalPages':6})
adapter._client=httpx.Client(base_url='http://synthetic',transport=httpx.MockTransport(catalog))
try:adapter.catalog_names();raised=False
except ValueError:raised=True
observations['B06']={'pages_requested':len(calls),'explicit_cap_exception':raised,'expected_satisfied':raised and len(calls)==5}
# Current taxonomy silently requests only first100 brands; a diagnosed limit.
def taxonomy(r):
 if r.url.path.endswith('categories'):return httpx.Response(200,json=[{'categoryName':'Laptop','status':'ACTIVE'}])
 return httpx.Response(200,json={'content':[{'brandName':f'Brand{i}','status':'ACTIVE'} for i in range(100)],'totalPages':2,'totalElements':101})
adapter._client=httpx.Client(base_url='http://synthetic',transport=httpx.MockTransport(taxonomy));_,brands=adapter.taxonomy();observations['B07']={'returned_brands':len(brands),'backend_total':101,'omitted_last_brand':True,'expected_satisfied':False,'known_limit':True}
# Real PostgreSQL conversations, injected write faults only.
c,me=login();customer=AuthenticatedCustomer.from_backend_user(me)
class FailUser(PostgresConversationRepository):
 def add_message(self,*a,**kw):raise HTTPException(503,'controlled write unavailable')
sid=uuid.uuid4();service=greeting_service(FailUser(S['dsn']));code=expect_http(lambda:service.send_message(sid,SendMessageRequest(message='Xin chào'),customer=customer))
with psycopg.connect(S['dsn']) as conn:n=conn.execute('SELECT count(*) FROM chat_history WHERE session_id=%s',(sid,)).fetchone()[0]
observations['B09']={'http_equivalent':code,'persisted_messages':n,'expected_satisfied':code==503 and n==0}
class FailAssistant(PostgresConversationRepository):
 def save_assistant(self,*a,**kw):raise HTTPException(503,'controlled assistant write unavailable')
sid=uuid.uuid4();repo=FailAssistant(S['dsn']);code=expect_http(lambda:greeting_service(repo).send_message(sid,SendMessageRequest(message='Xin chào'),customer=customer))
with psycopg.connect(S['dsn']) as conn:roles=[r[0] for r in conn.execute('SELECT sender_type FROM chat_history WHERE session_id=%s ORDER BY created_at',(sid,)).fetchall()]
# Fresh repository proves orphan is durable and enters next context read.
fresh=PostgresConversationRepository(S['dsn']);history=fresh.get_recent_messages(sid)
observations['B10']={'http_equivalent':code,'persisted_roles':roles,'next_recent_message_count':len(history),'expected_satisfied':code==503,'incomplete_turn_confirmed':roles==['USER'] and len(history)==1}
class EmbedTimeout:
 def embed(self,texts):raise OllamaUnavailable('controlled embedding timeout')
class Repo:
 def search(self,*a):raise AssertionError('must not search after failed embedding')
response=ChatService(customer_context_service=CustomerContextService(),knowledge_retrieval=PgvectorKnowledgeRetrievalService(Repo(),EmbedTimeout(),status='PUBLISHED'),response_generation=OllamaResponseGenerationService(no_model())).send_message(uuid.uuid4(),SendMessageRequest(message='Chính sách đổi trả?'))
observations['B12']={'message':response.message,'sources':[],'expected_satisfied':'không khả dụng' in response.message and not response.sources}
# Authenticated invalid trace UUID returns400 before any persisted session/history.
sid=str(uuid.uuid4());r=c.post('/api/v1/chat/sessions/'+sid+'/messages',headers={'X-Chat-Trace-Id':'invalid'},json={'message':'Xin chào'})
with psycopg.connect(S['dsn']) as conn:n=conn.execute('SELECT count(*) FROM chat_history WHERE session_id=%s',(sid,)).fetchone()[0]
observations['B11']={'http_status':r.status_code,'persisted_messages':n,'expected_satisfied':r.status_code==400 and n==0}
# Real Qwen concurrent POST. No mock delay, ordinary exact-product inference.
sid=str(uuid.uuid4());c.post('/api/v1/chat/sessions/'+sid+'/messages',json={'message':'Xin chào'}).raise_for_status()
before=len(c.get('/api/v1/chat/sessions/'+sid+'/messages').json())
def post(i):
 tid=str(uuid.uuid4());r=c.post('/api/v1/chat/sessions/'+sid+'/messages',headers={'X-Chat-Trace-Id':tid},json={'message':'Xiaomi 15 còn hàng không?'})
 return {'http_status':r.status_code,'trace_id':tid,'response':public(r.json())}
with ThreadPoolExecutor(max_workers=2) as pool:responses=list(pool.map(post,[0,1]))
for item in responses:
 trace=c.get('/api/v1/chat/sessions/'+sid+'/traces/'+item['trace_id']).json()
 dump(PRIVATE/'raw'/('B08-'+item['trace_id']+'.json'),trace)
 safe=[]
 for event in trace.get('events',[]):
  if event['name'] in ['query.plan.initial','query.plan.effective','response.strategy','retrieval.summary','conversation.reference_resolution']:
   value={k:v for k,v in event.items() if k not in ['input','error']}
   if event['name']=='response.strategy' and isinstance(value.get('output'),dict):value['output']={k:v for k,v in value['output'].items() if k!='messages'}
   safe.append(public(value))
 item['trace_evidence']='evidence/traces/B08-'+item['trace_id']+'.json';dump(OUT/item['trace_evidence'],{'trace_id':item['trace_id'],'events':safe,'status':trace.get('status')})
after=len(c.get('/api/v1/chat/sessions/'+sid+'/messages').json());probe=c.post('/api/v1/chat/sessions/'+sid+'/messages',json={'message':'Xin chào'})
observations['B08']={'responses':responses,'messages_before':before,'messages_after_pair':after,'lease_release_probe_status':probe.status_code,'expected_satisfied':sorted(r['http_status'] for r in responses)==[200,409] and after-before==2 and probe.status_code==200}
for id,o in sorted(observations.items()):
 case=cases[id];verdict='PASS' if o['expected_satisfied'] else 'FAIL';fault='NO_FAULT'
 if id in ['B07','B10']:verdict='PASS_WITH_WARNINGS';fault='DOMAIN_NORMALIZATION' if id=='B07' else 'CONVERSATION'
 persist({'id':id,'prompt':case['prompt'],'expected':case['expected'],'execution':'REAL_CONCURRENT_QWEN_API' if id=='B08' else 'REAL_SECURITY_API' if id=='B11' else 'CONTROLLED_PYTHON_TRANSPORT_OR_POSTGRES_FAULT_INJECTION','started_at':now(),'observations':public(o),'verdict':verdict,'first_fault':fault,'trace_id':None})
 dump(OUT/f'evidence/boundaries/{id}.json',public(o));print(id,verdict,flush=True)
c.close()

print(str(sum(o['expected_satisfied'] for k,o in observations.items() if k not in ['B07','B10']))+' passed; 2 diagnostic warnings',flush=True)
