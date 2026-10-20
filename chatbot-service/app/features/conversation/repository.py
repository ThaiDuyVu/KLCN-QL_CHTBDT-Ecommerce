from __future__ import annotations

from datetime import datetime, timezone
from threading import RLock
from typing import Any
from uuid import UUID, uuid4

from fastapi import HTTPException

from app.features.conversation.models import ChatMessage, ChatSession, SenderType, EvidenceSnapshot
from app.shared.config import get_settings
from app.shared.contracts.response import ProductCard


_default_repository = None


class InMemoryConversationRepository:
    def __init__(self) -> None:
        self._sessions_by_id: dict[UUID, ChatSession] = {}
        self._messages_by_session: dict[UUID, list[ChatMessage]] = {}
        self._snapshots: dict[UUID, EvidenceSnapshot] = {}
        self._last_product_cards: dict[UUID, list[ProductCard]] = {}
        self._lock = RLock()

    def create_session(
        self,
        customer_id: UUID,
        session_id: UUID | None = None,
    ) -> ChatSession:
        with self._lock:
            if session_id is not None and session_id in self._sessions_by_id:
                return self.get_session(session_id, customer_id)

            session = ChatSession(customer_id=customer_id, id=session_id or uuid4())
            self._sessions_by_id[session.id] = session
            self._messages_by_session.setdefault(session.id, [])
            return session

    def get_session(self, session_id: UUID, customer_id: UUID) -> ChatSession:
        with self._lock:
            session = self._sessions_by_id.get(session_id)
            if session is None:
                raise HTTPException(status_code=404, detail="Session not found")
            if session.customer_id != customer_id:
                raise HTTPException(status_code=403, detail="Forbidden: session does not belong to this customer")
            return session

    def get_active_sessions(self, customer_id: UUID) -> list[ChatSession]:
        with self._lock:
            return [
                session
                for session in self._sessions_by_id.values()
                if session.customer_id == customer_id and session.is_active
            ]

    def end_session(self, session_id: UUID, customer_id: UUID) -> ChatSession:
        with self._lock:
            session = self.get_session(session_id, customer_id)
            session.is_active = False
            return session

    def add_message(
        self,
        session_id: UUID,
        sender_type: SenderType | str,
        message: str,
        customer_id: UUID | None = None,
    ) -> ChatMessage:
        with self._lock:
            if customer_id is not None:
                self.get_session(session_id, customer_id)

            session = self._sessions_by_id.get(session_id)
            if session is None:
                raise HTTPException(status_code=404, detail="Session not found")
            if not session.is_active:
                raise HTTPException(status_code=409, detail="Session is closed")

            sender = SenderType(sender_type) if isinstance(sender_type, str) else sender_type
            chat_message = ChatMessage(
                session_id=session_id,
                sender_type=sender,
                message=message,
                created_at=datetime.now(timezone.utc),
            )
            self._messages_by_session.setdefault(session_id, []).append(chat_message)
            return chat_message

    def get_message(self, session_id: UUID, message_id: UUID) -> ChatMessage | None:
        with self._lock:
            for message in self._messages_by_session.get(session_id, []):
                if message.id == message_id:
                    return message
            return None

    def save_session(self, session: ChatSession) -> ChatSession:
        with self._lock:
            self._sessions_by_id[session.id] = session
            return session

    def get_recent_messages(self, session_id: UUID, window: int | None = None) -> list[ChatMessage]:
        with self._lock:
            limit = window if window is not None else get_settings().chat_history_window
            history = self._messages_by_session.get(session_id, [])
            return list(history)[-limit:] if limit > 0 else []

    def last_product_cards(self, session_id: UUID, customer_id: UUID) -> list[ProductCard]:
        with self._lock:
            self.get_session(session_id, customer_id)
            return [card.model_copy(deep=True) for card in self._last_product_cards.get(session_id, [])]

    def remember_product_cards(self, session_id: UUID, customer_id: UUID, cards: list[ProductCard]) -> None:
        with self._lock:
            self.get_session(session_id, customer_id)
            if cards:
                self._last_product_cards[session_id] = [card.model_copy(deep=True) for card in cards]


    def get_snapshot(self, session_id, customer_id):
        self.get_session(session_id, customer_id)
        return self._snapshots.get(session_id) or (EvidenceSnapshot(cards=self._last_product_cards[session_id]) if self._last_product_cards.get(session_id) else None)

    def save_assistant(self, session_id, customer_id, text, snapshot=None):
        message = self.add_message(session_id, SenderType.ASSISTANT, text, customer_id)
        if snapshot and snapshot.cards:
            self._snapshots[session_id] = snapshot.model_copy(deep=True)
            self.remember_product_cards(session_id, customer_id, snapshot.cards)
        return message

    def set_rating(self, session_id, message_id, customer_id, rating):
        self.get_session(session_id, customer_id)
        message = self.get_message(session_id, message_id)
        if message is None:
            raise HTTPException(404, 'Message not found')
        message.rating = rating
        return message

    def list_sessions(self, customer_id, page=0, size=20):
        rows = sorted((s for s in self._sessions_by_id.values() if s.customer_id == customer_id),
                      key=lambda s: (s.created_at, str(s.id)), reverse=True)
        return rows[page*size:(page+1)*size]


    def history_page(self, session_id, customer_id, before=None, size=100):
        with self._lock:
            self.get_session(session_id,customer_id)
            rows=self._messages_by_session.get(session_id,[])
            if before is not None:
                index=next((i for i,m in enumerate(rows) if m.id==before),None)
                if index is None: raise HTTPException(404,'History cursor not found')
                rows=rows[:index]
            return list(rows[-size:])
