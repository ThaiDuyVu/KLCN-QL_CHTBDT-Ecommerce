"""Parameterized SQL only; the model never supplies a query or identifiers."""
from collections.abc import Callable
from dataclasses import dataclass
import json
from typing import Any, Protocol
from uuid import UUID

from app.shared.contracts.knowledge import KnowledgeChunkMatch, KnowledgeSource
from app.shared.ollama.client import validate_embeddings


class KnowledgeRepositoryError(RuntimeError):
    pass


@dataclass(frozen=True)
class KnowledgeDocument:
    document_id: UUID
    title: str
    content: str
    status: str
    source: str | None = None
    document_type: str | None = None


@dataclass(frozen=True)
class IndexedChunk:
    chunk_index: int
    content: str
    embedding: list[float] | None


class KnowledgeRepository(Protocol):
    def documents(self, status: str) -> list[KnowledgeDocument]: ...
    def chunks(self, document_id: UUID) -> list[IndexedChunk]: ...
    def synchronize(self, document: KnowledgeDocument, chunks: list[IndexedChunk]) -> None: ...
    def search(self, vector: list[float], status: str, top_k: int) -> list[KnowledgeChunkMatch]: ...
    def record_retrievals(self, history_id: UUID, matches: list[KnowledgeChunkMatch]) -> None: ...


def vector_literal(vector: list[float]) -> str:
    validate_embeddings([vector], 1)
    return json.dumps(vector, allow_nan=False)


class PostgresKnowledgeRepository:
    def __init__(self, connect: Callable[[], Any]):
        self.connect = connect

    def documents(self, status: str) -> list[KnowledgeDocument]:
        with self.connect() as conn, conn.cursor() as cur:
            cur.execute("SELECT document_id, title, content, status, source, document_type FROM knowledge_documents WHERE status = %s ORDER BY document_id", (status,))
            return [KnowledgeDocument(*row) for row in cur.fetchall()]

    def chunks(self, document_id: UUID) -> list[IndexedChunk]:
        with self.connect() as conn, conn.cursor() as cur:
            cur.execute("SELECT chunk_index, content, embedding_vector::text FROM knowledge_chunks WHERE document_id = %s ORDER BY chunk_index", (document_id,))
            return [IndexedChunk(i, content, json.loads(vector) if vector else None) for i, content, vector in cur.fetchall()]

    def synchronize(self, document: KnowledgeDocument, chunks: list[IndexedChunk]) -> None:
        with self.connect() as conn, conn.cursor() as cur:
            cur.execute("SELECT content, status FROM knowledge_documents WHERE document_id = %s FOR UPDATE", (document.document_id,))
            if cur.fetchone() != (document.content, document.status):
                raise KnowledgeRepositoryError("Document changed during indexing; retry")
            cur.execute("SELECT chunk_id FROM knowledge_chunks WHERE document_id = %s FOR UPDATE", (document.document_id,))
            cur.fetchall()
            # Preserve audit meaning. Referenced chunks must not have their text rewritten.
            cur.execute("SELECT c.chunk_index, c.content FROM knowledge_chunks c WHERE c.document_id = %s AND EXISTS (SELECT 1 FROM chat_message_retrievals r WHERE r.chunk_id = c.chunk_id)", (document.document_id,))
            incoming = {c.chunk_index: c.content for c in chunks}
            if any(incoming.get(index, content) != content for index, content in cur.fetchall()):
                raise KnowledgeRepositoryError("Audited document changed; create a new document version")
            for chunk in chunks:
                cur.execute("""INSERT INTO knowledge_chunks (document_id, chunk_index, content, embedding_vector)
                    VALUES (%s, %s, %s, %s::vector)
                    ON CONFLICT (document_id, chunk_index) DO UPDATE
                    SET content = EXCLUDED.content, embedding_vector = EXCLUDED.embedding_vector""",
                    (document.document_id, chunk.chunk_index, chunk.content, vector_literal(chunk.embedding)))
            # Old chunks referenced by audit remain stored, but cannot be retrieved.
            cur.execute("UPDATE knowledge_chunks SET embedding_vector = NULL WHERE document_id = %s AND chunk_index >= %s", (document.document_id, len(chunks)))
            cur.execute("""DELETE FROM knowledge_chunks c WHERE document_id = %s AND chunk_index >= %s
                AND NOT EXISTS (SELECT 1 FROM chat_message_retrievals r WHERE r.chunk_id = c.chunk_id)""", (document.document_id, len(chunks)))

    def search(self, vector: list[float], status: str, top_k: int) -> list[KnowledgeChunkMatch]:
        if not 1 <= top_k <= 20:
            raise ValueError("top_k must be between 1 and 20")
        literal = vector_literal(vector)
        with self.connect() as conn, conn.cursor() as cur:
            cur.execute("""WITH eligible AS MATERIALIZED (
                SELECT c.*, d.title, d.source, d.document_type FROM knowledge_chunks c
                JOIN knowledge_documents d ON d.document_id = c.document_id
                WHERE d.status = %s AND c.embedding_vector IS NOT NULL
                AND vector_dims(c.embedding_vector) = %s
                AND vector_norm(c.embedding_vector) > 0)
                SELECT chunk_id, chunk_index, content, 1 - (embedding_vector <=> %s::vector),
                    document_id, title, source, document_type
                FROM eligible ORDER BY embedding_vector <=> %s::vector, chunk_id LIMIT %s""",
                (status, len(vector), literal, literal, top_k))
            return [KnowledgeChunkMatch(chunk_id=row[0], chunk_index=row[1], content=row[2], similarity_score=row[3],
                source=KnowledgeSource(document_id=row[4], title=row[5], source=row[6], document_type=row[7])) for row in cur.fetchall()]

    def record_retrievals(self, history_id: UUID, matches: list[KnowledgeChunkMatch]) -> None:
        with self.connect() as conn, conn.cursor() as cur:
            # Serializes repeated/concurrent calls for the same persisted message.
            cur.execute("SELECT history_id FROM chat_history WHERE history_id = %s FOR UPDATE", (history_id,))
            if cur.fetchone() is None:
                raise KnowledgeRepositoryError("Persist message before recording retrievals")
            for match in {m.chunk_id: m for m in matches}.values():
                cur.execute("""INSERT INTO chat_message_retrievals (history_id, chunk_id, similarity_score)
                    SELECT %s, %s, %s WHERE NOT EXISTS (
                        SELECT 1 FROM chat_message_retrievals WHERE history_id = %s AND chunk_id = %s)""",
                    (history_id, match.chunk_id, match.similarity_score, history_id, match.chunk_id))
