from __future__ import annotations

from uuid import UUID

from fastapi import HTTPException

from app.features.conversation.models import ChatMessage, ChatSession, SenderType
from app.features.conversation.repository import InMemoryConversationRepository


_shared_conversation_repository = InMemoryConversationRepository()


class ConversationService:
    def __init__(self, repository: InMemoryConversationRepository | None = None) -> None:
        self.repository = repository if repository is not None else _shared_conversation_repository

    def create_session(
        self,
        customer_id: UUID,
        session_id: UUID | None = None,
    ) -> ChatSession:
        if customer_id is None:
            raise HTTPException(status_code=400, detail="customer_id is required")
        return self.repository.create_session(customer_id, session_id)

    def get_session(self, session_id: UUID, customer_id: UUID) -> ChatSession:
        if session_id is None or customer_id is None:
            raise HTTPException(status_code=400, detail="session_id and customer_id are required")
        return self.repository.get_session(session_id, customer_id)

    def get_active_sessions(self, customer_id: UUID) -> list[ChatSession]:
        if customer_id is None:
            raise HTTPException(status_code=400, detail="customer_id is required")
        return self.repository.get_active_sessions(customer_id)

    def end_session(self, session_id: UUID, customer_id: UUID) -> ChatSession:
        if session_id is None or customer_id is None:
            raise HTTPException(status_code=400, detail="session_id and customer_id are required")
        return self.repository.end_session(session_id, customer_id)

    def add_message(
        self,
        session_id: UUID,
        sender_type: SenderType | str,
        message: str,
        customer_id: UUID | None = None,
    ) -> ChatMessage:
        if not message or not message.strip():
            raise HTTPException(status_code=400, detail="message must not be empty")
        if session_id is None:
            raise HTTPException(status_code=400, detail="session_id is required")

        sender = SenderType(sender_type) if isinstance(sender_type, str) else sender_type
        if sender not in (SenderType.USER, SenderType.ASSISTANT):
            raise HTTPException(status_code=400, detail="invalid sender_type")

        return self.repository.add_message(
            session_id=session_id,
            sender_type=sender,
            message=message.strip(),
            customer_id=customer_id,
        )

    def get_recent_messages(self, session_id: UUID, customer_id: UUID, window: int | None = None) -> list[ChatMessage]:
        if session_id is None or customer_id is None:
            raise HTTPException(status_code=400, detail="session_id and customer_id are required")
        self.repository.get_session(session_id, customer_id)
        return self.repository.get_recent_messages(session_id, window)

    def rate_message(
        self,
        session_id: UUID,
        message_id: UUID,
        customer_id: UUID,
        rating: int,
    ) -> ChatMessage:
        if session_id is None or message_id is None or customer_id is None:
            raise HTTPException(status_code=400, detail="session_id, message_id and customer_id are required")

        if rating not in (-1, 0, 1):
            raise HTTPException(status_code=400, detail="rating must be one of -1, 0, 1")

        session = self.repository.get_session(session_id, customer_id)
        message = self.repository.get_message(session_id, message_id)
        if message is None:
            raise HTTPException(status_code=404, detail="Message not found")

        message.rating = rating
        self.repository.save_session(session)
        return message