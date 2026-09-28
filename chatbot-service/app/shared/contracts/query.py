from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field, model_validator
from app.shared.contracts.enums import Intent, RequestedField

class ProductSearchPlan(BaseModel):
    model_config = ConfigDict(extra="forbid")

    category: str | None = None
    brands: list[str] = Field(default_factory=list)

    min_price: Decimal | None = Field(default=None, ge=0)
    max_price: Decimal | None = Field(default=None, ge=0)

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

    intent: Intent

    requested_fields: list[RequestedField] = Field(
        default_factory=list
    )

    references: dict[str, str] = Field(
        default_factory=dict
    )

    product_search: ProductSearchPlan | None = None

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