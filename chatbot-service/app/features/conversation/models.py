from __future__ import annotations

from datetime import datetime, timezone
from enum import Enum
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field
from app.shared.contracts.response import ProductCard
from app.shared.contracts.query import ProductSearchPlan


class SenderType(str, Enum):
    USER = "USER"
    ASSISTANT = "ASSISTANT"


class ChatSession(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: UUID = Field(default_factory=uuid4)
    customer_id: UUID
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    is_active: bool = True
    ended_at: datetime | None = None


class ChatMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: UUID = Field(default_factory=uuid4)
    session_id: UUID
    sender_type: SenderType
    message: str = Field(min_length=1)
    rating: int = Field(default=0, ge=-1, le=1)
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class EvidenceSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid")
    cards: list[ProductCard] = Field(default_factory=list, max_length=20)
    search: ProductSearchPlan | None = None
    captured_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class StoredAssistantMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")
    version: int = Field(default=1, ge=1, le=1)
    text: str = Field(min_length=1)
    snapshot: EvidenceSnapshot | None = None
