from uuid import UUID

from fastapi import APIRouter, Request

from app.features.chat.models import SendMessageRequest
from app.shared.contracts.response import ChatResponse


router = APIRouter(
    prefix="/api/v1/chat",
    tags=["chat"],
)


@router.post(
    "/sessions/{session_id}/messages",
    response_model=ChatResponse,
)
def send_message(
    session_id: UUID,
    request: SendMessageRequest,
    http_request: Request,
) -> ChatResponse:
    return http_request.app.state.chat_service.send_message(
        session_id=session_id,
        request=request,
    )
