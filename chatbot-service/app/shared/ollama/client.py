"""Synchronous adapter matching the existing synchronous service contracts."""
from app.shared.tracing import traced, event
import math
from typing import Any

import httpx

from app.shared.config import Settings


class OllamaError(RuntimeError):
    """Expected model/transport failure; safe for callers to handle."""


class OllamaUnavailable(OllamaError):
    pass


class OllamaModelError(OllamaError):
    pass


class OllamaInvalidResponse(OllamaError):
    pass


def validate_embeddings(vectors: Any, count: int) -> list[list[float]]:
    if not isinstance(vectors, list) or len(vectors) != count:
        raise OllamaInvalidResponse("Embedding count mismatch")
    dimension = None
    for vector in vectors:
        if not isinstance(vector, list) or not vector:
            raise OllamaInvalidResponse("Empty embedding")
        if any(isinstance(x, bool) or not isinstance(x, (int, float)) or not math.isfinite(x) for x in vector):
            raise OllamaInvalidResponse("Non-finite embedding")
        if not any(x != 0 for x in vector):
            raise OllamaInvalidResponse("Zero embedding cannot use cosine similarity")
        dimension = dimension or len(vector)
        if len(vector) != dimension:
            raise OllamaInvalidResponse("Inconsistent embedding dimensions")
    return vectors


class OllamaClient:
    def __init__(self, settings: Settings, *, client: httpx.Client | None = None):
        self.settings = settings
        self._owned = client is None
        self.client = client if client is not None else httpx.Client(timeout=settings.ollama_timeout_seconds)

    def close(self) -> None:
        if self._owned:
            self.client.close()

    @traced("ollama.http", ("endpoint", "payload"), result=False)
    def _post(self, endpoint: str, payload: dict[str, Any]) -> dict[str, Any]:
        try:
            response = self.client.post(
                self.settings.ollama_base_url.rstrip("/") + endpoint,
                json=payload, timeout=self.settings.ollama_timeout_seconds,
            )
        except httpx.TimeoutException as exc:
            raise OllamaUnavailable("Ollama timed out") from exc
        except httpx.RequestError as exc:
            raise OllamaUnavailable("Ollama connection failed") from exc
        event("ollama.http_status", {"endpoint": endpoint, "status_code": response.status_code})
        if not response.is_success:
            event("ollama.error_body", response.text, status="warning")
            raise OllamaModelError(f"Ollama HTTP {response.status_code}")
        try:
            data = response.json()
        except ValueError as exc:
            event("ollama.invalid_json", response.text, status="warning")
            raise OllamaInvalidResponse("Ollama returned invalid JSON") from exc
        if not isinstance(data, dict):
            raise OllamaInvalidResponse("Expected Ollama JSON object")
        if data.get("error"):
            raise OllamaModelError("Ollama reported a model error")
        if data.get("done_reason") == "length":
            event("ollama.output_limit", {"reason": "length", "eval_count": data.get("eval_count")}, status="warning")
        event("ollama.result", {k: v for k, v in data.items() if k != "embeddings"})
        return data

    @traced("ollama.chat", ("messages", "schema"))
    def chat(self, messages: list[dict[str, str]], *, schema: dict[str, Any] | None = None) -> str:
        payload = dict(model=self.settings.ollama_chat_model, messages=messages, stream=False, think=False,
                       options={"temperature": 0, "num_predict": 512})
        if schema is not None:
            payload["format"] = schema
        data = self._post("/api/chat", payload)
        message = data.get("message")
        content = message.get("content") if isinstance(message, dict) else None
        if not isinstance(content, str) or not content.strip():
            raise OllamaInvalidResponse("Missing message content")
        return content.strip()

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        data = self._post("/api/embed", dict(model=self.settings.ollama_embedding_model, input=texts))
        vectors = validate_embeddings(data.get("embeddings"), len(texts))
        event("embedding.shape", {"count": len(vectors), "dimensions": len(vectors[0]) if vectors else 0})
        return vectors
