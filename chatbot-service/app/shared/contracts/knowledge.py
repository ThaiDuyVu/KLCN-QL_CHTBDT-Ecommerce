from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class KnowledgeSource(BaseModel):
    model_config = ConfigDict(extra="forbid")

    document_id: UUID

    title: str = Field(min_length=1)
    source: str | None = None
    document_type: str | None = None


class KnowledgeChunkMatch(BaseModel):
    model_config = ConfigDict(extra="forbid")

    chunk_id: UUID
    chunk_index: int = Field(ge=0)

    content: str = Field(min_length=1)

    similarity_score: float

    source: KnowledgeSource