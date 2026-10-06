from typing import Any, Protocol


class ChatModel(Protocol):
    def chat(self, messages: list[dict[str, str]], *, schema: dict[str, Any] | None = None) -> str: ...


class EmbeddingModel(Protocol):
    def embed(self, texts: list[str]) -> list[list[float]]: ...
