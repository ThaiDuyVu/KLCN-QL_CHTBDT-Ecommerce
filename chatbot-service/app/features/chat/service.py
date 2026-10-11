from __future__ import annotations

import asyncio
from collections.abc import Callable
from decimal import Decimal
from uuid import UUID

import httpx

from fastapi import HTTPException
from app.shared.config import get_settings
from app.features.product_advisor.backend import backend_cookies

from app.features.knowledge.protocol import KnowledgeRetrievalService
from app.features.knowledge.repository import KnowledgeRepositoryError
from app.features.knowledge.service import KnowledgeUnavailable, build_source_references
from app.shared.contracts.knowledge import KnowledgeChunkMatch

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
        knowledge_retrieval: KnowledgeRetrievalService | None = None,
        knowledge_top_k: int = 5,
        after_response: Callable[[QueryContext, ChatResponse, list[KnowledgeChunkMatch]], None] | None = None,
    ) -> None:
        if not 1 <= knowledge_top_k <= 20:
            raise ValueError("knowledge_top_k must be between 1 and 20")
        self.knowledge_retrieval = knowledge_retrieval
        self.knowledge_top_k = knowledge_top_k
        self.after_response = after_response
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
                window=get_settings().chat_history_window + 1,
            )
        else:
            recent_messages = []

        recent_messages = recent_messages[:-1] if recent_messages else []

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

        query_plan = await asyncio.to_thread(self.query_understanding.understand, query_context)

        product_matches: list[ProductMatch] = []
        product_cards: list[ProductCard] = []

        if query_plan.intent in {Intent.PRODUCT_DISCOVERY, Intent.PRODUCT_DETAIL, Intent.PRODUCT_COMPARE} and query_plan.product_search is not None:
            product_search = query_plan.product_search
            if query_plan.intent == Intent.PRODUCT_DETAIL:
                product_search = product_search.model_copy(update={"top_k": 1})
            elif query_plan.intent == Intent.PRODUCT_COMPARE:
                product_search = product_search.model_copy(update={"top_k": min(product_search.top_k, 3)})
            cheaper = any(term in request.message.casefold() for term in ("rẻ hơn", "thấp hơn", "cheaper"))
            below_minimum = False
            if cheaper and customer is not None:
                previous = self.conversation_service.repository.last_product_cards(session_id, customer.customer_id)
                prices = [card.effective_price for card in previous if card.effective_price is not None]
                if prices:
                    ceiling = min(prices) - Decimal("1")
                    below_minimum = ceiling < 0 or (product_search.min_price is not None and ceiling < product_search.min_price)
                    if not below_minimum:
                        data = product_search.model_dump()
                        data["max_price"] = min(ceiling, product_search.max_price) if product_search.max_price is not None else ceiling
                        product_search = type(product_search).model_validate(data)
                        query_plan = query_plan.model_copy(update={"product_search": product_search})

            def search_and_build():
                token = backend_cookies.set(cookies or None)
                try:
                    matches = self.product_advisor.search(product_search)
                    return matches, self.product_advisor.build_cards(matches)
                finally:
                    backend_cookies.reset(token)

            try:
                if not below_minimum:
                    product_matches, product_cards = await asyncio.to_thread(search_and_build)
                if customer is not None:
                    self.conversation_service.repository.remember_product_cards(session_id, customer.customer_id, product_cards)
            except (httpx.HTTPError, ValueError) as exc:
                raise HTTPException(status_code=503, detail="Chưa thể lấy dữ liệu sản phẩm. Kiểm tra backend, kho hàng và dịch vụ embedding.") from exc

        """chuyển tiếp 'cookies' nhận từ Client sang hệ thống Spring Boot qua BackendClient
        Spring Security sẽ chịu trách nhiệm giải mã cookies, xác thực danh tính 
        và trả về đúng dữ liệu thuộc sở hữu của Customer đó"""
        context_payload = await self.customer_context_service.load_context(
            query_plan.intent,
            cookies or {},
        )

        knowledge_chunks = []
        if query_plan.intent == Intent.KNOWLEDGE_QA and self.knowledge_retrieval is not None:
            try:
                knowledge_chunks = await asyncio.to_thread(
                    self.knowledge_retrieval.retrieve,
                    query_plan.semantic_query or query_context.current_message,
                    top_k=self.knowledge_top_k,
                )
            except (KnowledgeUnavailable, KnowledgeRepositoryError):
                knowledge_chunks = []

        grounded_context = GroundedContext(
            query_plan=query_plan,
            user_query=query_context.current_message,
            relevant_history=query_context.recent_messages,
            products=product_matches,
            knowledge_chunks=knowledge_chunks,
            customer_context=context_payload,
        )

        assistant_message = await asyncio.to_thread(self.response_generation.generate, grounded_context)

        if customer is not None:
            self.conversation_service.add_message(
                session_id=session_id,
                sender_type=SenderType.ASSISTANT,
                message=assistant_message,
                customer_id=customer.customer_id,
            )

        response = ChatResponse(
            session_id=session_id,
            message=assistant_message,
            products=product_cards,
            sources=build_source_references(knowledge_chunks),
            suggested_questions=[
                "Bạn muốn tìm sản phẩm nào?",
            ]
            if query_plan.intent in (Intent.GREETING, Intent.PRODUCT_DISCOVERY)
            else [],
        )

        if self.after_response is not None:
            await asyncio.to_thread(self.after_response, query_context, response, knowledge_chunks)
        return response

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

