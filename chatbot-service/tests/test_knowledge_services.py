from uuid import uuid4

import pytest

from app.features.knowledge.repository import IndexedChunk, KnowledgeDocument
from app.features.knowledge.service import (KnowledgeIndexingService, KnowledgeUnavailable, PgvectorKnowledgeRetrievalService, build_source_references, chunk_text)
from app.shared.ollama.client import OllamaUnavailable
from tests.test_ai_services import knowledge_match


class FakeEmbeddings:
    def __init__(self):
        self.calls = []

    def embed(self, texts):
        self.calls.append(texts)
        return [[float(len(text)), 1.0] for text in texts]


class MemoryRepository:
    def __init__(self, docs):
        self.docs = docs
        self.index = {}
        self.search_calls = []
        self.matches = []
        self.audits = []

    def documents(self, status):
        # Intentionally returns invalid documents to test defensive service filtering.
        return self.docs

    def chunks(self, document_id):
        return self.index.get(document_id, [])

    def synchronize(self, document, chunks):
        self.index[document.document_id] = chunks

    def search(self, vector, status, top_k):
        self.search_calls.append((vector, status, top_k))
        return sorted(self.matches, key=lambda m: m.similarity_score, reverse=True)[:top_k]

    def record_retrievals(self, history_id, matches):
        self.audits.append((history_id, matches))


def document(content, status="PUBLISHED"):
    return KnowledgeDocument(uuid4(), "Chính sách", content, status)


def test_chunking_order_overlap_and_normalization():
    assert chunk_text("abcdefghij", size=4, overlap=1) == ["abcd", "defg", "ghij"]
    assert chunk_text(" \r\na\r\nb\r ") == ["a\nb"]
    assert chunk_text("   ") == []


@pytest.mark.parametrize("size,overlap", [(0, 0), (3, 3), (3, -1)])
def test_invalid_chunk_configuration(size, overlap):
    with pytest.raises(ValueError):
        chunk_text("x", size=size, overlap=overlap)


def test_index_idempotent_status_filter_and_empty_document():
    good, bad, empty = document("abcdefghij"), document("secret", "DRAFT"), document(" ")
    repo, embed = MemoryRepository([good, bad, empty]), FakeEmbeddings()
    service = KnowledgeIndexingService(repo, embed, status="PUBLISHED", chunk_size=4, overlap=1)
    assert service.ingest() == 2
    assert [c.chunk_index for c in repo.index[good.document_id]] == [0, 1, 2]
    assert bad.document_id not in repo.index
    assert repo.index[empty.document_id] == []
    first_calls = len(embed.calls)
    service.ingest()
    assert len(embed.calls) == first_calls
    service.ingest(force=True)
    assert len(embed.calls) == first_calls + 1


def test_changed_chunks_only_reembedded_and_trailing_chunks_removed():
    doc = document("abcdefghij")
    repo, embed = MemoryRepository([doc]), FakeEmbeddings()
    service = KnowledgeIndexingService(repo, embed, status="PUBLISHED", chunk_size=4, overlap=0)
    service.ingest()
    repo.docs = [KnowledgeDocument(doc.document_id, doc.title, "abcdZZZZ", doc.status)]
    service.ingest()
    assert embed.calls[-1] == ["ZZZZ"]
    assert [c.content for c in repo.index[doc.document_id]] == ["abcd", "ZZZZ"]


def test_embedding_failure_does_not_persist_partial_document():
    repo = MemoryRepository([document("abc")])
    class FailedEmbedding:
        def embed(self, texts):
            raise OllamaUnavailable("offline")
    with pytest.raises(OllamaUnavailable):
        KnowledgeIndexingService(repo, FailedEmbedding(), status="PUBLISHED").ingest()
    assert repo.index == {}


def test_retrieval_topk_ranking_metadata_and_audit_handoff():
    repo, embed = MemoryRepository([]), FakeEmbeddings()
    low, high = knowledge_match(), knowledge_match()
    low.similarity_score, high.similarity_score = 0.1, 0.95
    repo.matches = [low, high]
    service = PgvectorKnowledgeRetrievalService(repo, embed, status="PUBLISHED")
    result = service.retrieve("đổi trả", top_k=1)
    assert result == [high]
    assert result[0].source.title == "Đổi trả"
    assert repo.search_calls[0][1:] == ("PUBLISHED", 1)
    assert repo.audits == []
    history_id = uuid4()
    service.record_retrievals(history_id, result)
    assert repo.audits == [(history_id, result)]
    sources = build_source_references([high, high])
    assert len(sources) == 1
    assert sources[0].chunk_id == high.chunk_id
    assert sources[0].document_id == high.source.document_id


def test_retrieval_blank_no_results_and_limits():
    repo, embed = MemoryRepository([]), FakeEmbeddings()
    service = PgvectorKnowledgeRetrievalService(repo, embed, status="PUBLISHED")
    assert service.retrieve(" ") == []
    assert embed.calls == []
    assert service.retrieve("unknown") == []
    for top_k in (0, 21):
        with pytest.raises(ValueError):
            service.retrieve("x", top_k=top_k)


def test_retrieval_model_failure_maps_error():
    class FailedEmbedding:
        def embed(self, texts):
            raise OllamaUnavailable("offline")
    with pytest.raises(KnowledgeUnavailable):
        PgvectorKnowledgeRetrievalService(MemoryRepository([]), FailedEmbedding(), status="PUBLISHED").retrieve("x")
