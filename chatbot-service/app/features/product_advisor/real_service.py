"""Structured eligibility first, optional vector ranking, then live catalog enrichment."""

from collections import defaultdict
from contextvars import ContextVar
from decimal import Decimal
from typing import Any
from uuid import UUID

import httpx

from app.features.product_advisor.backend import BackendProductClient
from app.features.product_advisor.normalization import capacity_gb
from app.features.product_advisor.semantic import OllamaEmbeddingClient, ProductSemanticIndex
from app.shared.contracts.product import EligibleVariant, ProductMatch
from app.shared.contracts.query import ProductSearchPlan
from app.shared.contracts.response import ProductCard

_current_plan: ContextVar[ProductSearchPlan | None] = ContextVar("product_search_plan", default=None)


class RealProductAdvisorService:
    def __init__(
        self,
        backend: BackendProductClient,
        warehouse_id: UUID | None = None,
        index: ProductSemanticIndex | None = None,
        embedder: OllamaEmbeddingClient | None = None,
    ) -> None:
        self.backend = backend
        self.warehouse_id = warehouse_id
        self.index = index
        self.embedder = embedder

    def _eligible(self, detail: dict[str, Any], variant: dict[str, Any], plan: ProductSearchPlan) -> bool:
        if detail["product"]["status"] != "ACTIVE" or variant["status"] != "ACTIVE":
            return False
        if plan.category and detail["category"]["categoryName"].casefold() != plan.category.strip().casefold():
            return False
        if plan.brands and detail["brand"]["brandName"].casefold() not in {x.strip().casefold() for x in plan.brands}:
            return False
        price = Decimal(str(variant["effectivePrice"]))
        if plan.min_price is not None and price < plan.min_price:
            return False
        if plan.max_price is not None and price > plan.max_price:
            return False
        ram = capacity_gb(variant.get("ram"))
        if plan.min_ram_gb is not None and (ram is None or ram < plan.min_ram_gb):
            return False
        storage = capacity_gb(variant.get("storage"))
        if plan.min_storage_gb is not None and (storage is None or storage < plan.min_storage_gb):
            return False
        if plan.in_stock_only and (variant.get("availableQuantity") or 0) < 1:
            return False
        cpu_values = " ".join(
            spec["specValue"].casefold()
            for spec in detail.get("specifications", [])
            if spec["specKey"].casefold() in {"cpu", "processor", "vi xử lý", "bộ xử lý"}
        )
        return all(keyword.strip().casefold() in cpu_values for keyword in plan.cpu_keywords)

    @staticmethod
    def _variant(product_id: UUID, row: dict[str, Any]) -> EligibleVariant:
        return EligibleVariant(
            product_id=product_id,
            variant_id=UUID(row["variantId"]),
            sku=row["sku"],
            original_price=Decimal(str(row["originalPrice"])),
            effective_price=Decimal(str(row["effectivePrice"])),
            ram=row.get("ram"), storage=row.get("storage"), color=row.get("color"),
            available_quantity=row.get("availableQuantity"),
            warranty_months=row.get("warrantyMonths"),
        )

    def search(self, plan: ProductSearchPlan) -> list[ProductMatch]:
        if plan.in_stock_only and self.warehouse_id is None:
            raise ValueError("A warehouse is required for in_stock_only product search")
        _current_plan.set(plan)
        variants_by_product: dict[UUID, set[UUID]] = defaultdict(set)
        # Bounded paging avoids loading the entire catalog. Eligibility stays at the backend.
        for offset in range(0, 500, 100):
            page = self.backend.search(plan, self.warehouse_id, limit=100, offset=offset)
            for product_id, variant_id in page:
                variants_by_product[product_id].add(variant_id)
            if len(page) < 100:
                break
        if not variants_by_product:
            return []
        scores: dict[UUID, float] = {}
        if plan.semantic_query and self.index is not None and self.embedder is not None:
            scores = self.index.scores(
                list(variants_by_product), self.embedder.embed(plan.semantic_query), self.embedder.model
            )
        ranked_ids = sorted(variants_by_product, key=lambda key: (-scores.get(key, -2), str(key)))
        matches: list[ProductMatch] = []
        for product_id in ranked_ids:
            try:
                detail = self.backend.detail(product_id, self.warehouse_id)
            except httpx.HTTPStatusError as error:
                if error.response.status_code == 404:
                    continue
                raise
            eligible = [
                self._variant(product_id, row)
                for row in detail["variants"]
                if UUID(row["variantId"]) in variants_by_product[product_id]
                and self._eligible(detail, row, plan)
            ]
            if not eligible:
                continue
            eligible.sort(key=lambda row: (row.effective_price, row.sku))
            reasons = []
            if plan.category:
                reasons.append(f"Danh mục: {detail['category']['categoryName']}")
            if plan.brands:
                reasons.append(f"Thương hiệu: {detail['brand']['brandName']}")
            if plan.min_ram_gb is not None:
                reasons.append(f"RAM từ {plan.min_ram_gb}GB")
            if plan.min_storage_gb is not None:
                reasons.append(f"Bộ nhớ từ {plan.min_storage_gb}GB")
            matches.append(ProductMatch(
                product_id=product_id,
                semantic_score=scores.get(product_id),
                ranking_score=scores.get(product_id),
                eligible_variants=eligible,
                reasons=reasons,
            ))
            if len(matches) >= plan.top_k:
                break
        return matches

    def build_cards(self, matches: list[ProductMatch]) -> list[ProductCard]:
        plan = _current_plan.get()
        cards: list[ProductCard] = []
        for match in matches:
            try:
                detail = self.backend.detail(match.product_id, self.warehouse_id)
            except httpx.HTTPStatusError as error:
                if error.response.status_code == 404:
                    continue
                raise
            eligible_ids = {variant.variant_id for variant in match.eligible_variants}
            variants = [
                row for row in detail["variants"]
                if UUID(row["variantId"]) in eligible_ids
                and (self._eligible(detail, row, plan) if plan is not None else row["status"] == "ACTIVE")
            ]
            if not variants:
                continue
            row = min(variants, key=lambda value: (Decimal(str(value["effectivePrice"])), value["sku"]))
            images = detail.get("images", [])
            image = next((item for item in images if item.get("isPrimary")), images[0] if images else None)
            cards.append(ProductCard(
                product_id=match.product_id,
                product_name=detail["product"]["productName"],
                variant_id=UUID(row["variantId"]), sku=row["sku"],
                original_price=Decimal(str(row["originalPrice"])),
                effective_price=Decimal(str(row["effectivePrice"])),
                ram=row.get("ram"), storage=row.get("storage"), color=row.get("color"),
                available_quantity=row.get("availableQuantity"),
                warranty_months=row.get("warrantyMonths"),
                image_url=image["imageUrl"] if image else None,
                reasons=match.reasons,
            ))
        return cards
