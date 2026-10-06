from uuid import UUID

from fastapi import APIRouter

from app.features.chat.models import SendMessageRequest
from app.features.chat.service import MockChatService
from app.features.product_advisor.backend import BackendProductClient
from app.features.product_advisor.real_service import RealProductAdvisorService
from app.features.product_advisor.semantic import OllamaEmbeddingClient, ProductSemanticIndex
from app.shared.config import get_settings
from app.shared.contracts.response import ChatResponse


router = APIRouter(
    prefix="/api/v1/chat",
    tags=["chat"],
)

def _chat_service() -> MockChatService:
    settings = get_settings()
    if settings.product_advisor_mode != "real":
        return MockChatService()
    from uuid import UUID

    index = ProductSemanticIndex(settings.product_index_database_url)
    advisor = RealProductAdvisorService(
        backend=BackendProductClient(settings.backend_base_url, settings.backend_bearer_token),
        warehouse_id=UUID(settings.chatbot_warehouse_id) if settings.chatbot_warehouse_id else None,
        index=index,
        embedder=OllamaEmbeddingClient(settings.ollama_base_url, settings.ollama_embedding_model),
    )
    return MockChatService(product_advisor=advisor)


chat_service = _chat_service()


@router.post(
    "/sessions/{session_id}/messages",
    response_model=ChatResponse,
)
def send_message(
    session_id: UUID,
    request: SendMessageRequest,
) -> ChatResponse:
    return chat_service.send_message(
        session_id=session_id,
        request=request,
    )
