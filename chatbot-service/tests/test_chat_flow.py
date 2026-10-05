from uuid import UUID, uuid4

import pytest
from fastapi.testclient import TestClient

from app.features.chat.router import get_authenticated_customer
from app.features.customer_context.models import AuthenticatedCustomer
from app.main import app


async def override_get_authenticated_customer() -> AuthenticatedCustomer:
    return AuthenticatedCustomer(
        user_id=UUID("12345678-1234-5678-1234-567812345678"),
        customer_id=UUID("12345678-1234-5678-1234-567812345678"),
        display_name="Test User",
    )


@pytest.fixture(autouse=True)
def override_auth_dependency() -> None:
    app.dependency_overrides[get_authenticated_customer] = (
        override_get_authenticated_customer
    )
    yield
    app.dependency_overrides.clear()


client = TestClient(app)


def test_send_message_returns_mock_chat_response() -> None:
    session_id = uuid4()

    response = client.post(
        f"/api/v1/chat/sessions/{session_id}/messages",
        json={
            "message": "Xin chào",
        },
    )

    assert response.status_code == 200

    body = response.json()

    assert body["session_id"] == str(session_id)
    assert body["message"] == (
        "Xin chào! Mình có thể hỗ trợ bạn "
        "tìm và tư vấn thiết bị điện tử."
    )
    assert body["products"] == []
    assert body["sources"] == []
    assert body["suggested_questions"] == [
        "Bạn muốn tìm sản phẩm nào?"
    ]


def test_send_message_rejects_empty_message() -> None:
    response = client.post(
        f"/api/v1/chat/sessions/{uuid4()}/messages",
        json={
            "message": "",
        },
    )

    assert response.status_code == 422


def test_feedback_message_requires_session_ownership() -> None:
    session_owner_a = UUID("11111111-1111-4111-8111-111111111111")
    session_owner_b = UUID("22222222-2222-4222-8222-222222222222")
    session_id = uuid4()

    app.dependency_overrides[get_authenticated_customer] = (
        lambda: AuthenticatedCustomer(
            user_id=session_owner_a,
            customer_id=session_owner_a,
            display_name="User A",
        )
    )

    from app.features.conversation.service import ConversationService

    conversation_service = ConversationService()
    conversation_service.create_session(customer_id=session_owner_b, session_id=session_id)
    message = conversation_service.add_message(
        session_id=session_id,
        sender_type="USER",
        message="Test message",
        customer_id=session_owner_b,
    )

    response = client.post(
        f"/api/v1/chat/sessions/{session_id}/messages/{message.id}/feedback",
        json={"rating": 1},
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "Forbidden: session does not belong to this customer"


def test_send_message_returns_mock_product_discovery() -> None:
    session_id = uuid4()

    response = client.post(
        f"/api/v1/chat/sessions/{session_id}/messages",
        json={
            "message": "Tôi cần laptop để lập trình",
        },
    )

    assert response.status_code == 200

    body = response.json()

    assert body["message"] == (
        "Mình tìm thấy một sản phẩm "
        "phù hợp với nhu cầu của bạn."
    )

    assert len(body["products"]) == 1

    product = body["products"][0]

    assert product["product_name"] == "Dell Inspiron 15"
    assert product["ram"] == "16GB"
    assert product["storage"] == "512GB"
    assert product["effective_price"] == "15990000"