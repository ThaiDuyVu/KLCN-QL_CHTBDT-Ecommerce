from uuid import UUID

from app.features.knowledge.repository import IndexedChunk, KnowledgeRepository
from app.shared.contracts.knowledge import KnowledgeChunkMatch
from app.shared.contracts.response import SourceReference
from app.shared.ollama.client import OllamaError, validate_embeddings
from app.shared.ollama.protocol import EmbeddingModel


class KnowledgeUnavailable(RuntimeError):
    pass


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

    def retrieve(self, query: str, *, top_k: int = 5) -> list[KnowledgeChunkMatch]:
        if not 1 <= top_k <= 20:
            raise ValueError("top_k must be between 1 and 20")
        if not query.strip():
            return []
        try:
            vector = validate_embeddings(self.embeddings.embed([query]), 1)[0]
        except OllamaError as exc:
            raise KnowledgeUnavailable("Knowledge embedding unavailable") from exc
        return self.repository.search(vector, self.status, top_k)

    def record_retrievals(self, history_id: UUID, matches: list[KnowledgeChunkMatch]) -> None:
        self.repository.record_retrievals(history_id, matches)


def build_source_references(matches: list[KnowledgeChunkMatch]) -> list[SourceReference]:
    unique = {match.chunk_id: match for match in matches}
    return [SourceReference(document_id=m.source.document_id, chunk_id=m.chunk_id,
                            title=m.source.title, source=m.source.source) for m in unique.values()]
