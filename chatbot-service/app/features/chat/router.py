from uuid import UUID

from fastapi import APIRouter

from app.features.chat.models import SendMessageRequest
from app.features.chat.service import MockChatService
from app.shared.contracts.response import ChatResponse


router = APIRouter(
    prefix="/api/v1/chat",
    tags=["chat"],
)

chat_service = MockChatService()


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