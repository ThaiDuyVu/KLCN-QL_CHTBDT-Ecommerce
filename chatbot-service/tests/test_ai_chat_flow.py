from uuid import uuid4

from fastapi.testclient import TestClient

from app.features.chat.dependencies import build_chat_service
from app.features.chat.models import SendMessageRequest
from app.features.chat.service import MockChatService
from app.features.knowledge.repository import KnowledgeRepositoryError
from app.features.knowledge.service import KnowledgeUnavailable
from app.features.query_understanding.ollama_service import OllamaQueryUnderstandingService
from app.features.response_generation.ollama_service import INSUFFICIENT, OllamaResponseGenerationService
from app.shared.config import Settings
from tests.test_ai_services import FakeChat, knowledge_match


class FakeRetrieval:
    def __init__(self, error=None):
        self.calls = []
        self.match = knowledge_match()
        self.error = error

    def retrieve(self, query, *, top_k):
        self.calls.append((query, top_k))
        if self.error:
            raise self.error
        return [self.match]


def test_existing_pipeline_routes_knowledge_and_builds_sources():
    retrieval = FakeRetrieval()
    query = FakeChat('{"intent":"KNOWLEDGE_QA","semantic_query":"đổi trả"}')
    response = FakeChat("Chính sách đổi trả trong 7 ngày.")
    service = MockChatService(query_understanding=OllamaQueryUnderstandingService(query), response_generation=OllamaResponseGenerationService(response), knowledge_retrieval=retrieval, knowledge_top_k=3)
    result = service.send_message(uuid4(), SendMessageRequest(message="Cho tôi biết chính sách đổi trả"))
    assert result.message == "Chính sách đổi trả trong 7 ngày."
    assert retrieval.calls == [("đổi trả", 3)]
    assert result.sources[0].chunk_id == retrieval.match.chunk_id
    assert result.products == []


def test_retrieval_failure_returns_safe_response():
    for error in (KnowledgeUnavailable("offline"), KnowledgeRepositoryError("offline")):
        service = MockChatService(query_understanding=OllamaQueryUnderstandingService(FakeChat('{"intent":"KNOWLEDGE_QA"}')), response_generation=OllamaResponseGenerationService(FakeChat()), knowledge_retrieval=FakeRetrieval(error))
        result = service.send_message(uuid4(), SendMessageRequest(message="Chính sách?"))
        assert result.message == INSUFFICIENT
        assert result.sources == []


def test_ai_factory_does_not_present_mock_products_as_evidence():
    service, client = build_chat_service(Settings(_env_file=None, chatbot_ai_enabled=True))
    try:
        service.query_understanding = OllamaQueryUnderstandingService(FakeChat('{"intent":"PRODUCT_DISCOVERY","product_search":{}}'))
        result = service.send_message(uuid4(), SendMessageRequest(message="Laptop?"))
        assert result.products == []
        assert "chưa tìm thấy sản phẩm phù hợp" in result.message
    finally:
        client.close()


def test_mock_factory_and_app_lifespan_still_work():
    from app.main import app
    service, client = build_chat_service(Settings(_env_file=None))
    assert client is None
    assert isinstance(service, MockChatService)
    from app.features.chat.router import get_authenticated_customer
    from app.features.customer_context.models import AuthenticatedCustomer
    app.dependency_overrides[get_authenticated_customer] = lambda: AuthenticatedCustomer(
        user_id=uuid4(), customer_id=uuid4(), display_name="Test User",
    )
    try:
        with TestClient(app) as http:
            response = http.post(f"/api/v1/chat/sessions/{uuid4()}/messages", json={"message": "xin chào"})
            assert response.status_code == 200
            assert response.json()["sources"] == []
    finally:
        app.dependency_overrides.pop(get_authenticated_customer, None)


def test_audit_hook_runs_after_generation_with_persisted_history_id():
    from tests.test_knowledge_services import MemoryRepository, FakeEmbeddings
    from app.features.knowledge.service import PgvectorKnowledgeRetrievalService
    retrieval = FakeRetrieval()
    repo = MemoryRepository([])
    audit = PgvectorKnowledgeRetrievalService(repo, FakeEmbeddings(), status="PUBLISHED")
    persisted = []
    history_id = uuid4()
    session_id = uuid4()

    def persist_then_audit(context, response, matches):
        assert response.message == "Grounded answer"
        assert context.current_message == "Chính sách?"
        assert repo.audits == []
        persisted.append((history_id, response.message))
        audit.record_retrievals(history_id, matches)

    service = MockChatService(
        query_understanding=OllamaQueryUnderstandingService(FakeChat('{"intent":"KNOWLEDGE_QA"}')),
        response_generation=OllamaResponseGenerationService(FakeChat("Grounded answer")),
        knowledge_retrieval=retrieval, after_response=persist_then_audit,
    )
    service.send_message(session_id, SendMessageRequest(message="Chính sách?"))
    assert persisted == [(history_id, "Grounded answer")]
    assert repo.audits == [(history_id, [retrieval.match])]
    assert history_id != session_id


def test_authenticated_rag_preserves_history_sources_and_persisted_response():
    from app.features.conversation.repository import InMemoryConversationRepository
    from app.features.conversation.service import ConversationService
    from app.features.customer_context.models import AuthenticatedCustomer
    from app.features.customer_context.service import CustomerContextService
    conversation = ConversationService(InMemoryConversationRepository())
    customer = AuthenticatedCustomer(user_id=uuid4(), customer_id=uuid4(), display_name="Customer")
    session_id = uuid4()
    conversation.create_session(customer.customer_id, session_id)
    conversation.add_message(session_id, "USER", "Tôi muốn biết chính sách", customer.customer_id)
    model = FakeChat('{"intent":"KNOWLEDGE_QA","semantic_query":"đổi trả"}')
    service = MockChatService(
        conversation_service=conversation,
        customer_context_service=CustomerContextService(),
        query_understanding=OllamaQueryUnderstandingService(model),
        response_generation=OllamaResponseGenerationService(FakeChat("Đổi trả trong 7 ngày.")),
        knowledge_retrieval=FakeRetrieval(),
    )
    response = service.send_message(session_id, SendMessageRequest(message="Còn đổi trả?"), customer=customer)
    assert response.sources
    assert response.message == "Đổi trả trong 7 ngày."
    history = conversation.get_recent_messages(session_id, customer.customer_id)
    assert history[-1].message == response.message
    assert "Tôi muốn biết chính sách" in str(model.calls)


def test_factory_selects_real_product_advisor_when_ai_is_disabled(monkeypatch):
    from app.features.chat import dependencies
    class FakeAdvisor:
        def __init__(self, **kwargs):
            self.kwargs = kwargs
    monkeypatch.setattr(dependencies, "RealProductAdvisorService", FakeAdvisor)
    monkeypatch.setattr(dependencies, "ProductSemanticIndex", lambda url: url)
    service, client = build_chat_service(Settings(_env_file=None, product_advisor_mode="real", product_index_database_url="fake-db"))
    assert isinstance(service.product_advisor, FakeAdvisor)
    assert service.product_advisor.kwargs["index"] == "fake-db"
    assert client is None


def test_merged_endpoint_keeps_authentication_requirement():
    from fastapi import HTTPException
    from app.features.chat.router import get_backend_client
    from app.main import app
    class UnauthorizedBackend:
        async def get_current_customer(self, cookies):
            raise HTTPException(status_code=401, detail="Unauthorized")
    app.dependency_overrides[get_backend_client] = lambda: UnauthorizedBackend()
    try:
        with TestClient(app) as http:
            response = http.post(f"/api/v1/chat/sessions/{uuid4()}/messages", json={"message": "xin chào"})
            assert response.status_code == 401
    finally:
        app.dependency_overrides.pop(get_backend_client, None)


def test_product_search_and_cards_share_request_context_and_customer_cookies():
    from contextvars import ContextVar
    from app.features.product_advisor.backend import backend_cookies
    plan_context = ContextVar('test_search_plan', default=None)
    class Advisor:
        def search(self, plan):
            assert backend_cookies.get() == {'access_token': 'customer-cookie'}
            plan_context.set(plan)
            return []
        def build_cards(self, matches):
            assert plan_context.get().category == 'Laptop'
            assert backend_cookies.get() == {'access_token': 'customer-cookie'}
            return []
    service = MockChatService(
        query_understanding=OllamaQueryUnderstandingService(FakeChat('{"intent":"PRODUCT_DISCOVERY","product_search":{"category":"Laptop"}}')),
        product_advisor=Advisor(),
    )
    service.send_message(uuid4(), SendMessageRequest(message='Laptop?'), cookies={'access_token':'customer-cookie'})
    assert backend_cookies.get() is None


def test_cheaper_followup_uses_previous_card_price_not_repeated_budget():
    from app.features.conversation.repository import InMemoryConversationRepository
    from app.features.conversation.service import ConversationService
    from app.features.customer_context.models import AuthenticatedCustomer
    from app.shared.contracts.response import ProductCard
    from decimal import Decimal
    conversation = ConversationService(InMemoryConversationRepository())
    customer = AuthenticatedCustomer(user_id=uuid4(), customer_id=uuid4(), display_name='Customer')
    session_id = uuid4()
    conversation.create_session(customer.customer_id, session_id)
    previous = ProductCard(product_id=uuid4(), product_name='Laptop trước', effective_price=Decimal('15990000'))
    conversation.repository.remember_product_cards(session_id, customer.customer_id, [previous])
    class Advisor:
        plan = None
        def search(self, plan):
            self.plan = plan
            return []
        def build_cards(self, matches):
            return []
    advisor = Advisor()
    service = MockChatService(conversation_service=conversation, product_advisor=advisor,
        query_understanding=OllamaQueryUnderstandingService(FakeChat('{"intent":"PRODUCT_DISCOVERY","product_search":{"category":"Laptop","max_price":20000000}}')))
    service.send_message(session_id, SendMessageRequest(message='Có loại rẻ hơn không?'), customer=customer)
    assert advisor.plan.max_price == Decimal('15989999')
    assert conversation.repository.last_product_cards(session_id, customer.customer_id)[0].effective_price == Decimal('15990000')
