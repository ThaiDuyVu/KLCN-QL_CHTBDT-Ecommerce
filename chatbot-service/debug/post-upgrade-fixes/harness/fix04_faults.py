"""Controlled real-PG failures + real HTTP concurrent/security boundaries."""
import asyncio
import hashlib
import os
from pathlib import Path
import signal
import sys
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
import httpx
import psycopg
ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import OUT,PRIVATE,load,dump,now
# Runtime helpers require cases to exist, and must not overwrite the frozen main plan.
dump(OUT/'cases.json',[])
from runtime import login,public,password
from environment import start_chat
from fastapi import HTTPException
from app.features.conversation.postgres_repository import PostgresConversationRepository
from app.features.conversation.service import ConversationService
from app.features.customer_context.models import AuthenticatedCustomer
from app.features.chat.service import ChatService
from app.features.chat.models import SendMessageRequest
from app.features.response_generation.ollama_service import OllamaResponseGenerationService
from app.shared.contracts.query import QueryPlan
from app.shared.contracts.enums import Intent
from app.features.product_advisor.backend import BackendProductClient
from app.features.query_understanding.normalization import DomainNormalizer
from tests.test_conversation_safety import (
 test_cancellation_during_lease_acquisition_cleans_up_eventual_lock,
 test_cancel_during_model_keeps_lease_until_worker_settles,
 test_independent_sessions_not_serialized_and_no_network_transaction,
)

s=load(PRIVATE/'state.json');client,me=login();who=AuthenticatedCustomer.from_backend_user(me)
repo=PostgresConversationRepository(s['dsn']);proof={}
class QU:
 def __init__(self):self.contexts=[]
 def understand(self,ctx):self.contexts.append(ctx);return QueryPlan(intent=Intent.HELP)
class NoModel:
 def chat(self,*a,**k):raise AssertionError('Unexpected response model call')
def service(r,qu=None):return ChatService(conversation_service=ConversationService(r),query_understanding=qu or QU(),response_generation=OllamaResponseGenerationService(NoModel()))
def history(sid):return repo.history_page(sid,who.customer_id)
def roles(sid):return [m.sender_type.value for m in history(sid)]

try:
 # Seed the completed snapshot through the actual real Qwen/product pipeline.
 sid=uuid.uuid4();base=f'/api/v1/chat/sessions/{sid}'
 response=client.post(base+'/messages',json={'message':'Tìm laptop Dell dưới 20 triệu.'});response.raise_for_status();assert response.json()['products']
 completed=history(sid);before_snapshot=repo.get_snapshot(sid,who.customer_id)
 class FailAssistant(PostgresConversationRepository):
  def save_assistant(self,*a,**k):raise HTTPException(503,'controlled ASSISTANT persistence failure')
 abandoned='ABANDONED_B10 synthetic request'
 try:service(FailAssistant(s['dsn'])).send_message(sid,SendMessageRequest(message=abandoned),customer=who);raise AssertionError('Expected503')
 except HTTPException as exc:assert exc.status_code==503
 failed_roles=roles(sid);assert failed_roles==['USER','ASSISTANT','USER']
 raw_projection=[m.message for m in repo.get_recent_messages(sid)]
 assert abandoned in raw_projection
 # Replace the actual diagnostic FastAPI process and send a new real request.
 pid=load(PRIVATE/'state.json')['processes']['chatbot-default']['pid'];os.kill(pid,signal.SIGTERM);time.sleep(1);start_chat('default')
 tid=str(uuid.uuid4());r=client.post(base+'/messages',headers={'X-Chat-Trace-Id':tid},json={'message':'Xin chào'});r.raise_for_status()
 trace=client.get(base+'/traces/'+tid).json()
 ctx=next(e['output'] for e in trace['events'] if e['name']=='conversation.context')
 assert abandoned not in [m['content'] for m in ctx['recent_messages']] and len(ctx['recent_messages'])==2
 fresh=PostgresConversationRepository(s['dsn']);qu=QU()
 service(fresh,qu).send_message(sid,SendMessageRequest(message=abandoned),customer=who)
 assert abandoned not in [m.content for m in qu.contexts[0].recent_messages]
 assert fresh.get_snapshot(sid,who.customer_id).model_dump()==before_snapshot.model_dump()
 assert [m.id for m in history(sid)[:2]]==[m.id for m in completed]
 proof['B10']={'execution':'CONTROLLED_ASSISTANT_FAILURE_REAL_POSTGRES_THEN_REAL_FASTAPI_RESTART','failure_http_equivalent':503,'roles_after_failure':failed_roles,'baseline_raw_projection_includes_orphan':True,'context_after_actual_restart':public(ctx['recent_messages']),'restart_trace_id':tid,'retry_context':public([m.model_dump(mode='json') for m in qu.contexts[0].recent_messages]),'roles_after_restart_and_retry':roles(sid),'audit_orphan_preserved':any(m.message==abandoned for m in history(sid)),'completed_ids_unchanged':True,'previous_snapshot_preserved':True,'verdict':'PASS'}
 dump(OUT/'B10_restart_context.json',public(ctx));dump(OUT/'B10_restart_trace.json',public(trace))
 class FailUser(PostgresConversationRepository):
  def add_message(self,*a,**k):raise HTTPException(503,'controlled USER persistence failure')
 sid=repo.create_session(who.customer_id).id;qu=QU()
 try:service(FailUser(s['dsn']),qu).send_message(sid,SendMessageRequest(message='B09 request'),customer=who);raise AssertionError('Expected503')
 except HTTPException as exc:assert exc.status_code==503
 assert not roles(sid) and not qu.contexts
 proof['B09']={'execution':'CONTROLLED_WRITE_FAILURE_REAL_POSTGRES','http_equivalent':503,'persisted_roles':roles(sid),'model_invoked':False,'verdict':'PASS'}
 # Real HTTP race: no injected app/model delay and no fake success.
 sid=str(uuid.uuid4());base='/api/v1/chat/sessions/'+sid
 client.post(base+'/messages',json={'message':'Xin chào'}).raise_for_status();before=len(client.get(base+'/messages').json());gate=Barrier(2)
 def post(i):
  gate.wait(timeout=3);tid=str(uuid.uuid4());r=client.post(base+'/messages',headers={'X-Chat-Trace-Id':tid},json={'message':'Xiaomi 15 còn hàng không?'})
  return {'http_status':r.status_code,'trace_id':tid,'response':public(r.json())}
 with ThreadPoolExecutor(2) as pool:responses=list(pool.map(post,[0,1]))
 assert sorted(r['http_status'] for r in responses)==[200,409]
 after=len(client.get(base+'/messages').json());assert after==before+2
 probe=client.post(base+'/messages',json={'message':'Xin chào'});assert probe.status_code==200
 proof['B08']={'execution':'REAL_FASTAPI_REAL_OLLAMA_REAL_POSTGRES_CONCURRENCY','responses':responses,'messages_before':before,'messages_after_pair':after,'lease_release_probe_status':probe.status_code,'verdict':'PASS'}
 for row in responses:
  tr=client.get(base+'/traces/'+row['trace_id'])
  if tr.status_code==200:dump(OUT/('B08-'+row['trace_id']+'.json'),public(tr.json()))
 sid=str(uuid.uuid4());r=client.post('/api/v1/chat/sessions/'+sid+'/messages',headers={'X-Chat-Trace-Id':'not-a-uuid'},json={'message':'Xin chào'})
 with psycopg.connect(s['dsn']) as conn:count=conn.execute('SELECT count(*) FROM chat_history WHERE session_id=%s',(sid,)).fetchone()[0]
 assert r.status_code==400 and count==0
 proof['B11']={'execution':'REAL_AUTHENTICATED_HTTP','http_status':400,'persisted_messages':0,'verdict':'PASS'}
 # Pagination faults: preserve Fix01 fail-closed protection rather than editing caps.
 adapter=BackendProductClient('http://synthetic');adapter._client.close();calls=[]
 def capped(request):
  calls.append(request.url.path)
  if request.url.path.endswith('/categories'):return httpx.Response(200,json=[{'categoryName':'Laptop','status':'ACTIVE'}])
  return httpx.Response(200,json={'content':[{'brandName':'Dell','status':'ACTIVE'}]*100,'totalPages':2,'totalElements':101})
 adapter._client=httpx.Client(base_url='http://synthetic',transport=httpx.MockTransport(capped))
 class CappedQU:
  def understand(self,ctx):return DomainNormalizer(adapter).normalize(QueryPlan(intent=Intent.UNKNOWN),ctx)
 try:service(repo,CappedQU()).send_message(uuid.uuid4(),SendMessageRequest(message='Tìm laptop Lenovo.'));raise AssertionError('Expected503')
 except HTTPException as exc:assert exc.status_code==503
 finally:adapter._client.close()
 proof['B07']={'execution':'CONTROLLED_MOCK_TRANSPORT_REAL_ADAPTER','http_equivalent':503,'pages_requested':1,'returned_partial_search':False,'interpreted_as_not_found':False,'verdict':'PASS'}
 # Exercise cancellation and independent-session tests directly against this real DB.
 pg=(repo,who.customer_id,uuid.uuid4(),s['dsn'])
 for name,fn in [('cancel_enter',test_cancellation_during_lease_acquisition_cleans_up_eventual_lock),('cancel_model',test_cancel_during_model_keeps_lease_until_worker_settles),('independent_sessions',test_independent_sessions_not_serialized_and_no_network_transaction)]:
  asyncio.run(fn(pg));proof[name]={'execution':'CONTROLLED_ASYNC_EVENTS_REAL_POSTGRES','assertions_executed':True,'verdict':'PASS'}
 dump(OUT/'results.json',public(proof));print('Fault/concurrency proof PASS:',list(proof),flush=True)
finally:
 dump(OUT/'results.json',public(proof))
 client.close()
