from typing import Protocol

from app.shared.contracts.knowledge import KnowledgeChunkMatch


class KnowledgeRetrievalService(Protocol):
    def retrieve(self, query: str, *, top_k: int = 5) -> list[KnowledgeChunkMatch]: ...
