from __future__ import annotations

from app.shared.tracing import traced, event

import asyncio
from collections.abc import Callable
from uuid import UUID

import httpx

from fastapi import HTTPException
from app.shared.config import get_settings
from app.features.product_advisor.backend import backend_cookies
from app.features.product_advisor.real_service import ProductResolutionError

from app.features.knowledge.protocol import KnowledgeRetrievalService
from app.features.knowledge.repository import KnowledgeRepositoryError
from app.features.knowledge.service import KnowledgeUnavailable, build_source_references, relevant_chunks
from app.shared.contracts.knowledge import KnowledgeChunkMatch

from app.features.chat.models import SendMessageRequest
from app.features.conversation.models import SenderType, EvidenceSnapshot
from app.features.conversation.references import resolve_reference
from app.features.conversation.async_lease import blocking, request_lease
from app.features.chat.policy import read_only_plan
from app.features.conversation.service import ConversationService
from app.features.customer_context.models import AuthenticatedCustomer
from app.features.customer_context.service import (
    CustomerContextService, EMPTY_CART_MESSAGE, NO_OWNED_ORDERS_MESSAGE, NoOwnedOrders, VerifiedEmptyCart,
)
from app.features.product_advisor.protocol import ProductAdvisorService
from app.features.product_advisor.service import MockProductAdvisorService
from app.features.query_understanding.protocol import QueryUnderstandingService
from app.features.query_understanding.service import MockQueryUnderstandingService
from app.features.response_generation.protocol import ResponseGenerationService
from app.features.response_generation.service import MockResponseGenerationService
from app.shared.backend.client import BackendClient
from app.shared.backend.errors import OwnedOrderPaymentIntegrityError, PAYMENT_UNAVAILABLE_MESSAGE
from app.shared.contracts.context import (
    ChatMessage as SharedChatMessage,
    GroundedContext,
    CustomerContext,
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

    async def send_message_async(self, session_id, request, customer=None, cookies=None):
        repository = self.conversation_service.repository
        if hasattr(repository, 'request_lock') and customer is not None:
            async with request_lease(repository, session_id, customer.customer_id):
                return await self._send_message_async(session_id, request, customer, cookies)

        return await self._send_message_async(session_id, request, customer, cookies)

    @traced("chat.pipeline", ("request", "session_id"))
    async def _send_message_async(
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
            # Repository I/O runs off the event loop; blocking() settles a started write before
            # propagating cancellation, so the request lease is released only afterwards (Fix04).
            try:
                await blocking(self.conversation_service.get_session, session_id, customer.customer_id)
            except HTTPException as exc:
                if exc.status_code == 404:
                    await blocking(
                        self.conversation_service.create_session,
                        customer_id=customer.customer_id,
                        session_id=session_id,
                    )
                else:
                    raise

            await blocking(
                self.conversation_service.add_message,
                session_id=session_id,
                sender_type=SenderType.USER,
                message=request.message,
                customer_id=customer.customer_id,
            )
            recent_messages = await blocking(
                self.conversation_service.get_context_messages,
                session_id=session_id,
                customer_id=customer.customer_id,
                window=get_settings().chat_history_window,
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

        event("conversation.context", query_context)
        def understand():
            token = backend_cookies.set(cookies or None)
            try:
                return self.query_understanding.understand(query_context)
            finally:
                backend_cookies.reset(token)
        try:
            query_plan = read_only_plan(request.message)
            if query_plan is not None:
                event('query.read_only_guard', query_plan)
            else:
                query_plan = await blocking(understand)
        except (httpx.HTTPError, ValueError) as exc:
            raise HTTPException(status_code=503, detail="Chưa thể xác minh catalog sản phẩm; vui lòng thử lại.") from exc

        if customer is not None:
            snapshot = await blocking(self.conversation_service.repository.get_snapshot, session_id, customer.customer_id)
            query_plan = resolve_reference(request.message, query_plan, snapshot)
        event("query.plan.initial", query_plan)

        product_matches: list[ProductMatch] = []
        product_cards: list[ProductCard] = []

        if query_plan.intent in {Intent.PRODUCT_DISCOVERY, Intent.PRODUCT_DETAIL, Intent.PRODUCT_COMPARE} and query_plan.product_search is not None:
            product_search = query_plan.product_search
            if query_plan.intent == Intent.PRODUCT_DETAIL:
                product_search = product_search.model_copy(update={"top_k": 1})
            elif query_plan.intent == Intent.PRODUCT_COMPARE:
                product_search = product_search.model_copy(update={"top_k": min(product_search.top_k, 3)})
            # Cheaper/ordinal follow-ups are resolved once, from the owned snapshot, by resolve_reference.
            event("query.plan.effective", {"product_search": product_search})

            def search_and_build():
                token = backend_cookies.set(cookies or None)
                try:
                    matches = self.product_advisor.search(product_search)
                    return matches, self.product_advisor.build_cards(matches)
                finally:
                    backend_cookies.reset(token)

            try:
                product_matches, product_cards = await blocking(search_and_build)
            except ProductResolutionError as exc:
                event('product.resolution_failed', {'reason': exc.reason}, status='warning')
                query_plan = query_plan.model_copy(update={'clarification': str(exc)})
            except (httpx.HTTPError, ValueError) as exc:
                raise HTTPException(status_code=503, detail="Chưa thể lấy dữ liệu sản phẩm. Kiểm tra backend, kho hàng và dịch vụ embedding.") from exc

        """chuyển tiếp 'cookies' nhận từ Client sang hệ thống Spring Boot qua BackendClient
        Spring Security sẽ chịu trách nhiệm giải mã cookies, xác thực danh tính 
        và trả về đúng dữ liệu thuộc sở hữu của Customer đó"""
        context_options = {}
        if query_plan.references.get('order_code'):
            context_options['order_code'] = query_plan.references['order_code']
        if query_plan.references.get('order_selection') == 'latest':
            context_options['latest'] = True
        try:
            context_payload = await self.customer_context_service.load_context(query_plan.intent, cookies or {}, **context_options)
        except OwnedOrderPaymentIntegrityError:
            # The owned candidate was selected before detail retrieval. Do not replace it,
            # invent payment evidence, or infer payment from the order summary/status.
            query_plan = query_plan.model_copy(update={'clarification': PAYMENT_UNAVAILABLE_MESSAGE})
            context_payload = CustomerContext()
            event('customer.payment_unavailable', {'intent': query_plan.intent,
                  'reason': 'owned_payment_integrity', 'payment_evidence_loaded': False}, status='warning')
        except NoOwnedOrders:
            # Only a successful empty owned list reaches here; backend errors keep their own path.
            query_plan = query_plan.model_copy(update={'clarification': NO_OWNED_ORDERS_MESSAGE})
            context_payload = CustomerContext()
        except VerifiedEmptyCart:
            # Same existing sentence as an initialized cart with no items; no cart ID is invented.
            query_plan = query_plan.model_copy(update={'clarification': EMPTY_CART_MESSAGE})
            context_payload = CustomerContext()

        knowledge_chunks = []
        if query_plan.intent == Intent.KNOWLEDGE_QA and self.knowledge_retrieval is not None:
            try:
                knowledge_chunks = await blocking(
                    self.knowledge_retrieval.retrieve,
                    query_plan.semantic_query or query_context.current_message,
                    top_k=self.knowledge_top_k,
                )
                knowledge_chunks = relevant_chunks(query_context.current_message, knowledge_chunks)
            except (KnowledgeUnavailable, KnowledgeRepositoryError) as exc:
                event("knowledge.fallback", {"reason": type(exc).__name__, "action": "continue_without_knowledge"}, status="warning")
                knowledge_chunks = []
                query_plan = query_plan.model_copy(update={'clarification': 'Dịch vụ tra cứu tài liệu đang không khả dụng. Mình chưa thể xác minh câu trả lời; bạn vui lòng thử lại sau.'})

        event("retrieval.summary", {"intent": query_plan.intent, "products": len(product_matches), "cards": len(product_cards), "knowledge_chunks": len(knowledge_chunks), "knowledge_configured": self.knowledge_retrieval is not None})
        grounded_context = GroundedContext(
            query_plan=query_plan,
            user_query=query_context.current_message,
            relevant_history=query_context.recent_messages,
            products=product_matches,
            knowledge_chunks=knowledge_chunks,
            customer_context=context_payload,
        )

        event("response.grounded_context", grounded_context)
        assistant_message = await blocking(self.response_generation.generate, grounded_context)

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

        if customer is not None:
            snapshot_search = product_search if product_cards else None
            if product_cards and query_plan.references.get('source') == 'owned_session_snapshot' and snapshot and snapshot.search:
                # A detail question changes the selected object, not the original discovery requirements.
                snapshot_search = snapshot.search
            categories = {match.category_name for match in product_matches if match.category_name}
            if snapshot_search is not None and not snapshot_search.category and len(categories) == 1:
                snapshot_search = snapshot_search.model_copy(update={'category': next(iter(categories))})
            snapshot = EvidenceSnapshot(cards=product_cards, search=snapshot_search) if product_cards else None
            await blocking(self.conversation_service.repository.save_assistant,
                                    session_id, customer.customer_id, assistant_message, snapshot)
        if self.after_response is not None:
            await blocking(self.after_response, query_context, response, knowledge_chunks)
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
