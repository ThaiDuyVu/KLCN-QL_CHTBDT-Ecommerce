"""Synchronous adapter matching the existing synchronous service contracts."""
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
        if not response.is_success:
            raise OllamaModelError(f"Ollama HTTP {response.status_code}")
        try:
            data = response.json()
        except ValueError as exc:
            raise OllamaInvalidResponse("Ollama returned invalid JSON") from exc
        if not isinstance(data, dict):
            raise OllamaInvalidResponse("Expected Ollama JSON object")
        if data.get("error"):
            raise OllamaModelError("Ollama reported a model error")
        return data

    def chat(self, messages: list[dict[str, str]], *, schema: dict[str, Any] | None = None) -> str:
        payload = dict(model=self.settings.ollama_chat_model, messages=messages, stream=False,
                       options={"temperature": 0})
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
        return validate_embeddings(data.get("embeddings"), len(texts))
