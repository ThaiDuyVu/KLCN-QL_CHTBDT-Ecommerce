from datetime import datetime, timezone
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.shared.contracts.context import (
    AuthenticatedCustomer,
    ChatMessage,
    QueryContext,
)
from app.shared.contracts.enums import MessageRole


def test_query_context_accepts_valid_conversation() -> None:
    customer = AuthenticatedCustomer(
        user_id=uuid4(),
        customer_id=uuid4(),
        display_name="Thai",
    )

    message = ChatMessage(
        role=MessageRole.USER,
        content="Tôi cần laptop khoảng 20 triệu",
        created_at=datetime.now(timezone.utc),
    )

    context = QueryContext(
        customer=customer,
        session_id=uuid4(),
        current_message="Con Dell thì sao?",
        recent_messages=[message],
    )

    assert context.customer == customer
    assert context.current_message == "Con Dell thì sao?"
    assert len(context.recent_messages) == 1


def test_chat_message_parses_role_string() -> None:
    message = ChatMessage(
        role="ASSISTANT",
        content="Bạn cần hỗ trợ gì?",
        created_at=datetime.now(timezone.utc),
    )

    assert message.role == MessageRole.ASSISTANT


def test_query_context_rejects_empty_current_message() -> None:
    with pytest.raises(ValidationError):
        QueryContext(
            customer=AuthenticatedCustomer(
                user_id=uuid4(),
                customer_id=uuid4(),
                display_name="Thai",
            ),
            session_id=uuid4(),
            current_message="",
        )


def test_query_context_rejects_unknown_fields() -> None:
    with pytest.raises(ValidationError):
        AuthenticatedCustomer(
            user_id=uuid4(),
            customer_id=uuid4(),
            display_name="Thai",
            email="thai@example.com",
        )