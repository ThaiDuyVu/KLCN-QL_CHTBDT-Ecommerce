from uuid import UUID

from fastapi import APIRouter, Depends, Request

from app.features.chat.models import FeedbackRequest, SendMessageRequest
from app.features.chat.service import ChatService
from app.features.customer_context.models import AuthenticatedCustomer
from app.shared.backend.client import BackendClient
from app.shared.contracts.response import ChatResponse


router = APIRouter(
    prefix="/api/v1/chat",
    tags=["chat"],
)


def get_backend_client() -> BackendClient:
    return BackendClient()


def get_chat_service(request: Request) -> ChatService:
    return request.app.state.chat_service


async def get_authenticated_customer(
    request: Request,
    backend_client: BackendClient = Depends(get_backend_client),
) -> AuthenticatedCustomer:
    cookies = dict(request.cookies)
    return await backend_client.get_current_customer(cookies)


@router.post(
    "/sessions/{session_id}/messages",
    response_model=ChatResponse,
)
async def send_message(
    session_id: UUID,
    request: SendMessageRequest,
    http_request: Request,
    customer: AuthenticatedCustomer = Depends(get_authenticated_customer),
    service: ChatService = Depends(get_chat_service),
) -> ChatResponse:
    return await service.send_message_async(
        session_id=session_id,
        request=request,
        customer=customer,
        cookies=dict(http_request.cookies),
    )


@router.post(
    "/sessions/{session_id}/messages/{message_id}/feedback",
)
def feedback_message(
    session_id: UUID,
    message_id: UUID,
    request: FeedbackRequest,
    customer: AuthenticatedCustomer = Depends(get_authenticated_customer),
    service: ChatService = Depends(get_chat_service),
):
    return service.rate_message(
        session_id=session_id,
        message_id=message_id,
        customer_id=customer.customer_id,
        rating=request.rating,
    )


@router.get("/sessions/{session_id}/messages")
def get_messages(
    session_id: UUID,
    customer: AuthenticatedCustomer = Depends(get_authenticated_customer),
    service: ChatService = Depends(get_chat_service),
):
    return service.conversation_service.get_recent_messages(session_id, customer.customer_id, window=100)


@router.get("/status")
async def status(customer: AuthenticatedCustomer = Depends(get_authenticated_customer)):
    """Read-only lab readiness; never returns tokens or database connection strings."""
    import httpx
    from app.shared.config import get_settings
    settings = get_settings()
    available_models = []
    ollama_reachable = False
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            response = await client.get(settings.ollama_base_url.rstrip('/') + '/api/tags')
            response.raise_for_status()
            available_models = [m['name'] for m in response.json().get('models', [])]
            ollama_reachable = True
    except (httpx.HTTPError, ValueError, KeyError, TypeError):
        pass
    return {
        'ai_enabled': settings.chatbot_ai_enabled,
        'product_mode': settings.product_advisor_mode,
        'chat_model': settings.ollama_chat_model,
        'embedding_model': settings.ollama_embedding_model,
        'ollama_reachable': ollama_reachable,
        'chat_model_ready': settings.ollama_chat_model in available_models,
        'embedding_model_ready': settings.ollama_embedding_model in available_models,
        'knowledge_configured': bool(settings.knowledge_database_url),
        'knowledge_status': settings.knowledge_document_status,
        'product_index_configured': bool(settings.product_index_database_url),
        'warehouse_configured': bool(settings.chatbot_warehouse_id),
        'conversation_storage': 'in-memory',
    }
