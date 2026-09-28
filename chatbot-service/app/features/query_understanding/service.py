from app.shared.contracts.context import QueryContext
from app.shared.contracts.enums import Intent
from app.shared.contracts.query import (
    ProductSearchPlan,
    QueryPlan,
)


class MockQueryUnderstandingService:
    def understand(
        self,
        context: QueryContext,
    ) -> QueryPlan:
        message = context.current_message
        normalized = message.strip().lower()

        greetings = {
            "xin chào",
            "chào",
            "hello",
            "hi",
        }

        if normalized in greetings:
            return QueryPlan(
                intent=Intent.GREETING,
            )

        if "laptop" in normalized:
            return QueryPlan(
                intent=Intent.PRODUCT_DISCOVERY,
                product_search=ProductSearchPlan(
                    category="Laptop",
                    semantic_query=message,
                ),
            )

        return QueryPlan(
            intent=Intent.UNKNOWN,
        )