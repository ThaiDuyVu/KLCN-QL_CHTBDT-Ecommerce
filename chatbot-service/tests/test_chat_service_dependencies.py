from uuid import uuid4

from app.features.chat.models import SendMessageRequest
from app.features.chat.service import MockChatService
from app.features.product_advisor.protocol import ProductAdvisorService
from app.features.response_generation.protocol import ResponseGenerationService
from app.shared.contracts.context import GroundedContext, QueryContext
from app.shared.contracts.product import ProductMatch
from app.shared.contracts.response import ProductCard
from app.shared.contracts.enums import Intent
from app.shared.contracts.query import ProductSearchPlan, QueryPlan


class StubQueryUnderstandingService:
    def understand(
        self,
        context: QueryContext,
    ) -> QueryPlan:
        return QueryPlan(
            intent=Intent.UNKNOWN,
        )


def test_chat_service_accepts_query_understanding_dependency() -> None:
    service = MockChatService(
        query_understanding=StubQueryUnderstandingService(),
    )

    response = service.send_message(
        session_id=uuid4(),
        request=SendMessageRequest(
            message="Xin chào",
        ),
    )

    assert response.message == "Mình chưa hiểu yêu cầu của bạn."


class StubProductAdvisorService:
    def __init__(self) -> None:
        self.searched_plan: ProductSearchPlan | None = None
        self.card_matches: list[ProductMatch] | None = None

    def __bool__(self) -> bool:
        return False

    def search(self, plan: ProductSearchPlan) -> list[ProductMatch]:
        self.searched_plan = plan
        return []

    def build_cards(self, matches: list[ProductMatch]) -> list[ProductCard]:
        self.card_matches = matches
        return []


def test_chat_service_accepts_product_advisor_dependency() -> None:
    stub = StubProductAdvisorService()
    dependency: ProductAdvisorService = stub
    service = MockChatService(product_advisor=dependency)

    response = service.send_message(
        session_id=uuid4(),
        request=SendMessageRequest(message="Tôi cần laptop để lập trình"),
    )

    assert stub.searched_plan is not None
    assert stub.card_matches == []
    assert response.products == []
    assert response.message == "Mình chưa tìm thấy sản phẩm phù hợp."


class StubResponseGenerationService:
    def __init__(self) -> None:
        self.context: GroundedContext | None = None

    def __bool__(self) -> bool:
        return False

    def generate(self, context: GroundedContext) -> str:
        self.context = context
        return "Injected response generation was used."


def test_chat_service_accepts_response_generation_dependency() -> None:
    stub = StubResponseGenerationService()
    dependency: ResponseGenerationService = stub
    service = MockChatService(response_generation=dependency)

    response = service.send_message(
        session_id=uuid4(),
        request=SendMessageRequest(message="Tôi cần laptop để lập trình"),
    )

    assert response.message == "Injected response generation was used."
    assert stub.context is not None
    assert stub.context.query_plan.intent == Intent.PRODUCT_DISCOVERY
    assert len(stub.context.products) == 1
    assert len(response.products) == 1
