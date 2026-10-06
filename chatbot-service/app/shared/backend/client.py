from __future__ import annotations

from typing import Any

import httpx
from fastapi import HTTPException

from app.features.customer_context.models import AuthenticatedCustomer
from app.shared.config import get_settings


class BackendClient:
    def __init__(
        self,
        base_url: str | None = None,
        transport: httpx.AsyncBaseTransport | httpx.MockTransport | None = None,
        timeout: float = 10.0,
    ) -> None:
        self.base_url = base_url or get_settings().backend_base_url
        self.timeout = timeout
        self._transport = transport

    def _build_client(self) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url=self.base_url,
            timeout=self.timeout,
            transport=self._transport,
        )

    async def _request_json(
        self,
        method: str,
        path: str,
        cookies: dict[str, str] | None = None,
        params: dict[str, Any] | None = None,
    ) -> Any | None:
        try:
            async with self._build_client() as client:
                response = await client.request(
                    method=method.upper(),
                    url=path,
                    cookies=cookies,
                    params=params,
                )
        except httpx.TimeoutException as exc:
            raise HTTPException(
                status_code=503,
                detail="Backend service timed out.",
            ) from exc

        if response.status_code in {401, 403}:
            raise HTTPException(status_code=401)

        if response.status_code == 404:
            return None

        if response.status_code == 204:
            return None

        try:
            response.raise_for_status()
        except httpx.HTTPStatusError:
            return None

        if not response.content:
            return None

        try:
            data = response.json()
        except ValueError:
            return None

        return data

    async def get_current_user(self, cookies: dict[str, str]) -> dict[str, Any]:
        payload = await self._request_json("GET", "/api/auth/me", cookies=cookies)
        if not isinstance(payload, dict):
            raise HTTPException(status_code=401, detail="Invalid auth payload")
        return payload

    async def get_current_customer(
        self,
        cookies: dict[str, str],
    ) -> AuthenticatedCustomer:
        payload = await self.get_current_user(cookies)

        user_id_raw = payload.get("userId") or payload.get("user_id")
        display_name = payload.get("displayName") or payload.get("display_name")

        if user_id_raw is None or display_name is None:
            raise HTTPException(status_code=401, detail="Missing user identity data")

        mapped = {
            "user_id": user_id_raw,
            "customer_id": user_id_raw,
            "display_name": display_name,
        }

        return AuthenticatedCustomer.model_validate(mapped)

    async def get_cart(self, cookies: dict[str, str]) -> dict[str, Any] | None:
        payload = await self._request_json("GET", "/api/cart", cookies=cookies)
        if payload is None:
            return None
        if isinstance(payload, list):
            return {"items": payload} if payload else {"items": []}
        return payload

    async def get_recent_orders(self, cookies: dict[str, str]) -> list[dict[str, Any]]:
        payload = await self._request_json("GET", "/api/orders/mine", cookies=cookies)
        if payload is None:
            return []
        if isinstance(payload, list):
            return payload
        if isinstance(payload, dict):
            for key in ("items", "orders", "data"):
                value = payload.get(key)
                if isinstance(value, list):
                    return value
            return [payload]
        return []

    async def get_order_detail(
        self,
        order_id: str | None,
        cookies: dict[str, str],
    ) -> dict[str, Any] | None:
        if order_id is None:
            return None
        return await self._request_json(
            "GET",
            f"/api/orders/mine/{order_id}",
            cookies=cookies,
        )

    async def get_warranties(self, cookies: dict[str, str]) -> list[dict[str, Any]]:
        payload = await self._request_json("GET", "/api/warranties/mine", cookies=cookies)
        if payload is None:
            return []
        if isinstance(payload, list):
            return payload
        if isinstance(payload, dict):
            for key in ("items", "warranties", "data"):
                value = payload.get(key)
                if isinstance(value, list):
                    return value
            return [payload]
        return []
