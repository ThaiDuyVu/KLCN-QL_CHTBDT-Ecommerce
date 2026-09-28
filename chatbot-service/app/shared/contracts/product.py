from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator

class ProductCandidate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    product_id: UUID
    semantic_score: float


class EligibleVariant(BaseModel):
    model_config = ConfigDict(extra="forbid")

    variant_id: UUID
    product_id: UUID

    sku: str = Field(min_length=1)

    original_price: Decimal = Field(ge=0)
    effective_price: Decimal = Field(ge=0)

    ram: str | None = None
    storage: str | None = None
    color: str | None = None

    available_quantity: int | None = Field(default=None, ge=0)
    warranty_months: int | None = Field(default=None, ge=0)


class ProductMatch(BaseModel):
    model_config = ConfigDict(extra="forbid")

    product_id: UUID

    semantic_score: float | None = None
    ranking_score: float | None = None

    eligible_variants: list[EligibleVariant] = Field(
        default_factory=list
    )

    reasons: list[str] = Field(
        default_factory=list
    )

    @model_validator(mode="after")
    def validate_variant_product_ids(self) -> "ProductMatch":
        mismatched = [
            variant
            for variant in self.eligible_variants
            if variant.product_id != self.product_id
        ]

        if mismatched:
            raise ValueError(
                "all eligible_variants must belong to ProductMatch.product_id"
            )

        return self