from uuid import uuid4

from app.features.query_understanding.service import (
    MockQueryUnderstandingService,
)
from app.shared.contracts.context import (
    AuthenticatedCustomer,
    QueryContext,
)
from app.shared.contracts.enums import Intent


def make_context(message: str) -> QueryContext:
    return QueryContext(
        customer=AuthenticatedCustomer(
            user_id=uuid4(),
            customer_id=uuid4(),
            display_name="Test Customer",
        ),
        session_id=uuid4(),
        current_message=message,
    )


def test_mock_query_understanding_detects_greeting() -> None:
    service = MockQueryUnderstandingService()

    plan = service.understand(
        make_context("Xin chào")
    )

    assert plan.intent == Intent.GREETING


def test_mock_query_understanding_detects_product_discovery() -> None:
    service = MockQueryUnderstandingService()

    plan = service.understand(
        make_context("Tôi cần laptop để lập trình")
    )

    assert plan.intent == Intent.PRODUCT_DISCOVERY
    assert plan.product_search is not None
    assert plan.product_search.category == "Laptop"


def test_mock_query_understanding_returns_unknown() -> None:
    service = MockQueryUnderstandingService()

    plan = service.understand(
        make_context("abc xyz")
    )

    assert plan.intent == Intent.UNKNOWN