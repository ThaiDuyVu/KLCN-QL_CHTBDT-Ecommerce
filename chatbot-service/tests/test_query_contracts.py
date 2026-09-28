from decimal import Decimal

import pytest
from pydantic import ValidationError

from app.shared.contracts.query import ProductSearchPlan, QueryPlan
from app.shared.contracts.enums import Intent, RequestedField


def test_product_search_plan_accepts_valid_filters() -> None:
    plan = ProductSearchPlan(
        category="Laptop",
        brands=["Dell"],
        max_price=20_000_000,
        min_ram_gb=16,
        semantic_query="phù hợp lập trình Java",
    )

    assert plan.category == "Laptop"
    assert plan.brands == ["Dell"]
    assert plan.max_price == Decimal("20000000")
    assert plan.min_ram_gb == 16
    assert plan.top_k == 5


def test_product_search_plan_rejects_invalid_top_k() -> None:
    with pytest.raises(ValidationError):
        ProductSearchPlan(top_k=0)


def test_product_search_plan_rejects_unknown_fields() -> None:
    with pytest.raises(ValidationError):
        ProductSearchPlan(brand_name="Dell")

def test_product_search_plan_rejects_reversed_price_range() -> None:
    with pytest.raises(ValidationError):
        ProductSearchPlan(
            min_price=30_000_000,
            max_price=20_000_000,
        )

def test_query_plan_accepts_product_discovery() -> None:
    plan = QueryPlan(
        intent=Intent.PRODUCT_DISCOVERY,
        requested_fields=[
            RequestedField.PRICE,
            RequestedField.STOCK,
        ],
        product_search=ProductSearchPlan(
            category="Laptop",
            brands=["Dell"],
            max_price=20_000_000,
            semantic_query="phù hợp lập trình Java",
        ),
    )

    assert plan.intent == Intent.PRODUCT_DISCOVERY
    assert plan.product_search is not None
    assert plan.product_search.brands == ["Dell"]
    assert RequestedField.PRICE in plan.requested_fields


def test_query_plan_accepts_knowledge_query() -> None:
    plan = QueryPlan(
        intent=Intent.KNOWLEDGE_QA,
        semantic_query="thời hạn chính sách đổi trả",
    )

    assert plan.intent == Intent.KNOWLEDGE_QA
    assert plan.semantic_query == "thời hạn chính sách đổi trả"
    assert plan.product_search is None


def test_query_plan_parses_enum_strings() -> None:
    plan = QueryPlan(
        intent="PRODUCT_DETAIL",
        requested_fields=["PRICE", "STOCK"],
    )

    assert plan.intent == Intent.PRODUCT_DETAIL
    assert plan.requested_fields == [
        RequestedField.PRICE,
        RequestedField.STOCK,
    ]


def test_query_plan_rejects_unknown_fields() -> None:
    with pytest.raises(ValidationError):
        QueryPlan(
            intent=Intent.HELP,
            unknown_field="value",
        )

def test_query_plan_requires_product_search_for_discovery() -> None:
    with pytest.raises(ValidationError):
        QueryPlan(
            intent=Intent.PRODUCT_DISCOVERY,
        )


def test_query_plan_allows_product_detail_without_product_search() -> None:
    plan = QueryPlan(
        intent=Intent.PRODUCT_DETAIL,
        references={
            "product_name": "Dell Inspiron 15",
        },
    )

    assert plan.intent == Intent.PRODUCT_DETAIL
    assert plan.product_search is None