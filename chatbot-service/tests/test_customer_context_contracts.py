from datetime import date, datetime, timezone
from decimal import Decimal
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.shared.contracts.context import (
    CartContext,
    CartItemContext,
    CustomerContext,
    OrderContext,
    PaymentContext,
    WarrantyContext,
)


def test_customer_context_is_empty_by_default() -> None:
    context = CustomerContext()

    assert context.cart is None
    assert context.recent_orders == []
    assert context.warranties == []
    assert context.warranty_tickets == []


def test_cart_context_accepts_items() -> None:
    item = CartItemContext(
        variant_id=uuid4(),
        product_name="Dell Inspiron 15",
        sku="DEV-DELL-512",
        quantity=1,
        effective_price=15_990_000,
        available_quantity=4,
    )

    cart = CartContext(
        cart_id=uuid4(),
        warehouse_id=uuid4(),
        warehouse_name="Chi nhánh 1",
        items=[item],
        subtotal=15_990_000,
    )

    assert len(cart.items) == 1
    assert cart.subtotal == Decimal("15990000")


def test_cart_item_rejects_invalid_quantity() -> None:
    with pytest.raises(ValidationError):
        CartItemContext(
            variant_id=uuid4(),
            product_name="Dell Inspiron 15",
            sku="DEV-DELL-512",
            quantity=0,
            effective_price=15_990_000,
        )


def test_order_context_accepts_payment() -> None:
    payment = PaymentContext(
        payment_id=uuid4(),
        payment_method="VNPAY",
        status="PAID",
        amount=15_990_000,
    )

    order = OrderContext(
        order_id=uuid4(),
        order_code="ORD-001",
        order_date=datetime.now(timezone.utc),
        status="CONFIRMED",
        total_amount=15_990_000,
        payment=payment,
    )

    assert order.payment is not None
    assert order.payment.status == "PAID"


def test_warranty_context_accepts_backend_summary() -> None:
    warranty = WarrantyContext(
        warranty_id=uuid4(),
        order_id=uuid4(),
        product_name="Dell Inspiron 15",
        sku="DEV-DELL-512",
        serial_number="SN001",
        start_date=date(2026, 1, 1),
        end_date=date(2028, 1, 1),
        status="ACTIVE",
        eligible=True,
    )

    assert warranty.status == "ACTIVE"
    assert warranty.eligible is True


def test_customer_context_rejects_unknown_fields() -> None:
    with pytest.raises(ValidationError):
        CustomerContext(
            loyalty_point=100,
        )