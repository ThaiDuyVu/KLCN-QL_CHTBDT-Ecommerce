import asyncio
import json
from uuid import uuid4

import httpx
import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.shared.tracing import TraceStore, capture, event, traced, sanitize, enabled, store
from app.shared.config import Settings, get_settings
from app.features.query_understanding.ollama_service import OllamaQueryUnderstandingService
from app.shared.contracts.context import QueryContext
from app.features.chat.router import get_authenticated_customer
from app.features.customer_context.models import AuthenticatedCustomer
from app.main import app


def test_nested_threads_concurrent_traces_and_redaction():
    local = TraceStore()
    @traced('work', ('message',))
    def work(message, cookies):
        event('child', {'message': message, 'password': 'not-recorded'})
        return message
    async def run(message):
        trace = local.create(uuid4(), uuid4(), uuid4())
        with capture(trace):
            await asyncio.to_thread(work, message, {'session': 'secret-cookie'})
        return trace.snapshot()
    async def both():
        return await asyncio.gather(run('first'), run('second'))
    a, b = asyncio.run(both())
    assert a['events'][1]['parent_id'] == a['events'][0]['id']
    assert a['events'][0]['input'] == {'message': 'first'}
    assert 'second' not in json.dumps(a)
    assert 'first' not in json.dumps(b)
    assert 'not-recorded' not in json.dumps(a)
    assert 'secret-cookie' not in json.dumps(a)
    assert a['status'] == 'ok'


def test_access_expiry_bounds_and_duplicate_ids():
    local = TraceStore(capacity=1, ttl=10)
    owner, session, id1 = uuid4(), uuid4(), uuid4()
    t = local.create(id1, session, owner)
    with pytest.raises(HTTPException) as denied:
        local.get(id1, session, uuid4())
    assert denied.value.status_code == 404
    with pytest.raises(HTTPException):
        local.create(id1, session, owner)
    with capture(t):
        event('large', 'x' * 70000)
        for i in range(300):
            event('item', i)
    assert t.snapshot()['events'][0]['output']['truncated']
    assert t.snapshot()['dropped_events'] > 0
    local.create(uuid4(), session, owner)
    with pytest.raises(HTTPException):
        local.get(id1, session, owner)
    trace = next(iter(local.items.values()))
    trace.started -= 11
    assert local.list(session, owner) == []


def test_json_prompt_redaction_does_not_mutate_request():
    payload = {'messages': [{'role': 'user', 'content': json.dumps({'email': 'secret@example.com', 'question': 'hello'})}], 'Authorization': 'secret'}
    result = sanitize(payload)
    assert 'secret@example.com' not in json.dumps(result)
    assert 'hello' in json.dumps(result)
    assert 'secret@example.com' in payload['messages'][0]['content']
    assert not enabled(Settings(_env_file=None, chatbot_trace_enabled=True, app_env='production'))


def test_repair_attempts_raw_output_and_fallback_visible():
    class Model:
        def chat(self, messages, schema=None):
            return '{"intent": "INVALID"}'
    context = QueryContext(customer={'user_id': uuid4(), 'customer_id': uuid4(), 'display_name': 'Test'}, session_id=uuid4(), current_message='hello')
    t = TraceStore().create(uuid4(), context.session_id, uuid4())
    with capture(t):
        plan = OllamaQueryUnderstandingService(Model()).understand(context)
    events = t.snapshot()['events']
    attempts = [e for e in events if e['name'] == 'query.model_attempt']
    assert len(attempts) == 2
    assert len(attempts[0]['input']['messages']) == 2
    assert len(attempts[1]['input']['messages']) == 3
    assert attempts[0]['output']['raw_output'] == '{"intent": "INVALID"}'
    assert plan.intent.value == 'UNKNOWN'
    assert t.snapshot()['status'] == 'warning'
    assert any(e['name'] == 'query.validation_failed' for e in events)


def test_failure_trace_is_retrievable_only_by_owner(monkeypatch):
    monkeypatch.setenv('CHATBOT_TRACE_ENABLED', 'true')
    monkeypatch.setenv('CHATBOT_TRACE_FILE_ENABLED', 'false')
    get_settings.cache_clear()
    customer = AuthenticatedCustomer(user_id=uuid4(), customer_id=uuid4(), display_name='Test')
    other = AuthenticatedCustomer(user_id=uuid4(), customer_id=uuid4(), display_name='Other')
    class Failing:
        @traced('failing.step')
        async def send_message_async(self, **kwargs):
            raise HTTPException(503, 'Unavailable')
    previous = app.state.chat_service
    app.state.chat_service = Failing()
    app.dependency_overrides[get_authenticated_customer] = lambda: customer
    session, trace_id = uuid4(), uuid4()
    path = f'/api/v1/chat/sessions/{session}'
    try:
        client = TestClient(app)
        response = client.post(path+'/messages', json={'message': 'hello'}, headers={'X-Chat-Trace-Id': str(trace_id)})
        assert response.status_code == 503
        assert response.headers['X-Chat-Trace-Id'] == str(trace_id)
        response = client.get(path+f'/traces/{trace_id}')
        assert response.status_code == 200
        assert response.headers['cache-control'] == 'no-store'
        assert response.json()['status'] == 'error'
        assert response.json()['events'][-1]['output']['http_status'] == 503
        app.dependency_overrides[get_authenticated_customer] = lambda: other
        assert client.get(path+f'/traces/{trace_id}').status_code == 404
        assert client.get(path+'/traces').json() == []
        monkeypatch.setenv('APP_ENV', 'production')
        get_settings.cache_clear()
        assert client.get(path+'/traces').status_code == 404
    finally:
        app.state.chat_service = previous
        app.dependency_overrides.pop(get_authenticated_customer, None)


def test_ollama_transport_error_and_raw_http_payload():
    from app.shared.ollama.client import OllamaClient, OllamaError
    def respond(request):
        return httpx.Response(200, json={'message': {'content': ''}, 'done_reason': 'length', 'eval_count': 512})
    client = OllamaClient(Settings(_env_file=None), client=httpx.Client(transport=httpx.MockTransport(respond)))
    t = TraceStore().create(uuid4(), uuid4(), uuid4())
    with capture(t):
        try:
            client.chat([{'role': 'user', 'content': 'hello'}])
        except OllamaError:
            event('fallback', status='warning')
    data = t.snapshot()
    assert data['status'] == 'warning'
    assert any(e['name'] == 'ollama.result' and e['output']['done_reason'] == 'length' for e in data['events'])
    assert any(e['name'] == 'ollama.chat' and e['status'] == 'error' for e in data['events'])
