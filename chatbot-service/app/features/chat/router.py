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

chat_service = ChatService()


def get_backend_client() -> BackendClient:
    return BackendClient()


def get_chat_service() -> ChatService:
    return chat_service


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
) -> ChatResponse:
    return await chat_service.send_message_async(
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