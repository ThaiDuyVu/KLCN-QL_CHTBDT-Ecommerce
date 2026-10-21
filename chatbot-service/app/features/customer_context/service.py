"""Map existing backend camelCase DTOs into the locked shared contracts."""
from app.shared.tracing import traced, event
from datetime import datetime
from decimal import Decimal, InvalidOperation
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


NO_OWNED_ORDERS_MESSAGE = 'Tài khoản của bạn chưa có đơn hàng nào.'


class NoOwnedOrders(Exception):
    """The owned order list was returned successfully and is empty (not an error or 404)."""


# Same sentence as the existing empty-cart branch in OllamaResponseGenerationService.generate.
EMPTY_CART_MESSAGE = 'Giỏ hàng của bạn hiện đang trống.'


class VerifiedEmptyCart(Exception):
    """GET /api/cart returned 200 for a customer who never created a cart (cartId=null, no items)."""


def never_created_cart(row: dict) -> bool:
    # CartServiceImpl.get never creates a cart; it serializes cartId=null, items=[], subtotal=0.
    # Anything else without a cart identity stays malformed (502).
    if 'cartId' not in row or row['cartId'] is not None or row.get('items') != []:
        return False
    try:
        return Decimal(str(row.get('subtotal'))) == 0
    except (InvalidOperation, ValueError):
        return False


class CustomerContextService:
    def __init__(self, backend_client: BackendClient | None = None):
        self.backend_client = backend_client

    @traced('customer.context', ('intent',))
    async def load_context(self, intent: str, cookies: dict[str, str], *, order_code: str | None = None, latest: bool = False) -> CustomerContext:
        context = CustomerContext()
        if not cookies or self.backend_client is None:
            return context
        try:
            if intent == 'CART_STATUS':
                row = await self.backend_client.get_cart(cookies)
                if isinstance(row, dict) and never_created_cart(row):
                    event('customer.cart_state', {'state': 'never_created', 'items': 0})
                    raise VerifiedEmptyCart()
                if row:
                    context.cart = CartContext(cart_id=row['cartId'], warehouse_id=row.get('warehouseId'),
                        warehouse_name=row.get('warehouseName'), subtotal=row['subtotal'],
                        items=[CartItemContext(variant_id=i['variantId'], product_name=i['productName'],
                            sku=i['sku'], quantity=i['quantity'], effective_price=i['effectivePrice'],
                            available_quantity=i.get('availableQuantity')) for i in row['items']])
            elif intent in {'ORDER_STATUS', 'PAYMENT_STATUS'}:
                rows = await self.backend_client.get_recent_orders(cookies)
                if rows == [] and order_code is None:
                    event('customer.order_selection', {'selection': 'none', 'matched': 0, 'owned_list': 'verified_empty'})
                    raise NoOwnedOrders()
                # Unknown (404/204/unexpected shape) stays "no evidence", never "no orders".
                rows = rows or []
                # The owned endpoint defines the candidate scope. No arbitrary ID lookup.
                rows = sorted(rows, key=lambda r: (datetime.fromisoformat(r['orderDate'].replace('Z', '+00:00')), str(r['orderId'])), reverse=True)
                if order_code is not None:
                    # Displayed codes are upper-case UUIDs; a typed code may differ only in case.
                    rows = [r for r in rows if r['orderCode'].casefold() == order_code.casefold()]
                    if len(rows) > 1:
                        raise ValueError('Ambiguous owned order code')
                elif latest or intent == 'PAYMENT_STATUS':
                    rows = rows[:1]
                if intent == 'PAYMENT_STATUS':
                    details = []
                    for row in rows:
                        detail = await self.backend_client.get_order_detail(str(row['orderId']), cookies)
                        if detail is not None:
                            if detail['orderId'] != row['orderId'] or detail['orderCode'] != row['orderCode']:
                                raise ValueError('Order detail identity changed')
                            details.append(detail)
                    rows = details
                event('customer.order_selection', {'selection': 'exact_code' if order_code else 'latest' if latest or intent == 'PAYMENT_STATUS' else 'recent', 'matched': len(rows), 'order_ids': [r['orderId'] for r in rows]})
                context.recent_orders = [order_context(row) for row in rows]
            elif intent == 'WARRANTY_STATUS':
                context.warranties = [warranty_context(row) for row in await self.backend_client.get_warranties(cookies)]
            elif intent == 'WARRANTY_TICKET_STATUS':
                context.warranty_tickets = [ticket_context(row) for row in await self.backend_client.get_warranty_tickets(cookies)]
        except (KeyError, TypeError, ValueError, ValidationError) as exc:
            raise HTTPException(status_code=502, detail='Dữ liệu khách hàng từ backend không đúng cấu trúc mong đợi.') from exc
        return context
