from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class ComparedProduct(BaseModel):
    model_config = ConfigDict(extra="forbid")
    product_id: UUID
    product_name: str = Field(min_length=1)
    category_name: str | None = None
    image_url: str | None = None


class ComparisonRow(BaseModel):
    """One criterion; `values` follow the order of `products`, already formatted for display."""
    model_config = ConfigDict(extra="forbid")
    key: str
    label: str
    values: list[str]
    # Indexes of the products whose value is measurably better; empty when not comparable.
    best: list[int] = Field(default_factory=list)


class ProductComparison(BaseModel):
    model_config = ConfigDict(extra="forbid")
    category_name: str | None = None
    products: list[ComparedProduct] = Field(min_length=2, max_length=3)
    rows: list[ComparisonRow] = Field(default_factory=list)
    summary: list[str] = Field(default_factory=list)
