from uuid import UUID

from app.shared.contracts.product import (
    EligibleVariant,
    ProductMatch,
)
from app.shared.contracts.query import ProductSearchPlan
from app.shared.contracts.response import ProductCard


MOCK_PRODUCT_ID = UUID(
    "11111111-1111-1111-1111-111111111111"
)

MOCK_VARIANT_ID = UUID(
    "22222222-2222-2222-2222-222222222222"
)


class MockProductAdvisorService:
    def search(
        self,
        plan: ProductSearchPlan,
    ) -> list[ProductMatch]:
        variant = EligibleVariant(
            variant_id=MOCK_VARIANT_ID,
            product_id=MOCK_PRODUCT_ID,
            sku="DEV-DELL-512",
            original_price=17_990_000,
            effective_price=15_990_000,
            ram="16GB",
            storage="512GB",
            color="Silver",
            available_quantity=4,
            warranty_months=24,
        )

        return [
            ProductMatch(
                product_id=MOCK_PRODUCT_ID,
                semantic_score=0.89,
                ranking_score=0.93,
                eligible_variants=[variant],
                reasons=[
                    "Phù hợp nhu cầu lập trình",
                    "Nằm trong nhóm laptop phù hợp",
                ],
            )
        ]

    def build_cards(
        self,
        matches: list[ProductMatch],
    ) -> list[ProductCard]:
        cards: list[ProductCard] = []

        for match in matches:
            if match.product_id != MOCK_PRODUCT_ID:
                continue

            variant = (
                match.eligible_variants[0]
                if match.eligible_variants
                else None
            )

            cards.append(
                ProductCard(
                    product_id=match.product_id,
                    product_name="Dell Inspiron 15",
                    variant_id=(
                        variant.variant_id
                        if variant is not None
                        else None
                    ),
                    sku=(
                        variant.sku
                        if variant is not None
                        else None
                    ),
                    original_price=(
                        variant.original_price
                        if variant is not None
                        else None
                    ),
                    effective_price=(
                        variant.effective_price
                        if variant is not None
                        else None
                    ),
                    ram=(
                        variant.ram
                        if variant is not None
                        else None
                    ),
                    storage=(
                        variant.storage
                        if variant is not None
                        else None
                    ),
                    color=(
                        variant.color
                        if variant is not None
                        else None
                    ),
                    available_quantity=(
                        variant.available_quantity
                        if variant is not None
                        else None
                    ),
                    warranty_months=(
                        variant.warranty_months
                        if variant is not None
                        else None
                    ),
                    image_url="/api/media/products/dell.jpg",
                    reasons=match.reasons,
                )
            )

        return cards