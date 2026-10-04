import json

import httpx
import pytest

from app.shared.config import Settings
from app.shared.ollama.client import (OllamaClient, OllamaInvalidResponse, OllamaModelError, OllamaUnavailable)


def client_for(handler):
    settings = Settings(_env_file=None, ollama_base_url="http://fake:123", ollama_chat_model="test-chat", ollama_embedding_model="test-embed")
    return OllamaClient(settings, client=httpx.Client(transport=httpx.MockTransport(handler)))


def test_chat_sends_configured_model_schema_and_non_streaming():
    def handler(request):
        body = json.loads(request.content)
        assert str(request.url) == "http://fake:123/api/chat"
        assert body["model"] == "test-chat"
        assert body["stream"] is False
        assert body["format"] == {"type": "object"}
        return httpx.Response(200, json={"message": {"content": " hello "}})
    assert client_for(handler).chat([], schema={"type": "object"}) == "hello"


def test_embed_batch_uses_config_and_accepts_runtime_dimension():
    def handler(request):
        assert request.url.path == "/api/embed"
        assert json.loads(request.content) == {"model": "test-embed", "input": ["a", "b"]}
        return httpx.Response(200, json={"embeddings": [[1, 2, 3], [3, 2, 1]]})
    assert len(client_for(handler).embed(["a", "b"])[0]) == 3


@pytest.mark.parametrize("error", [httpx.ReadTimeout, httpx.ConnectError])
@pytest.mark.parametrize("method", ["chat", "embed"])
def test_transport_errors(error, method):
    def handler(request):
        raise error("failed", request=request)
    with pytest.raises(OllamaUnavailable):
        getattr(client_for(handler), method)(["a"] if method == "embed" else [])


@pytest.mark.parametrize("response,expected", [
    (httpx.Response(404, json={"error": "missing model"}), OllamaModelError),
    (httpx.Response(503), OllamaModelError),
    (httpx.Response(200, text="not JSON"), OllamaInvalidResponse),
    (httpx.Response(200, json=[]), OllamaInvalidResponse),
    (httpx.Response(200, json={"error": "model failed"}), OllamaModelError),
    (httpx.Response(200, json={}), OllamaInvalidResponse),
    (httpx.Response(200, json={"message": {"content": " "}}), OllamaInvalidResponse),
])
def test_bad_chat_responses(response, expected):
    with pytest.raises(expected):
        client_for(lambda _: response).chat([])


@pytest.mark.parametrize("vectors", [[], [[]], [[0, 0]], [[True, 1]], [[float("nan")]], [["x"]], [[1], [1, 2]]])
def test_invalid_embeddings(vectors):
    from app.shared.ollama.client import validate_embeddings
    with pytest.raises(OllamaInvalidResponse):
        validate_embeddings(vectors, 1 if len(vectors) < 2 else 2)


def test_empty_embed_does_not_call_http():
    def handler(_):
        pytest.fail("HTTP must not be called")
    assert client_for(handler).embed([]) == []
