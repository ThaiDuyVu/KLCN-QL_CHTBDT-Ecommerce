from collections.abc import Callable
from uuid import UUID

from app.features.knowledge.protocol import KnowledgeRetrievalService
from app.features.knowledge.repository import KnowledgeRepositoryError
from app.features.knowledge.service import KnowledgeUnavailable, build_source_references

from app.features.chat.models import SendMessageRequest
from app.features.product_advisor.service import (
    MockProductAdvisorService,
)
from app.features.query_understanding.service import (
    MockQueryUnderstandingService,
)
from app.features.response_generation.protocol import ResponseGenerationService
from app.features.response_generation.service import (
    MockResponseGenerationService,
)
from app.shared.contracts.context import (
    AuthenticatedCustomer,
    GroundedContext,
    QueryContext,
)
from app.shared.contracts.enums import Intent
from app.shared.contracts.knowledge import KnowledgeChunkMatch
from app.shared.contracts.product import ProductMatch
from app.shared.contracts.response import (
    ChatResponse,
    ProductCard,
)
from app.features.query_understanding.protocol import (
    QueryUnderstandingService,
)
from app.features.product_advisor.protocol import (
    ProductAdvisorService,
)


MOCK_USER_ID = UUID(
    "33333333-3333-3333-3333-333333333333"
)

MOCK_CUSTOMER_ID = UUID(
    "44444444-4444-4444-4444-444444444444"
)


class MockChatService:
    def __init__(
        self,
        query_understanding: QueryUnderstandingService | None = None,
        product_advisor: ProductAdvisorService | None = None,
        response_generation: ResponseGenerationService | None = None,
        knowledge_retrieval: KnowledgeRetrievalService | None = None,
        knowledge_top_k: int = 5,
        after_response: Callable[[QueryContext, ChatResponse, list[KnowledgeChunkMatch]], None] | None = None,
    ) -> None:
        if not 1 <= knowledge_top_k <= 20:
            raise ValueError("knowledge_top_k must be between 1 and 20")
        self.after_response = after_response
        self.knowledge_retrieval = knowledge_retrieval
        self.knowledge_top_k = knowledge_top_k
        self.query_understanding: QueryUnderstandingService = (
            query_understanding
            if query_understanding is not None
            else MockQueryUnderstandingService()
        )

        self.product_advisor: ProductAdvisorService = (
            product_advisor
            if product_advisor is not None
            else MockProductAdvisorService()
        )

        self.response_generation: ResponseGenerationService = (
            response_generation
            if response_generation is not None
            else MockResponseGenerationService()
        )

    def send_message(
        self,
        session_id: UUID,
        request: SendMessageRequest,
    ) -> ChatResponse:
        query_context = QueryContext(
            customer=AuthenticatedCustomer(
                user_id=MOCK_USER_ID,
                customer_id=MOCK_CUSTOMER_ID,
                display_name="Mock Customer",
            ),
            session_id=session_id,
            current_message=request.message,
            recent_messages=[],
        )

        query_plan = self.query_understanding.understand(
            query_context
        )

        product_matches: list[ProductMatch] = []
        product_cards: list[ProductCard] = []

        if (
            query_plan.intent in {Intent.PRODUCT_DISCOVERY, Intent.PRODUCT_DETAIL, Intent.PRODUCT_COMPARE}
            and query_plan.product_search is not None
        ):
            product_search = query_plan.product_search
            if query_plan.intent == Intent.PRODUCT_DETAIL:
                product_search = product_search.model_copy(update={"top_k": 1})
            elif query_plan.intent == Intent.PRODUCT_COMPARE:
                product_search = product_search.model_copy(update={"top_k": min(product_search.top_k, 3)})
            product_matches = self.product_advisor.search(
                product_search
            )

            product_cards = self.product_advisor.build_cards(
                product_matches
            )

        knowledge_chunks = []
        if query_plan.intent == Intent.KNOWLEDGE_QA and self.knowledge_retrieval is not None:
            try:
                knowledge_chunks = self.knowledge_retrieval.retrieve(
                    query_plan.semantic_query or query_context.current_message,
                    top_k=self.knowledge_top_k,
                )
            except (KnowledgeUnavailable, KnowledgeRepositoryError):
                # No evidence means the generator must give a safe fallback.
                knowledge_chunks = []

        grounded_context = GroundedContext(
            query_plan=query_plan,
            user_query=query_context.current_message,
            relevant_history=query_context.recent_messages,
            products=product_matches,
            knowledge_chunks=knowledge_chunks,
        )

        response = ChatResponse(
            session_id=session_id,
            message=self.response_generation.generate(
                grounded_context
            ),
            products=product_cards,
            sources=build_source_references(knowledge_chunks),
            suggested_questions=[
                "Bạn muốn tìm sản phẩm nào?",
            ],
        )

        if self.after_response is not None:
            self.after_response(query_context, response, knowledge_chunks)
        return response
