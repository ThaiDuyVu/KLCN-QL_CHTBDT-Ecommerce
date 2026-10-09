from uuid import uuid4

import pytest

from app.features.knowledge.repository import IndexedChunk, KnowledgeDocument, KnowledgeRepositoryError, PostgresKnowledgeRepository
from tests.test_ai_services import knowledge_match


class FakeCursor:
    def __init__(self, *, one=None, batches=()):
        self.one = one
        self.batches = iter(batches)
        self.calls = []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass

    def execute(self, sql, params):
        self.calls.append((sql, params))

    def fetchone(self):
        return self.one

    def fetchall(self):
        return next(self.batches)


class FakeConnection:
    def __init__(self, cursor):
        self.cur = cursor
        self.failed = False

    def __enter__(self):
        return self

    def __exit__(self, error_type, *_):
        self.failed = error_type is not None

    def cursor(self):
        return self.cur


def repository(cursor):
    connection = FakeConnection(cursor)
    return PostgresKnowledgeRepository(lambda: connection), connection


def test_search_parameterizes_vector_status_limit_and_maps_joined_metadata():
    chunk_id, document_id = uuid4(), uuid4()
    cursor = FakeCursor(batches=[[(chunk_id, 2, "evidence", 0.8, document_id, "Policy", "url", "POLICY")]])
    repo, _ = repository(cursor)
    matches = repo.search([1, 2], "PUBLISHED", 3)
    assert matches[0].chunk_id == chunk_id
    assert matches[0].source.document_id == document_id
    assert matches[0].source.title == "Policy"
    assert matches[0].source.source == "url"
    assert matches[0].similarity_score == 0.8
    sql, params = cursor.calls[0]
    assert "MATERIALIZED" in sql and "vector_dims" in sql
    assert "d.status = %s" in sql
    assert params == ("PUBLISHED", 2, "[1, 2]", "[1, 2]", 3)


def test_synchronize_is_transactional_and_handles_trailing_audited_chunks():
    doc = KnowledgeDocument(uuid4(), "Policy", "abc", "PUBLISHED")
    cursor = FakeCursor(one=("abc", "PUBLISHED"), batches=[[], []])
    repo, conn = repository(cursor)
    repo.synchronize(doc, [IndexedChunk(0, "abc", [1, 2])])
    assert not conn.failed
    assert any("ON CONFLICT" in sql for sql, _ in cursor.calls)
    assert any("SET embedding_vector = NULL" in sql for sql, _ in cursor.calls)
    assert any("DELETE" in sql and "NOT EXISTS" in sql for sql, _ in cursor.calls)


def test_changed_document_aborts_before_upsert():
    doc = KnowledgeDocument(uuid4(), "Policy", "abc", "PUBLISHED")
    cursor = FakeCursor(one=("changed", "PUBLISHED"))
    repo, conn = repository(cursor)
    with pytest.raises(KnowledgeRepositoryError):
        repo.synchronize(doc, [])
    assert conn.failed
    assert not any("INSERT" in sql for sql, _ in cursor.calls)


def test_rewriting_audited_chunk_requires_new_document_version():
    doc = KnowledgeDocument(uuid4(), "Policy", "new", "PUBLISHED")
    cursor = FakeCursor(one=("new", "PUBLISHED"), batches=[[], [(0, "old")]])
    repo, conn = repository(cursor)
    with pytest.raises(KnowledgeRepositoryError, match="version"):
        repo.synchronize(doc, [IndexedChunk(0, "new", [1, 2])])
    assert conn.failed


def test_audit_requires_persisted_history_and_deduplicates_matches():
    history_id = uuid4()
    match = knowledge_match()
    cursor = FakeCursor(one=(history_id,))
    repo, _ = repository(cursor)
    repo.record_retrievals(history_id, [match, match])
    assert len(cursor.calls) == 2
    sql, params = cursor.calls[1]
    assert "NOT EXISTS" in sql
    assert params[0] == history_id
    assert params[1] == match.chunk_id
    missing, _ = repository(FakeCursor())
    with pytest.raises(KnowledgeRepositoryError, match="Persist"):
        missing.record_retrievals(history_id, [match])
