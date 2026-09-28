from decimal import Decimal
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.shared.contracts.response import (
    ChatResponse,
    ProductCard,
    SourceReference,
)


def test_product_card_accepts_customer_facing_product() -> None:
    product = ProductCard(
        product_id=uuid4(),
        product_name="Dell Inspiron 15",
        variant_id=uuid4(),
        sku="DEV-DELL-512",
        original_price=17_990_000,
        effective_price=15_990_000,
        ram="16GB",
        storage="512GB",
        available_quantity=4,
        warranty_months=24,
        image_url="/api/media/products/dell.jpg",
        reasons=["Phù hợp nhu cầu lập trình"],
    )

    assert product.product_name == "Dell Inspiron 15"
    assert product.effective_price == Decimal("15990000")
    assert product.available_quantity == 4


def test_product_card_allows_product_without_selected_variant() -> None:
    product = ProductCard(
        product_id=uuid4(),
        product_name="Dell Inspiron 15",
    )

    assert product.variant_id is None
    assert product.effective_price is None


def test_source_reference_accepts_knowledge_source() -> None:
    document_id = uuid4()
    chunk_id = uuid4()

    source = SourceReference(
        document_id=document_id,
        chunk_id=chunk_id,
        title="Chính sách đổi trả",
        source="internal-policy",
    )

    assert source.document_id == document_id
    assert source.chunk_id == chunk_id


def test_chat_response_accepts_structured_output() -> None:
    product = ProductCard(
        product_id=uuid4(),
        product_name="Dell Inspiron 15",
    )

    source = SourceReference(
        document_id=uuid4(),
        title="Hướng dẫn mua hàng",
    )

    response = ChatResponse(
        session_id=uuid4(),
        message="Mình tìm thấy một sản phẩm phù hợp.",
        products=[product],
        sources=[source],
        suggested_questions=[
            "Bạn muốn so sánh với sản phẩm khác không?"
        ],
    )

    assert len(response.products) == 1
    assert len(response.sources) == 1
    assert len(response.suggested_questions) == 1


def test_chat_response_allows_text_only_response() -> None:
    response = ChatResponse(
        session_id=uuid4(),
        message="Xin chào! Mình có thể giúp bạn tìm sản phẩm.",
    )

    assert response.products == []
    assert response.sources == []
    assert response.suggested_questions == []


def test_response_contract_rejects_unknown_fields() -> None:
    with pytest.raises(ValidationError):
        ChatResponse(
            session_id=uuid4(),
            message="Hello",
            intent="GREETING",
        )