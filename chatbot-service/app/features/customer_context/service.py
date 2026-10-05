from __future__ import annotations

from app.shared.backend.client import BackendClient
from app.shared.contracts.context import CustomerContext


class CustomerContextService:
    def __init__(self, backend_client: BackendClient | None = None) -> None:
        self.backend_client = backend_client

    async def load_context(self, intent: str, cookies: dict[str, str]) -> CustomerContext:
        context = CustomerContext()

        if not cookies or self.backend_client is None:
            return context

        normalized_intent = (intent or "").upper()

        if normalized_intent in {"PRODUCT_DISCOVERY", "CART_STATUS"}:
            cart = await self.backend_client.get_cart(cookies)
            if cart is not None:
                context.cart = cart
            return context

        if normalized_intent == "ORDER_STATUS":
            orders = await self.backend_client.get_recent_orders(cookies)
            if orders:
                context.recent_orders = orders
            return context

        if normalized_intent == "PAYMENT_STATUS":
            orders = await self.backend_client.get_recent_orders(cookies)
            if not orders:
                return context

            latest_order = orders[0]
            order_id = latest_order.get("id") or latest_order.get("orderId")
            order_detail = await self.backend_client.get_order_detail(str(order_id), cookies)
            if order_detail is None:
                return context

            payment = order_detail.get("payment")
            if payment is not None:
                context.payment = payment
            return context

        if normalized_intent == "WARRANTY_STATUS":
            warranties = await self.backend_client.get_warranties(cookies)
            if warranties:
                context.warranties = warranties
            return context

        if normalized_intent in {"GREETING", "UNKNOWN"}:
            return CustomerContext()

        return context
