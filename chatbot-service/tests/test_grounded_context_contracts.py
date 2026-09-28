from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.shared.contracts.context import (
    CustomerContext,
    GroundedContext,
)
from app.shared.contracts.enums import Intent
from app.shared.contracts.knowledge import (
    KnowledgeChunkMatch,
    KnowledgeSource,
)
from app.shared.contracts.product import (
    EligibleVariant,
    ProductMatch,
)
from app.shared.contracts.query import (
    ProductSearchPlan,
    QueryPlan,
)


def test_grounded_context_accepts_minimal_context() -> None:
    context = GroundedContext(
        query_plan=QueryPlan(
            intent=Intent.HELP,
        ),
        user_query="Bạn có thể giúp gì?",
    )

    assert context.user_query == "Bạn có thể giúp gì?"
    assert context.products == []
    assert context.knowledge_chunks == []
    assert context.customer_context is None


def test_grounded_context_accepts_product_matches() -> None:
    product_id = uuid4()

    variant = EligibleVariant(
        variant_id=uuid4(),
        product_id=product_id,
        sku="DEV-DELL-512",
        original_price=17_990_000,
        effective_price=15_990_000,
    )

    product = ProductMatch(
        product_id=product_id,
        semantic_score=0.89,
        ranking_score=0.93,
        eligible_variants=[variant],
        reasons=["Phù hợp nhu cầu lập trình"],
    )

    context = GroundedContext(
        query_plan=QueryPlan(
            intent=Intent.PRODUCT_DISCOVERY,
            product_search=ProductSearchPlan(
                category="Laptop",
            ),
        ),
        user_query="Gợi ý laptop để lập trình",
        products=[product],
    )

    assert len(context.products) == 1
    assert context.products[0].product_id == product_id


def test_grounded_context_accepts_knowledge_matches() -> None:
    source = KnowledgeSource(
        document_id=uuid4(),
        title="Chính sách đổi trả",
    )

    chunk = KnowledgeChunkMatch(
        chunk_id=uuid4(),
        chunk_index=0,
        content="Nội dung chính sách đổi trả",
        similarity_score=0.88,
        source=source,
    )

    context = GroundedContext(
        query_plan=QueryPlan(
            intent=Intent.KNOWLEDGE_QA,
            semantic_query="chính sách đổi trả",
        ),
        user_query="Chính sách đổi trả thế nào?",
        knowledge_chunks=[chunk],
    )

    assert len(context.knowledge_chunks) == 1
    assert context.knowledge_chunks[0].source.title == "Chính sách đổi trả"


def test_grounded_context_accepts_customer_context() -> None:
    customer_context = CustomerContext()

    context = GroundedContext(
        query_plan=QueryPlan(
            intent=Intent.ORDER_STATUS,
        ),
        user_query="Đơn hàng của tôi thế nào?",
        customer_context=customer_context,
    )

    assert context.customer_context is not None


def test_grounded_context_rejects_empty_user_query() -> None:
    with pytest.raises(ValidationError):
        GroundedContext(
            query_plan=QueryPlan(
                intent=Intent.HELP,
            ),
            user_query="",
        )