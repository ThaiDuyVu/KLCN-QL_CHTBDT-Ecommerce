"""Optional real pgvector test; isolated schema, never touches business tables.

KNOWLEDGE_TEST_DATABASE_URL must point to a disposable PostgreSQL database
with pgvector preinstalled. The fixture creates and drops only its own schema.
"""
import os
from contextlib import contextmanager
from uuid import uuid4

import pytest

from app.features.knowledge.repository import IndexedChunk, KnowledgeDocument, KnowledgeRepositoryError, PostgresKnowledgeRepository


@pytest.fixture
def pg_repository():
    dsn = os.environ.get("KNOWLEDGE_TEST_DATABASE_URL")
    if not dsn:
        pytest.skip("KNOWLEDGE_TEST_DATABASE_URL is not configured")
    import psycopg
    from psycopg import sql
    schema = "test_knowledge_" + uuid4().hex
    with psycopg.connect(dsn) as conn:
        conn.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
        conn.execute(sql.SQL("SET search_path TO {}, public").format(sql.Identifier(schema)))
        conn.execute("CREATE TABLE knowledge_documents (document_id UUID PRIMARY KEY, title TEXT NOT NULL, content TEXT NOT NULL, status TEXT NOT NULL, source TEXT, document_type TEXT)")
        conn.execute("CREATE TABLE knowledge_chunks (chunk_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), document_id UUID REFERENCES knowledge_documents, chunk_index INT, content TEXT, embedding_vector vector, UNIQUE(document_id, chunk_index))")
        conn.execute("CREATE TABLE chat_history (history_id UUID PRIMARY KEY)")
        conn.execute("CREATE TABLE chat_message_retrievals (retrieval_id UUID DEFAULT gen_random_uuid(), history_id UUID REFERENCES chat_history, chunk_id UUID REFERENCES knowledge_chunks, similarity_score NUMERIC(10,8))")

    @contextmanager
    def connect():
        with psycopg.connect(dsn) as conn:
            conn.execute(sql.SQL("SET search_path TO {}, public").format(sql.Identifier(schema)))
            yield conn
    try:
        yield PostgresKnowledgeRepository(connect), connect
    finally:
        with psycopg.connect(dsn) as conn:
            conn.execute(sql.SQL("DROP SCHEMA {} CASCADE").format(sql.Identifier(schema)))


def test_pgvector_status_dimensions_ranking_audit_and_version_guard(pg_repository):
    repo, connect = pg_repository
    published = KnowledgeDocument(uuid4(), "Policy", "first second", "PUBLISHED", "policy.md", "POLICY")
    draft = KnowledgeDocument(uuid4(), "Draft", "private", "DRAFT")
    with connect() as conn:
        for doc in (published, draft):
            conn.execute("INSERT INTO knowledge_documents VALUES (%s,%s,%s,%s,%s,%s)", (doc.document_id, doc.title, doc.content, doc.status, doc.source, doc.document_type))
    repo.synchronize(published, [IndexedChunk(0, "first", [1, 0]), IndexedChunk(1, "second", [0, 1]), IndexedChunk(2, "other dimension", [1, 0, 0])])
    repo.synchronize(draft, [IndexedChunk(0, "private", [1, 0])])
    assert repo.documents("PUBLISHED") == [published]
    matches = repo.search([1, 0], "PUBLISHED", 1)
    assert len(matches) == 1
    assert matches[0].content == "first"
    assert matches[0].similarity_score == pytest.approx(1)
    assert matches[0].source.title == "Policy"
    assert len(repo.search([1, 0], "PUBLISHED", 20)) == 2
    history_id = uuid4()
    with connect() as conn:
        conn.execute("INSERT INTO chat_history VALUES (%s)", (history_id,))
    repo.record_retrievals(history_id, matches + matches)
    repo.record_retrievals(history_id, matches)
    with connect() as conn:
        assert conn.execute("SELECT count(*) FROM chat_message_retrievals").fetchone()[0] == 1
    with pytest.raises(KnowledgeRepositoryError):
        repo.synchronize(published, [IndexedChunk(0, "changed", [1, 0])])
    # Remove unreferenced trailing chunks; preserve audited first chunk.
    repo.synchronize(published, [IndexedChunk(0, "first", [1, 0])])
    assert len(repo.chunks(published.document_id)) == 1
