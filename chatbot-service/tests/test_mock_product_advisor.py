from app.features.product_advisor.service import (
    MockProductAdvisorService,
)
from app.shared.contracts.query import ProductSearchPlan


def test_mock_product_advisor_returns_product_match() -> None:
    service = MockProductAdvisorService()

    matches = service.search(
        ProductSearchPlan(
            category="Laptop",
            semantic_query="laptop để lập trình",
        )
    )

    assert len(matches) == 1
    assert len(matches[0].eligible_variants) == 1
    assert matches[0].eligible_variants[0].ram == "16GB"


def test_mock_product_advisor_builds_product_card() -> None:
    service = MockProductAdvisorService()

    matches = service.search(
        ProductSearchPlan(
            category="Laptop",
        )
    )

    cards = service.build_cards(matches)

    assert len(cards) == 1
    assert cards[0].product_name == "Dell Inspiron 15"
    assert cards[0].effective_price == 15_990_000