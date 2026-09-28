from decimal import Decimal
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.shared.contracts.product import (
    EligibleVariant,
    ProductCandidate,
    ProductMatch,
)


def test_product_candidate_accepts_semantic_result() -> None:
    product_id = uuid4()

    candidate = ProductCandidate(
        product_id=product_id,
        semantic_score=0.89,
    )

    assert candidate.product_id == product_id
    assert candidate.semantic_score == 0.89


def test_eligible_variant_accepts_business_data() -> None:
    product_id = uuid4()
    variant_id = uuid4()

    variant = EligibleVariant(
        variant_id=variant_id,
        product_id=product_id,
        sku="DEV-DELL-512",
        original_price=17_990_000,
        effective_price=15_990_000,
        ram="16GB",
        storage="512GB",
        available_quantity=4,
        warranty_months=24,
    )

    assert variant.variant_id == variant_id
    assert variant.original_price == Decimal("17990000")
    assert variant.effective_price == Decimal("15990000")
    assert variant.available_quantity == 4


def test_eligible_variant_rejects_negative_stock() -> None:
    with pytest.raises(ValidationError):
        EligibleVariant(
            variant_id=uuid4(),
            product_id=uuid4(),
            sku="DEV-DELL-512",
            original_price=17_990_000,
            effective_price=15_990_000,
            available_quantity=-1,
        )


def test_product_match_accepts_ranked_product() -> None:
    product_id = uuid4()

    variant = EligibleVariant(
        variant_id=uuid4(),
        product_id=product_id,
        sku="DEV-DELL-512",
        original_price=17_990_000,
        effective_price=15_990_000,
    )

    match = ProductMatch(
        product_id=product_id,
        semantic_score=0.89,
        ranking_score=0.93,
        eligible_variants=[variant],
        reasons=[
            "Trong ngân sách",
            "Phù hợp nhu cầu lập trình",
        ],
    )

    assert len(match.eligible_variants) == 1
    assert match.ranking_score == 0.93
    assert len(match.reasons) == 2


def test_product_contracts_reject_unknown_fields() -> None:
    with pytest.raises(ValidationError):
        ProductCandidate(
            product_id=uuid4(),
            semantic_score=0.9,
            product_name="Dell Inspiron 15",
        )

def test_product_match_rejects_variant_from_other_product() -> None:
    match_product_id = uuid4()
    other_product_id = uuid4()

    variant = EligibleVariant(
        variant_id=uuid4(),
        product_id=other_product_id,
        sku="OTHER-SKU",
        original_price=10_000_000,
        effective_price=9_000_000,
    )

    with pytest.raises(ValidationError):
        ProductMatch(
            product_id=match_product_id,
            eligible_variants=[variant],
        )