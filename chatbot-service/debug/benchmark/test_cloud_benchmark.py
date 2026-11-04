"""Offline checks only; never call Gemini or the local business APIs."""
import sys
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock

import httpx
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from run_cloud_benchmark import Guard, CloudClient, SafetyStop, stop_reason, report


def state(**overrides):
    return {'charged_calls': 0, 'observed_calls': 0, 'tokens': 0, 'inflight': None, **overrides}


def test_budget_reserves_worst_case_and_blocks_next_turn():
    s = state()
    g = Guard(s, lambda: None, 65, 3, 100)
    g.before(); g.reserve('trace', 'session')
    assert s['charged_calls'] == 3
    g.settle([{'name': 'gemini.chat'}, {'name': 'gemini.usage', 'output': {'totalTokenCount': 20}}])
    assert s['charged_calls'] == s['observed_calls'] == 1
    assert s['tokens'] == 20
    s['inflight'] = None
    with pytest.raises(SafetyStop, match='MODEL_CALL_BUDGET'):
        g.before()


def test_waits_after_previous_response_including_resumed_run():
    now = [100.0]; sleeps = []
    def sleep(seconds):
        sleeps.append(seconds); now[0] += seconds
    g = Guard(state(last_finished_at=100), lambda: None, 65, 50, 100,
              clock=lambda: now[0], sleep=sleep)
    g.before()
    assert sum(sleeps) == 65
    assert max(sleeps) <= 30


def test_ambiguous_post_and_token_budget_block_before_network():
    for s, reason in [(state(inflight={'trace_id':'unknown'}), 'AMBIGUOUS'),
                      (state(tokens=100), 'TOKEN_BUDGET')]:
        with pytest.raises(SafetyStop, match=reason):
            Guard(s, lambda: None, 65, 50, 100).before()


@pytest.mark.parametrize('code', [429, 500, 503])
def test_upstream_error_circuit_breaker(code):
    t = {'events':[{'name':'llm.provider_selected','output':{'provider':'gemini'}},
                   {'name':'gemini.http_status','output':{'status_code':code}}]}
    assert stop_reason(200,t) == 'CLOUD_HTTP_ERROR: dừng ngay, gồm 429/5xx.'


def test_missing_trace_and_local_fallback_fail_closed():
    assert 'MISSING_TRACE' in stop_reason(200,{})
    assert 'PROVIDER_MISMATCH' in stop_reason(200,{'events':[{'name':'llm.provider_selected','output':{'provider':'ollama'}}]})
    assert 'LOCAL_CHAT' in stop_reason(200,{'events':[{'name':'llm.provider_selected','output':{'provider':'gemini'}},{'name':'ollama.chat'}]})


def test_cloud_post_injects_provider_and_never_retries_on_429():
    inner = Mock()
    inner.post.return_value = httpx.Response(503)
    inner.get.return_value = httpx.Response(200,json={'events':[
        {'name':'llm.provider_selected','output':{'provider':'gemini'}},
        {'name':'gemini.chat','status':'error'},
        {'name':'gemini.http_status','output':{'status_code':429}}]})
    s = state(); proxy = CloudClient(inner,Guard(s,lambda:None,65,50,100))
    proxy.post('/api/v1/chat/sessions/s/messages',json={'message':'test'},headers={'X-Chat-Trace-Id':'t'})
    assert inner.post.call_count == 1
    assert inner.post.call_args.kwargs['json']['provider'] == 'gemini'
    assert proxy.last['stop_reason'].startswith('HTTP_503')
    assert s['inflight']  # only persisted row may clear this


def test_transport_failure_keeps_reservation_and_never_retries():
    inner = Mock(); inner.post.side_effect = httpx.ReadTimeout('private request content')
    s=state(); proxy=CloudClient(inner,Guard(s,lambda:None,65,50,100))
    with pytest.raises(SafetyStop,match='POST_TRANSPORT_ERROR') as error:
        proxy.post('/api/v1/chat/sessions/s/messages',json={'message':'test'},headers={'X-Chat-Trace-Id':'t'})
    assert inner.post.call_count == 1 and s['charged_calls'] == 3
    assert s['inflight'] and 'private' not in str(error.value)


def test_partial_report_never_claims_full_score(tmp_path):
    s=state(results=[],planned_cases=205,planned_turns=290,model='test',started_at='now',commit='abc')
    report(s,tmp_path/'REPORT.md')
    text=(tmp_path/'REPORT.md').read_text()
    assert 'INCOMPLETE' in text and 'NOT_RUN' in text
    assert '/ 100 =' not in text


def remote_error(code=503):
    return {'trace_id':'trace', 'http_status':503, 'events':[
        {'name':'llm.provider_selected','output':{'provider':'gemini'}},
        {'name':'gemini.http_status','output':{'status_code':code}}]}


def test_only_verified_upstream_503_is_retryable():
    from run_cloud_benchmark import retryable_503
    assert retryable_503(remote_error())
    assert not retryable_503(remote_error(429))
    assert not retryable_503({'http_status':503,'events':[]})
    assert not retryable_503({**remote_error(), 'local_chat_detected':True})


def test_backoff_archives_all_turns_and_is_bounded(tmp_path):
    from run_cloud_benchmark import schedule_case_retry
    s=state(results=[])
    original={'id':'CASE','session_id':'old','turns':[{'turn':1,'verdict':'PASS'},{'turn':2,'verdict':'BLOCKED_ENVIRONMENT'}]}
    delays=[]
    for n in range(5):
        record={**original};s['results'].append(record)
        assert schedule_case_retry(s,record,remote_error(),tmp_path,5,now=lambda:100,jitter=0)
        assert not s['results']
        delays.append(s['pending_retry']['not_before']-100)
    assert delays==[30,60,120,240,300]
    assert len(list((tmp_path/'attempts').glob('*.json')))==5
    assert 'PASS' in (tmp_path/'attempts/CASE-failed-01.json').read_text()
    s['results'].append(original)
    assert not schedule_case_retry(s,original,remote_error(),tmp_path,5)


def test_retry_deadline_is_honored_before_sending():
    now=[100.0]
    g=Guard(state(pending_retry={'not_before':220}),lambda:None,20,50,100,
            clock=lambda:now[0],sleep=lambda seconds:now.__setitem__(0,now[0]+seconds))
    g.before()
    assert now[0]==220


@pytest.mark.parametrize('retry_limit,expected_posts,exit_code', [(0,2,2),(1,4,0)])
def test_multiturn_503_restarts_case_in_fresh_session_without_duplicate_history(tmp_path,monkeypatch,retry_limit,expected_posts,exit_code):
    import json
    import run_cloud_benchmark as mod
    now=[100.0]
    original_init=mod.Guard.__init__
    def init(self,*args,**kwargs):
        original_init(self,*args,clock=lambda:now[0],sleep=lambda sec:now.__setitem__(0,now[0]+sec),**kwargs)
    monkeypatch.setattr(mod.Guard,'__init__',init)
    # schedule uses its injectable clock; align it with the guard in this offline run.
    schedule=mod.schedule_case_retry
    monkeypatch.setattr(mod,'schedule_case_retry',lambda *a,**kw:schedule(*a,**kw,now=lambda:now[0],jitter=0))
    monkeypatch.setattr(mod,'HERE',tmp_path)
    monkeypatch.setattr(mod,'source_digest',lambda:'test-source')
    client=Mock();posts=[];traces={}
    def post(path,*,json,headers):
        posts.append((path,json))
        status=503 if len(posts)==2 else 200
        tid=headers['X-Chat-Trace-Id']
        traces[tid]={'events':[
            {'name':'llm.provider_selected','output':{'provider':'gemini'}},
            {'name':'gemini.chat','status':'error' if status==503 else 'ok'},
            {'name':'gemini.http_status','output':{'status_code':status}}]}
        return httpx.Response(status,json={'message':'answer','products':[],'sources':[]})
    def get(path):
        if path.endswith('/status'):
            return httpx.Response(200,request=httpx.Request('GET','http://test/status'),json={
                'providers':{'gemini':{'enabled':True,'model':'test'}},'ai_enabled':True,'trace_enabled':True,'product_mode':'real'})
        return httpx.Response(200,json=traces[path.rsplit('/',1)[-1]])
    client.post.side_effect=post;client.get.side_effect=get
    monkeypatch.setattr(mod.baseline,'login',lambda:client)
    monkeypatch.setattr(mod.baseline,'load_catalog',lambda:[])
    monkeypatch.setattr(mod.baseline,'personal_snapshot',lambda _: {})
    monkeypatch.setattr(mod.baseline,'write_fingerprint',lambda _: {})
    monkeypatch.setattr(mod.baseline.subprocess,'check_output',lambda *a,**kw:'abc\n')
    grader=Mock();grader.grade.return_value=('PASS',[],[])
    monkeypatch.setattr(mod.baseline,'Grader',lambda *a:grader)
    args=SimpleNamespace(resume=None,interval=20,max_model_calls=50,max_tokens=100000,max_503_retries=retry_limit)
    cases=[({'group':'G1','title':'test'},{'id':'C1','title':'multi','difficulty':'D','probe':False,
        'turns':[{'say':'first','expect':{}},{'say':'followup','expect':{}}]})]
    assert mod.execute(args,cases)==exit_code
    assert len(posts)==expected_posts
    assert posts[0][0]==posts[1][0]
    if retry_limit:
        assert posts[2][0]==posts[3][0] and posts[0][0]!=posts[2][0]
        assert [p[1]['message'] for p in posts]==['first','followup','first','followup']
        result=json.loads(next(tmp_path.glob('runs/*/results.json')).read_text())
        assert len(result['results'][0]['turns'])==2
        assert all(t['verdict']=='PASS' for t in result['results'][0]['turns'])
        assert result['observed_calls']==4 and result['inflight'] is None
        assert len(list(tmp_path.glob('runs/*/evidence/*.json')))==4
