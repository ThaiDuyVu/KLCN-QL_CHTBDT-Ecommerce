"""Focused adapter/routing checks, with no real Gemini calls or business writes."""
import asyncio
from uuid import uuid4
from unittest.mock import Mock

import httpx
import pytest
from fastapi import HTTPException
from app.shared.config import Settings
from app.shared.gemini.client import GeminiClient, GeminiError
from app.shared.llm import SelectableChatModel
from app.shared.tracing import Trace, capture


def settings(**kwargs):
    return Settings(_env_file=None, chatbot_ai_enabled=True, gemini_api_key='synthetic-secret', **kwargs)


def test_gemini_message_schema_and_header_translation():
    def handle(request):
        import json
        assert request.headers['x-goog-api-key'] == 'synthetic-secret'
        assert 'key=' not in str(request.url)
        data = json.loads(request.content)
        assert data['systemInstruction']['parts'] == [{'text': 'system rules'}]
        assert [r['role'] for r in data['contents']] == ['user', 'model', 'user']
        assert data['generationConfig']['responseJsonSchema'] == {'type': 'object'}
        return httpx.Response(200, json={'candidates': [{'finishReason': 'STOP', 'content': {'parts': [{'text': '{"ok":true}'}]}}]})
    with httpx.Client(transport=httpx.MockTransport(handle)) as transport:
        client = GeminiClient(settings(gemini_enabled=True), client=transport)
        assert client.chat([{'role': role, 'content': content} for role, content in
                            [('system', 'system rules'), ('user', 'first'), ('assistant', 'prior'), ('user', 'next')]],
                           schema={'type': 'object'}) == '{"ok":true}'


def test_disabled_gemini_makes_no_http_request():
    transport = Mock()
    client = GeminiClient(settings(gemini_enabled=False), client=transport)
    with pytest.raises(GeminiError):
        client.chat([{'role': 'user', 'content': 'test'}])
    transport.post.assert_not_called()


def test_upstream_error_body_and_api_key_are_not_in_trace_or_exception():
    with httpx.Client(transport=httpx.MockTransport(lambda _: httpx.Response(429, text='synthetic-secret'))) as transport:
        client = GeminiClient(settings(gemini_enabled=True), client=transport)
        trace = Trace(uuid4(), uuid4(), uuid4())
        with capture(trace):
            with pytest.raises(GeminiError) as error:
                client.chat([{'role': 'user', 'content': 'test'}])
        assert 'synthetic-secret' not in str(error.value)
        assert 'synthetic-secret' not in str(trace.snapshot())


@pytest.mark.parametrize('status,expected', [
    (503, 'tạm thời không khả dụng'),
    (429, 'giới hạn lượt gọi hoặc quota'),
    (403, 'từ chối quyền truy cập'),
    (404, 'Không truy cập được model'),
])
def test_upstream_failures_have_specific_safe_guidance_without_retry(status, expected):
    calls = []
    def handle(request):
        calls.append(request)
        return httpx.Response(status, text='synthetic-secret')
    with httpx.Client(transport=httpx.MockTransport(handle)) as transport:
        client = GeminiClient(settings(gemini_enabled=True), client=transport)
        with pytest.raises(GeminiError) as error:
            client.chat([{'role': 'user', 'content': 'test'}])
        assert expected in str(error.value)
        assert 'synthetic-secret' not in str(error.value)
        assert len(calls) == 1


def test_provider_context_is_isolated_and_default_remains_local():
    local, remote = Mock(), Mock()
    local.chat.return_value = 'local'
    remote.chat.return_value = 'remote'
    router = SelectableChatModel(settings(gemini_enabled=True), local, remote)
    async def run(provider):
        with router.select(provider):
            await asyncio.sleep(0)
            return await asyncio.to_thread(router.chat, [])
    async def both():
        return await asyncio.gather(run('gemini'), run('ollama'))
    assert asyncio.run(both()) == ['remote', 'local']
    assert router.chat([]) == 'local'


def test_disabled_provider_is_rejected_and_embeddings_stay_local():
    local, remote = Mock(), Mock()
    local.embed.return_value = [[1, 2]]
    router = SelectableChatModel(settings(gemini_enabled=True), local, remote)
    with router.select('gemini'):
        assert router.embed(['text']) == [[1, 2]]
    remote.embed.assert_not_called()
    disabled = SelectableChatModel(settings(gemini_enabled=False), local)
    with pytest.raises(HTTPException) as error:
        with disabled.select('gemini'):
            pass
    assert error.value.status_code == 503
    remote.chat.assert_not_called()


def test_secret_not_exposed_and_invalid_default_provider_rejected():
    config = settings(gemini_enabled=True)
    assert 'synthetic-secret' not in repr(config)
    assert 'gemini_api_key' not in config.model_dump()
    with pytest.raises(ValueError):
        settings(gemini_enabled=False, llm_provider='gemini')


@pytest.mark.parametrize('production,expected_status', [(False, 503), (True, 403)])
def test_request_override_is_rejected_before_service_for_disabled_or_production(monkeypatch, production, expected_status):
    from types import SimpleNamespace
    from starlette.requests import Request
    from starlette.responses import Response
    from app.features.chat import router as routes
    from app.features.chat.models import SendMessageRequest
    config = settings(gemini_enabled=False, app_env='production' if production else 'development')
    client = SelectableChatModel(config, Mock())
    request = Request({'type': 'http', 'app': SimpleNamespace(state=SimpleNamespace(llm_client=client)), 'headers': []})
    service = Mock()
    monkeypatch.setattr(routes, 'get_settings', lambda: config)
    with pytest.raises(HTTPException) as error:
        asyncio.run(routes.send_message(uuid4(), SendMessageRequest(message='test', provider='gemini'),
                                        request, Response(), customer=Mock(), service=service))
    assert error.value.status_code == expected_status
    service.send_message_async.assert_not_called()
