from __future__ import annotations

import asyncio
from uuid import UUID

from fastapi import HTTPException

from app.features.chat.models import SendMessageRequest
from app.features.conversation.models import SenderType
from app.features.conversation.service import ConversationService
from app.features.customer_context.models import AuthenticatedCustomer
from app.features.customer_context.service import CustomerContextService
from app.features.product_advisor.protocol import ProductAdvisorService
from app.features.product_advisor.service import MockProductAdvisorService
from app.features.query_understanding.protocol import QueryUnderstandingService
from app.features.query_understanding.service import MockQueryUnderstandingService
from app.features.response_generation.protocol import ResponseGenerationService
from app.features.response_generation.service import MockResponseGenerationService
from app.shared.backend.client import BackendClient
from app.shared.contracts.context import (
    ChatMessage as SharedChatMessage,
    GroundedContext,
    QueryContext,
)
from app.shared.contracts.enums import Intent, MessageRole
from app.shared.contracts.product import ProductMatch
from app.shared.contracts.response import ChatResponse, ProductCard


class ChatService:
    def __init__(
        self,
        conversation_service: ConversationService | None = None,
        customer_context_service: CustomerContextService | None = None,
        query_understanding: QueryUnderstandingService | None = None,
        product_advisor: ProductAdvisorService | None = None,
        response_generation: ResponseGenerationService | None = None,
    ) -> None:
        self.conversation_service = (
            conversation_service
            if conversation_service is not None
            else ConversationService()
        )
        self.customer_context_service = (
            customer_context_service
            if customer_context_service is not None
            else CustomerContextService(backend_client=BackendClient())
        )
        self.query_understanding = (
            query_understanding
            if query_understanding is not None
            else MockQueryUnderstandingService()
        )
        self.product_advisor = (
            product_advisor
            if product_advisor is not None
            else MockProductAdvisorService()
        )
        self.response_generation = (
            response_generation
            if response_generation is not None
            else MockResponseGenerationService()
        )

    async def send_message_async(
        self,
        session_id: UUID,
        request: SendMessageRequest,
        customer: AuthenticatedCustomer | None = None,
        cookies: dict[str, str] | None = None,
    ) -> ChatResponse:
        authenticated_customer = customer or AuthenticatedCustomer(
            user_id=UUID(int=0),
            customer_id=UUID(int=0),
            display_name="Guest",
        )

        if customer is not None:
            try:
                self.conversation_service.get_session(session_id, customer.customer_id)
            except HTTPException as exc:
                if exc.status_code == 404:
                    self.conversation_service.create_session(
                        customer_id=customer.customer_id,
                        session_id=session_id,
                    )
                else:
                    raise

            self.conversation_service.add_message(
                session_id=session_id,
                sender_type=SenderType.USER,
                message=request.message,
                customer_id=customer.customer_id,
            )
            recent_messages = self.conversation_service.get_recent_messages(
                session_id=session_id,
                customer_id=customer.customer_id,
                window=10,
            )
        else:
            recent_messages = []

        recent_history = [
            SharedChatMessage(
                role=(
                    MessageRole.USER
                    if message.sender_type == SenderType.USER
                    else MessageRole.ASSISTANT
                ),
                content=message.message,
                created_at=message.created_at,
            )
            for message in recent_messages
        ]

        query_context = QueryContext(
            customer=authenticated_customer.model_dump(),
            session_id=session_id,
            current_message=request.message,
            recent_messages=recent_history,
        )

        query_plan = self.query_understanding.understand(query_context)

        product_matches: list[ProductMatch] = []
        product_cards: list[ProductCard] = []

        if query_plan.product_search is not None:
            product_matches = self.product_advisor.search(query_plan.product_search)
            product_cards = self.product_advisor.build_cards(product_matches)

        """chuyển tiếp 'cookies' nhận từ Client sang hệ thống Spring Boot qua BackendClient
        Spring Security sẽ chịu trách nhiệm giải mã cookies, xác thực danh tính 
        và trả về đúng dữ liệu thuộc sở hữu của Customer đó"""
        context_payload = await self.customer_context_service.load_context(
            query_plan.intent,
            cookies or {},
        )

        grounded_context = GroundedContext(
            query_plan=query_plan,
            user_query=query_context.current_message,
            relevant_history=query_context.recent_messages,
            products=product_matches,
            customer_context=context_payload,
        )

        assistant_message = self.response_generation.generate(grounded_context)

        if customer is not None:
            self.conversation_service.add_message(
                session_id=session_id,
                sender_type=SenderType.ASSISTANT,
                message=assistant_message,
                customer_id=customer.customer_id,
            )

        return ChatResponse(
            session_id=session_id,
            message=assistant_message,
            products=product_cards,
            suggested_questions=[
                "Bạn muốn tìm sản phẩm nào?",
            ]
            if query_plan.intent in (Intent.GREETING, Intent.PRODUCT_DISCOVERY)
            else [],
        )

    def send_message(
        self,
        session_id: UUID,
        request: SendMessageRequest,
        customer: AuthenticatedCustomer | None = None,
        cookies: dict[str, str] | None = None,
    ) -> ChatResponse:
        return asyncio.run(
            self.send_message_async(
                session_id=session_id,
                request=request,
                customer=customer,
                cookies=cookies,
            )
        )

    def rate_message(
        self,
        session_id: UUID,
        message_id: UUID,
        customer_id: UUID,
        rating: int,
    ):
        return self.conversation_service.rate_message(
            session_id=session_id,
            message_id=message_id,
            customer_id=customer_id,
            rating=rating,
        )


MockChatService = ChatService

