from typing import Protocol

from app.shared.contracts.context import QueryContext
from app.shared.contracts.query import QueryPlan


class QueryUnderstandingService(Protocol):
    def understand(
        self,
        context: QueryContext,
    ) -> QueryPlan:
        ...