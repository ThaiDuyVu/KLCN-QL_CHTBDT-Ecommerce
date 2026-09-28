from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import app


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