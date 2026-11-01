from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator
from app.shared.contracts.enums import Intent, RequestedField

class ProductSelector(BaseModel):
    """Explicit identity, separate from minimum discovery filters."""
    model_config = ConfigDict(extra="forbid")
    product_id: UUID | None = None
    variant_id: UUID | None = None
    product_name: str | None = Field(default=None, min_length=1)
    sku: str | None = Field(default=None, min_length=1)
    ram_gb: int | None = Field(default=None, ge=1)
    storage_gb: int | None = Field(default=None, ge=1)
    color: str | None = Field(default=None, min_length=1)

    @model_validator(mode="after")
    def identity_required(self):
        if sum(value is not None for value in (self.product_id, self.variant_id, self.product_name, self.sku)) != 1:
            raise ValueError("Exactly one explicit identity is required")
        return self


class ProductSearchPlan(BaseModel):
    model_config = ConfigDict(extra="forbid")

    targets: list[ProductSelector] = Field(default_factory=list, max_length=3)
    category: str | None = None
    brands: list[str] = Field(default_factory=list)

    min_price: Decimal | None = Field(default=None, ge=0)
    max_price: Decimal | None = Field(default=None, ge=0)
    # "khoảng 25 triệu": rank results inside [min_price, max_price] by closeness to this price.
    target_price: Decimal | None = Field(default=None, ge=0)

    min_ram_gb: int | None = Field(default=None, ge=1)
    min_storage_gb: int | None = Field(default=None, ge=1)

    cpu_keywords: list[str] = Field(default_factory=list)

    in_stock_only: bool = False

    semantic_query: str | None = None

    top_k: int = Field(default=5, ge=1, le=20)

    @model_validator(mode="after")
    def validate_price_range(self) -> "ProductSearchPlan":
        if (
            self.min_price is not None
            and self.max_price is not None
            and self.min_price > self.max_price
        ):
            raise ValueError("min_price must be less than or equal to max_price")

        return self

class QueryPlan(BaseModel):
    model_config = ConfigDict(extra="forbid")

    clarification: str | None = None
    intent: Intent

    requested_fields: list[RequestedField] = Field(
        default_factory=list
    )

    references: dict[str, str] = Field(
        default_factory=dict
    )

    product_search: ProductSearchPlan | None = None

    # One search per product type for "1 điện thoại và 1 laptop với 100 triệu"; set only by the
    # deterministic normalizer, never taken from model output.
    bundle: list[ProductSearchPlan] = Field(default_factory=list, max_length=3)
    budget: Decimal | None = Field(default=None, ge=0)

    semantic_query: str | None = None

    @model_validator(mode="after")
    def validate_product_discovery(self) -> "QueryPlan":
        if (
            self.intent == Intent.PRODUCT_DISCOVERY
            and self.product_search is None
        ):
            raise ValueError(
                "product_search is required for PRODUCT_DISCOVERY"
            )

        return self