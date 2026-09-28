from typing import Protocol

from app.shared.contracts.product import ProductMatch
from app.shared.contracts.query import ProductSearchPlan
from app.shared.contracts.response import ProductCard


class ProductAdvisorService(Protocol):
    def search(
        self,
        plan: ProductSearchPlan,
    ) -> list[ProductMatch]:
        ...

    def build_cards(
        self,
        matches: list[ProductMatch],
    ) -> list[ProductCard]:
        ...