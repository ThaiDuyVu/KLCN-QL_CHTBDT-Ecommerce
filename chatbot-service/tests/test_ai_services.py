import json
from datetime import datetime, timezone
from uuid import uuid4

import pytest

from app.features.query_understanding.ollama_service import OllamaQueryUnderstandingService
from app.features.response_generation.ollama_service import INSUFFICIENT, OllamaResponseGenerationService
from app.shared.contracts.context import AuthenticatedCustomer, ChatMessage, GroundedContext, QueryContext, CustomerContext, OrderContext
from app.shared.contracts.enums import Intent, MessageRole
from app.shared.contracts.knowledge import KnowledgeChunkMatch, KnowledgeSource
from app.shared.contracts.product import EligibleVariant, ProductMatch
from app.shared.contracts.query import QueryPlan
from app.shared.ollama.client import OllamaUnavailable


class FakeChat:
    def __init__(self, *outputs):
        self.outputs = iter(outputs)
        self.calls = []

    def chat(self, messages, *, schema=None):
        self.calls.append((list(messages), schema))
        output = next(self.outputs)
        if isinstance(output, Exception):
            raise output
        return output


def query_context(message="Còn loại rẻ hơn không?"):
    return QueryContext(customer=AuthenticatedCustomer(user_id=uuid4(), customer_id=uuid4(), display_name="private-name"), session_id=uuid4(), current_message=message,
        recent_messages=[ChatMessage(role=MessageRole.USER, content="Laptop RAM 16GB dưới 20 triệu", created_at=datetime.now(timezone.utc))])


def knowledge_match():
    return KnowledgeChunkMatch(chunk_id=uuid4(), chunk_index=0, content="Đổi trả trong 7 ngày theo điều kiện chính sách.", similarity_score=0.9,
        source=KnowledgeSource(document_id=uuid4(), title="Đổi trả", source="policy.md", document_type="POLICY"))


@pytest.mark.parametrize("output,intent", [
    ({"intent": "GREETING"}, Intent.GREETING),
    ({"intent": "UNKNOWN"}, Intent.UNKNOWN),
    ({"intent": "KNOWLEDGE_QA", "semantic_query": "chính sách đổi trả"}, Intent.KNOWLEDGE_QA),
    ({"intent": "PRODUCT_DISCOVERY", "requested_fields": ["PRICE", "STOCK"], "product_search": {"category": "Laptop", "max_price": "20000000", "min_ram_gb": 16}}, Intent.PRODUCT_DISCOVERY),
])
def test_valid_typed_plans(output, intent):
    model = FakeChat(json.dumps(output))
    plan = OllamaQueryUnderstandingService(model).understand(query_context())
    assert plan.intent == intent
    if plan.product_search:
        assert plan.product_search.min_ram_gb == 16
        assert plan.product_search.max_price == 20000000


@pytest.mark.parametrize("output", [
    "bad json", '{"intent":"UNSUPPORTED"}', '{"intent":"PRODUCT_DISCOVERY"}',
    '{"intent":"PRODUCT_DISCOVERY","product_search":{"min_price":10,"max_price":1}}',
    '{"intent":"KNOWLEDGE_QA","sql":"DROP TABLE knowledge_chunks"}',
    '{"intent":"UNKNOWN","requested_fields":["FAKE"]}',
    '{"intent":"PRODUCT_DISCOVERY","product_search":{"top_k":21}}',
])
def test_invalid_plans_bounded_fallback(output):
    model = FakeChat(output, output)
    assert OllamaQueryUnderstandingService(model).understand(query_context()).intent == Intent.UNKNOWN
    assert len(model.calls) == 2


def test_repair_can_succeed():
    model = FakeChat("invalid", '{"intent":"GREETING"}')
    assert OllamaQueryUnderstandingService(model).understand(query_context()).intent == Intent.GREETING


def test_failure_returns_unknown_without_retry():
    model = FakeChat(OllamaUnavailable("offline"))
    assert OllamaQueryUnderstandingService(model).understand(query_context()).intent == Intent.UNKNOWN
    assert len(model.calls) == 1


def test_context_window_preserves_current_message_omits_identity():
    context = query_context()
    model = FakeChat('{"intent":"UNKNOWN"}')
    OllamaQueryUnderstandingService(model, history_window=1).understand(context)
    messages, schema = model.calls[0]
    data = json.loads(messages[1]["content"])
    assert data["current_message"] == context.current_message
    assert data["recent_messages"][0]["content"] == context.recent_messages[0].content
    assert str(context.customer.user_id) not in str(messages)
    assert context.customer.display_name not in str(messages)
    assert schema == QueryPlan.model_json_schema()


def test_history_changes_interpretation_with_same_current_message():
    class HistoryAwareChat:
        def chat(self, messages, *, schema=None):
            data = json.loads(messages[1]["content"])
            if data["recent_messages"]:
                return '{"intent":"PRODUCT_DISCOVERY","product_search":{"category":"Laptop","max_price":15000000}}'
            return '{"intent":"UNKNOWN"}'
    context = query_context()
    assert OllamaQueryUnderstandingService(HistoryAwareChat()).understand(context).intent == Intent.PRODUCT_DISCOVERY
    assert OllamaQueryUnderstandingService(HistoryAwareChat(), history_window=0).understand(context).intent == Intent.UNKNOWN


def test_no_evidence_skips_llm_even_with_history_or_empty_customer():
    model = FakeChat()
    ctx = GroundedContext(query_plan=QueryPlan(intent=Intent.ORDER_STATUS), user_query="Đơn của tôi?", relevant_history=query_context().recent_messages, customer_context=CustomerContext())
    assert OllamaResponseGenerationService(model).generate(ctx) == INSUFFICIENT
    assert model.calls == []


@pytest.mark.parametrize("evidence", ["products", "knowledge", "customer"])
def test_response_uses_exact_grounded_data(evidence):
    ctx = GroundedContext(query_plan=QueryPlan(intent=Intent.KNOWLEDGE_QA), user_query="Thông tin?")
    if evidence == "knowledge":
        ctx.knowledge_chunks = [knowledge_match()]
    elif evidence == "products":
        product_id = uuid4()
        ctx.products = [ProductMatch(product_id=product_id, eligible_variants=[EligibleVariant(product_id=product_id, variant_id=uuid4(), sku="SKU1", original_price=100, effective_price=90)])]
    else:
        ctx.customer_context = CustomerContext(recent_orders=[OrderContext(order_id=uuid4(), order_code="ORD1", order_date=datetime.now(timezone.utc), status="PENDING", total_amount=90)])
    model = FakeChat("Câu trả lời dựa trên dữ liệu.")
    assert OllamaResponseGenerationService(model).generate(ctx) == "Câu trả lời dựa trên dữ liệu."
    payload = json.loads(model.calls[0][0][1]["content"])
    assert payload == ctx.model_dump(mode="json")
    assert "Không tự suy đoán" in model.calls[0][0][0]["content"]


def test_generation_failure_is_controlled():
    ctx = GroundedContext(query_plan=QueryPlan(intent=Intent.KNOWLEDGE_QA), user_query="Đổi trả?", knowledge_chunks=[knowledge_match()])
    result = OllamaResponseGenerationService(FakeChat(OllamaUnavailable("offline"))).generate(ctx)
    assert "tạm thời không khả dụng" in result
