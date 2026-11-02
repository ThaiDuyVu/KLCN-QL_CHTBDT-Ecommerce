from app.shared.tracing import traced, event
import math
import re
from app.features.query_understanding.normalization import folded
from uuid import UUID

from app.features.knowledge.repository import IndexedChunk, KnowledgeRepository
from app.shared.contracts.knowledge import KnowledgeChunkMatch
from app.shared.contracts.response import SourceReference
from app.shared.ollama.client import OllamaError, validate_embeddings
from app.shared.ollama.protocol import EmbeddingModel


class KnowledgeUnavailable(RuntimeError):
    pass


def relevant_chunks(query: str, matches: list[KnowledgeChunkMatch]) -> list[KnowledgeChunkMatch]:
    """Conservative topical gate over ranked, status-eligible candidates.

    A cosine score alone is not calibrated relevance. Require the explicit
    question topic in chunk text, retaining rank and original provenance.
    Unsupported topics abstain rather than citing the nearest unrelated policy.
    """
    topics = {
        'returns': ('doi tra', 'hoan tra', 'return'),
        'payment': ('thanh toan', 'payment'),
        'warranty': ('bao hanh', 'warranty'),
        'promotion': ('khuyen mai', 'khuyen mai', 'promotion'),
        'shipping': ('giao hang', 'van chuyen', 'shipping'),
    }
    q = folded(query)
    wanted = [name for name, aliases in topics.items() if any(a in q for a in aliases)]
    tokens = {t for t in re.findall(r'\w+', q) if len(t) >= 4} - {'theo', 'demo', 'lieu', 'chinh', 'sach', 'hang', 'minh', 'duoc', 'khong', 'nhung', 'thong'}
    selected = []
    for match in matches:
        content = folded(match.content)
        valid_score = math.isfinite(match.similarity_score) and match.similarity_score > 0
        topical = any(any(alias in content for alias in topics[name]) for name in wanted) if wanted else bool(tokens.intersection(re.findall(r'\w+', content)))
        if valid_score and topical:
            selected.append(match)
    event('knowledge.relevance', {'candidate_count': len(matches), 'selected_chunk_ids': [m.chunk_id for m in selected], 'rejected_chunk_ids': [m.chunk_id for m in matches if m not in selected], 'topics': wanted, 'policy': 'positive_cosine_and_explicit_topic'})
    return selected


def chunk_text(content: str, *, size: int = 1000, overlap: int = 150) -> list[str]:
    """Stable character windows; normalize line endings, keep Unicode intact."""
    if size <= 0 or overlap < 0 or overlap >= size:
        raise ValueError("Require size > overlap >= 0")
    text = content.replace("\r\n", "\n").replace("\r", "\n").strip()
    chunks = []
    start = 0
    while start < len(text):
        chunk = text[start:start + size].strip()
        if chunk:
            chunks.append(chunk)
        if start + size >= len(text):
            break
        start += size - overlap
    return chunks


class KnowledgeIndexingService:
    def __init__(self, repository: KnowledgeRepository, embeddings: EmbeddingModel, *, status: str, chunk_size: int = 1000, overlap: int = 150):
        if not status.strip():
            raise ValueError("Explicit eligible status is required")
        chunk_text("", size=chunk_size, overlap=overlap)
        self.repository = repository
        self.embeddings = embeddings
        self.status, self.chunk_size, self.overlap = status, chunk_size, overlap

    def ingest(self, *, force: bool = False) -> int:
        count = 0
        for document in self.repository.documents(self.status):
            if document.status != self.status:
                continue
            contents = chunk_text(document.content, size=self.chunk_size, overlap=self.overlap)
            existing = {c.chunk_index: c for c in self.repository.chunks(document.document_id)}
            missing = [i for i, text in enumerate(contents) if force or i not in existing or existing[i].content != text or existing[i].embedding is None]
            # Bounded batches, one transaction per document after all vectors validate.
            generated = {}
            for start in range(0, len(missing), 32):
                batch = missing[start:start + 32]
                vectors = validate_embeddings(self.embeddings.embed([contents[i] for i in batch]), len(batch))
                generated.update(zip(batch, vectors))
            chunks = [IndexedChunk(i, text, generated[i] if i in generated else existing[i].embedding) for i, text in enumerate(contents)]
            if chunks:
                validate_embeddings([c.embedding for c in chunks], len(chunks))
            self.repository.synchronize(document, chunks)
            count += 1
        return count


class PgvectorKnowledgeRetrievalService:
    def __init__(self, repository: KnowledgeRepository, embeddings: EmbeddingModel, *, status: str):
        if not status.strip():
            raise ValueError("Explicit eligible status is required")
        self.repository, self.embeddings, self.status = repository, embeddings, status

    @traced('knowledge.retrieve', ('query', 'top_k'))
    def retrieve(self, query: str, *, top_k: int = 5) -> list[KnowledgeChunkMatch]:
        if not 1 <= top_k <= 20:
            raise ValueError("top_k must be between 1 and 20")
        if not query.strip():
            return []
        try:
            vector = validate_embeddings(self.embeddings.embed([query]), 1)[0]
        except OllamaError as exc:
            raise KnowledgeUnavailable("Knowledge embedding unavailable") from exc
        return relevant_chunks(query, self.repository.search(vector, self.status, top_k))

    def record_retrievals(self, history_id: UUID, matches: list[KnowledgeChunkMatch]) -> None:
        self.repository.record_retrievals(history_id, matches)


def build_source_references(matches: list[KnowledgeChunkMatch]) -> list[SourceReference]:
    unique = {match.chunk_id: match for match in matches}
    return [SourceReference(document_id=m.source.document_id, chunk_id=m.chunk_id,
                            title=m.source.title, source=m.source.source) for m in unique.values()]
