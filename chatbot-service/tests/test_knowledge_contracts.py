from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.shared.contracts.knowledge import (
    KnowledgeChunkMatch,
    KnowledgeSource,
)


def test_knowledge_chunk_match_accepts_retrieval_result() -> None:
    document_id = uuid4()

    source = KnowledgeSource(
        document_id=document_id,
        title="Chính sách đổi trả",
        source="internal-policy",
        document_type="POLICY",
    )

    match = KnowledgeChunkMatch(
        chunk_id=uuid4(),
        chunk_index=2,
        content="Khách hàng có thể yêu cầu đổi trả...",
        similarity_score=0.87,
        source=source,
    )

    assert match.chunk_index == 2
    assert match.similarity_score == 0.87
    assert match.source.document_id == document_id


def test_knowledge_source_allows_optional_metadata() -> None:
    source = KnowledgeSource(
        document_id=uuid4(),
        title="Hướng dẫn bảo hành",
    )

    assert source.source is None
    assert source.document_type is None


def test_knowledge_chunk_rejects_negative_index() -> None:
    with pytest.raises(ValidationError):
        KnowledgeChunkMatch(
            chunk_id=uuid4(),
            chunk_index=-1,
            content="Nội dung",
            similarity_score=0.8,
            source=KnowledgeSource(
                document_id=uuid4(),
                title="Tài liệu",
            ),
        )


def test_knowledge_contract_rejects_unknown_fields() -> None:
    with pytest.raises(ValidationError):
        KnowledgeSource(
            document_id=uuid4(),
            title="Tài liệu",
            status="ACTIVE",
        )