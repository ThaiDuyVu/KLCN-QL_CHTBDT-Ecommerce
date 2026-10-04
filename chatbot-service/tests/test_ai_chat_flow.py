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
        assert result.message == INSUFFICIENT
    finally:
        client.close()


def test_mock_factory_and_app_lifespan_still_work():
    from app.main import app
    service, client = build_chat_service(Settings(_env_file=None))
    assert client is None
    assert isinstance(service, MockChatService)
    with TestClient(app) as http:
        response = http.post(f"/api/v1/chat/sessions/{uuid4()}/messages", json={"message": "xin chào"})
        assert response.status_code == 200
        assert response.json()["sources"] == []


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
