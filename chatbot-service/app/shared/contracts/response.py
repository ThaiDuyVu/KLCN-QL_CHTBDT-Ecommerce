from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class ProductCard(BaseModel):
    model_config = ConfigDict(extra="forbid")

    product_id: UUID
    product_name: str = Field(min_length=1)

    variant_id: UUID | None = None
    sku: str | None = None

    original_price: Decimal | None = Field(default=None, ge=0)
    effective_price: Decimal | None = Field(default=None, ge=0)

    ram: str | None = None
    storage: str | None = None
    color: str | None = None

    available_quantity: int | None = Field(default=None, ge=0)
    warranty_months: int | None = Field(default=None, ge=0)

    image_url: str | None = None

    reasons: list[str] = Field(default_factory=list)


class SourceReference(BaseModel):
    model_config = ConfigDict(extra="forbid")

    document_id: UUID
    chunk_id: UUID | None = None

    title: str = Field(min_length=1)
    source: str | None = None


class ChatResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    session_id: UUID

    message: str = Field(min_length=1)

    products: list[ProductCard] = Field(
        default_factory=list
    )

    sources: list[SourceReference] = Field(
        default_factory=list
    )

    suggested_questions: list[str] = Field(
        default_factory=list
    )