from app.features.response_generation.service import (
    MockResponseGenerationService,
)
from app.shared.contracts.context import GroundedContext
from app.shared.contracts.enums import Intent
from app.shared.contracts.query import QueryPlan


def test_mock_response_generation_handles_greeting() -> None:
    service = MockResponseGenerationService()

    context = GroundedContext(
        query_plan=QueryPlan(
            intent=Intent.GREETING,
        ),
        user_query="Xin chào",
    )

    response = service.generate(context)

    assert response == (
        "Xin chào! Mình có thể hỗ trợ bạn "
        "tìm và tư vấn thiết bị điện tử."
    )


def test_mock_response_generation_handles_unknown() -> None:
    service = MockResponseGenerationService()

    context = GroundedContext(
        query_plan=QueryPlan(
            intent=Intent.UNKNOWN,
        ),
        user_query="abc",
    )

    assert service.generate(context) == (
        "Mình chưa hiểu yêu cầu của bạn."
    )