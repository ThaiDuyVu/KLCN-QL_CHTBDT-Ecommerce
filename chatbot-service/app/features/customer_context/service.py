"""Map existing backend camelCase DTOs into the locked shared contracts."""
from pydantic import ValidationError
from fastapi import HTTPException

from app.shared.backend.client import BackendClient
from app.shared.contracts.context import (
    CartContext, CartItemContext, CustomerContext, OrderContext, PaymentContext,
    WarrantyContext, WarrantyTicketContext,
)


def payment_context(row: dict) -> PaymentContext:
    return PaymentContext(payment_id=row['paymentId'], payment_method=row['paymentMethod'],
                          status=row['status'], amount=row['amount'])


def order_context(row: dict) -> OrderContext:
    return OrderContext(order_id=row['orderId'], order_code=row['orderCode'], order_date=row['orderDate'],
                        status=row['status'], total_amount=row['totalAmount'],
                        payment=payment_context(row['payment']) if row.get('payment') else None)


def warranty_context(row: dict) -> WarrantyContext:
    return WarrantyContext(warranty_id=row['warrantyId'], order_id=row['orderId'],
        product_name=row['productName'], sku=row['sku'], serial_number=row.get('serialNumber'),
        imei_numbers=row.get('imeiNumbers') or [], start_date=row['startDate'], end_date=row['endDate'],
        status=row['status'], eligible=row['eligible'])


def ticket_context(row: dict) -> WarrantyTicketContext:
    return WarrantyTicketContext(ticket_id=row['ticketId'], ticket_code=row['ticketCode'],
        warranty_id=row['warrantyId'], product_name=row['productName'], sku=row['sku'],
        issue_description=row['issueDescription'], resolution_note=row.get('resolutionNote'),
        status=row['status'], created_at=row['createdAt'], resolved_at=row.get('resolvedAt'))


class CustomerContextService:
    def __init__(self, backend_client: BackendClient | None = None):
        self.backend_client = backend_client

    async def load_context(self, intent: str, cookies: dict[str, str]) -> CustomerContext:
        context = CustomerContext()
        if not cookies or self.backend_client is None:
            return context
        try:
            if intent == 'CART_STATUS':
                row = await self.backend_client.get_cart(cookies)
                if row:
                    context.cart = CartContext(cart_id=row['cartId'], warehouse_id=row.get('warehouseId'),
                        warehouse_name=row.get('warehouseName'), subtotal=row['subtotal'],
                        items=[CartItemContext(variant_id=i['variantId'], product_name=i['productName'],
                            sku=i['sku'], quantity=i['quantity'], effective_price=i['effectivePrice'],
                            available_quantity=i.get('availableQuantity')) for i in row['items']])
            elif intent in {'ORDER_STATUS', 'PAYMENT_STATUS'}:
                rows = await self.backend_client.get_recent_orders(cookies)
                if intent == 'PAYMENT_STATUS' and rows:
                    detail = await self.backend_client.get_order_detail(str(rows[0]['orderId']), cookies)
                    rows = [detail] if detail else []
                context.recent_orders = [order_context(row) for row in rows]
            elif intent == 'WARRANTY_STATUS':
                context.warranties = [warranty_context(row) for row in await self.backend_client.get_warranties(cookies)]
            elif intent == 'WARRANTY_TICKET_STATUS':
                context.warranty_tickets = [ticket_context(row) for row in await self.backend_client.get_warranty_tickets(cookies)]
        except (KeyError, TypeError, ValueError, ValidationError) as exc:
            raise HTTPException(status_code=502, detail='Dữ liệu khách hàng từ backend không đúng cấu trúc mong đợi.') from exc
        return context
