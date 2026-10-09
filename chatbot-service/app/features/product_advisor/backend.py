"""Typed adapter to the existing authenticated backend product APIs."""

from contextvars import ContextVar
from typing import Any
from uuid import UUID

import httpx

from app.shared.contracts.query import ProductSearchPlan


backend_cookies: ContextVar[dict[str, str] | None] = ContextVar("backend_product_cookies", default=None)


class BackendProductClient:
    def __init__(self, base_url: str, bearer_token: str = "") -> None:
        self._client = httpx.Client(
            base_url=base_url.rstrip("/"),
            timeout=10,
            headers={"Authorization": f"Bearer {bearer_token}"} if bearer_token else {},
        )

    def _get(self, path: str, **kwargs) -> httpx.Response:
        cookies = backend_cookies.get()
        request = self._client.build_request("GET", path, cookies=cookies, **kwargs)
        if cookies:
            request.headers.pop("Authorization", None)
        return self._client.send(request)

    def search(
        self, plan: ProductSearchPlan, warehouse_id: UUID | None, limit: int, offset: int
    ) -> list[tuple[UUID, UUID]]:
        params: list[tuple[str, str]] = [("limit", str(limit)), ("offset", str(offset))]
        scalar = {
            "category": plan.category,
            "minPrice": plan.min_price,
            "maxPrice": plan.max_price,
            "minRamGb": plan.min_ram_gb,
            "minStorageGb": plan.min_storage_gb,
            "warehouseId": warehouse_id,
        }
        params.extend((name, str(value)) for name, value in scalar.items() if value is not None)
        params.append(("inStockOnly", str(plan.in_stock_only).lower()))
        params.extend(("brands", brand) for brand in plan.brands)
        params.extend(("cpuKeywords", keyword) for keyword in plan.cpu_keywords)
        response = self._get("/api/v1/products/search/advanced", params=params)
        response.raise_for_status()
        return [(UUID(row["productId"]), UUID(row["variantId"])) for row in response.json()]

    def detail(self, product_id: UUID, warehouse_id: UUID | None = None) -> dict[str, Any]:
        response = self._get(
            f"/api/v1/products/{product_id}/detail",
            params={"warehouseId": str(warehouse_id)} if warehouse_id else None,
        )
        response.raise_for_status()
        return response.json()

    def catalog_page(self, page: int, size: int = 100) -> dict[str, Any]:
        response = self._get("/api/v1/products", params={"page": page, "size": size})
        response.raise_for_status()
        return response.json()
