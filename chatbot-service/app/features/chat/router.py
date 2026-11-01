from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, Request, Response, HTTPException, Query
from app.shared.config import get_settings, is_dev_like
from app.shared.tracing import capture, enabled, store, event

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
    http_response: Response,
    customer: AuthenticatedCustomer = Depends(get_authenticated_customer),
    service: ChatService = Depends(get_chat_service),
) -> ChatResponse:
    async def run():
        return await service.send_message_async(
            session_id=session_id, request=request, customer=customer,
            cookies=dict(http_request.cookies),
        )
    if not enabled(get_settings()):
        return await run()
    try:
        trace_id = UUID(http_request.headers.get('X-Chat-Trace-Id') or str(uuid4()))
    except ValueError:
        raise HTTPException(400, 'X-Chat-Trace-Id phải là UUID.')
    trace = store.create(trace_id, session_id, customer.customer_id)
    http_response.headers['X-Chat-Trace-Id'] = str(trace_id)
    http_response.headers['Cache-Control'] = 'no-store'
    with capture(trace):
        event('request.accepted', {'message': request.message, 'session_id': session_id,
                                 'authentication': 'verified_by_backend'})
        try:
            return await run()
        except HTTPException as exc:
            exc.headers = {**(exc.headers or {}), 'X-Chat-Trace-Id': str(trace_id), 'Cache-Control': 'no-store'}
            raise


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
    before: UUID | None = None,
    size: int = Query(default=100, ge=1, le=100),
    customer: AuthenticatedCustomer = Depends(get_authenticated_customer),
    service: ChatService = Depends(get_chat_service),
):
    return service.conversation_service.repository.history_page(session_id, customer.customer_id, before, size)


def require_dev_like_environment() -> None:
    # Lab diagnostics only; resolved before authentication so production adds no backend/Ollama call.
    if not is_dev_like(get_settings().app_env):
        raise HTTPException(404)


@router.get("/status", dependencies=[Depends(require_dev_like_environment)])
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
        'trace_enabled': enabled(settings),
        'trace_file_enabled': enabled(settings) and settings.chatbot_trace_file_enabled,
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
        'conversation_storage': get_settings().conversation_storage,
        'feedback_storage': 'message_feedback' if get_settings().conversation_storage == 'postgres' else 'memory',
        'snapshot_storage': 'assistant-message-envelope' if get_settings().conversation_storage == 'postgres' else 'memory',
    }


@router.get('/sessions/{session_id}/traces')
def list_traces(session_id: UUID, response: Response,
                customer: AuthenticatedCustomer = Depends(get_authenticated_customer)):
    if not enabled(get_settings()):
        raise HTTPException(404, 'Tracing chưa được bật trong môi trường phát triển.')
    response.headers['Cache-Control'] = 'no-store'
    return store.list(session_id, customer.customer_id)


@router.get('/sessions/{session_id}/traces/{trace_id}')
def get_trace(session_id: UUID, trace_id: UUID, response: Response,
              customer: AuthenticatedCustomer = Depends(get_authenticated_customer)):
    if not enabled(get_settings()):
        raise HTTPException(404, 'Tracing chưa được bật trong môi trường phát triển.')
    response.headers['Cache-Control'] = 'no-store'
    return store.get(trace_id, session_id, customer.customer_id)


@router.get('/sessions')
def list_sessions(page: int = 0, size: int = 20,
                  customer: AuthenticatedCustomer = Depends(get_authenticated_customer),
                  service: ChatService = Depends(get_chat_service)):
    return service.conversation_service.list_sessions(customer.customer_id,page,size)


@router.get('/sessions/{session_id}')
def session_detail(session_id: UUID, customer: AuthenticatedCustomer = Depends(get_authenticated_customer),
                   service: ChatService = Depends(get_chat_service)):
    return service.conversation_service.get_session(session_id,customer.customer_id)


@router.post('/sessions/{session_id}/close')
def close_session(session_id: UUID, customer: AuthenticatedCustomer = Depends(get_authenticated_customer),
                  service: ChatService = Depends(get_chat_service)):
    return service.conversation_service.end_session(session_id,customer.customer_id)
