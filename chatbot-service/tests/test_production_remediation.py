"""Production-readiness Phase 1 (NX) regressions; synthetic fixtures only.

Each NX test goes through an entry point that already existed at f99f713 (normalizer,
resolver, price_slots, ChatService, advisor, FastAPI app) so a bug-reproduction failure
there is an assertion about behaviour, not a missing import. DR-dependent cases assert
only the PENDING fail-safe; their approved behaviour is NOT_RUN_PENDING_DECISION.
"""
import asyncio
import json
import logging
import time
from contextlib import contextmanager
from datetime import datetime, timezone
from decimal import Decimal
from threading import Event
from uuid import uuid4

import httpx
import psycopg
import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.features.chat.models import SendMessageRequest
from app.features.chat.policy import read_only_plan
from app.features.chat.service import ChatService
from app.features.conversation.models import EvidenceSnapshot
from app.features.conversation.references import resolve_reference
from app.features.conversation.repository import InMemoryConversationRepository
from app.features.conversation.service import ConversationService
from app.features.customer_context.models import AuthenticatedCustomer
from app.features.customer_context.service import CustomerContextService
from app.features.product_advisor.real_service import RealProductAdvisorService
from app.features.product_advisor.semantic import ProductSemanticIndex
from app.features.query_understanding.normalization import DomainNormalizer, folded, price_slots
from app.features.query_understanding.ollama_service import OllamaQueryUnderstandingService
from app.features.response_generation.ollama_service import INSUFFICIENT, OllamaResponseGenerationService
from app.shared.backend.client import BackendClient
from app.shared.config import get_settings
from app.shared.contracts.context import ChatMessage
from app.shared.contracts.enums import Intent, MessageRole
from app.shared.contracts.query import ProductSearchPlan, QueryPlan
from app.shared.contracts.response import ProductCard
from app.shared.tracing import TraceStore, capture, sanitize
from tests.test_ai_services import FakeChat
from tests.test_query_product_v2 import Backend, context
from tests.test_real_product_advisor import FakeBackend, FakeEmbedder, FakeIndex, PRODUCT, WAREHOUSE

# The existing user-facing clarification reused while DR-13b (new wording) is pending.
EXISTING_CLARIFICATION = 'Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?'
MILLION = Decimal('1000000')


def normalized(message, history=False):
    ctx = context(message)
    if history:
        now = datetime.now(timezone.utc)
        ctx.recent_messages = [ChatMessage(role=MessageRole.USER, content='Tìm sản phẩm trước đó.', created_at=now),
                               ChatMessage(role=MessageRole.ASSISTANT, content='Đây là gợi ý trước.', created_at=now)]
    return DomainNormalizer(Backend()).normalize(QueryPlan(intent=Intent.UNKNOWN), ctx)


def pipeline(message, snapshot, history=True):
    """Same order as ChatService: normalizer (model output ignored) then owned-snapshot resolver."""
    return resolve_reference(message, normalized(message, history), snapshot)


def cards(count, category, brands, first_price, max_price=None, min_ram=None):
    return EvidenceSnapshot(
        cards=[ProductCard(product_id=uuid4(), variant_id=uuid4(), product_name=f'Synthetic {i}', sku=f'SYN-{i}',
                           effective_price=first_price + i * MILLION) for i in range(count)],
        search=ProductSearchPlan(category=category, brands=brands, max_price=max_price, min_ram_gb=min_ram))


def phones(first_price=17 * MILLION):
    return cards(2, 'Điện thoại', ['Apple'], first_price)


def laptops(first_price=15990000):
    return cards(2, 'Laptop', [], Decimal(first_price), max_price=20 * MILLION, min_ram=16)


def no_min(plan, value):
    return plan.product_search is None or plan.product_search.min_price != value


# ---------------------------------------------------------------- Group A: price (A1/A2)

def test_nx01_cheaper_than_amount_is_never_a_minimum():
    # BR at the parser: f99f713 read "rẻ hơn 15 triệu" as min_price=15M. DR-02: strictly below the amount.
    assert price_slots(folded('laptop rẻ hơn 15 triệu')) == (None, 15 * MILLION - 1)
    plan = normalized('Tìm laptop rẻ hơn 15 triệu')
    assert plan.clarification is None and no_min(plan, 15 * MILLION)
    assert plan.product_search.max_price == 15 * MILLION - 1


def test_nx02_lower_than_amount_is_a_strict_maximum():
    plan = normalized('Tìm laptop giá thấp hơn 20 triệu')
    assert no_min(plan, 20 * MILLION)
    assert plan.clarification is None and plan.product_search.max_price == 20 * MILLION - 1


@pytest.mark.parametrize('phrase', ['hơn 20 triệu', 'đắt hơn 20 triệu', 'trên 20 triệu'])
def test_nx03_more_than_amount_stays_minimum(phrase):
    assert price_slots(folded('laptop ' + phrase)) == (20 * MILLION, None)
    # "laptop hơn ..." without "giá" hits the Fix01 unknown-brand slot on both HEADs; keep that guard.
    search = normalized('Tìm laptop giá ' + phrase).product_search
    assert (search.category, search.min_price, search.max_price) == ('Laptop', 20 * MILLION, None)


@pytest.mark.parametrize('prompt,low,high', [
    ('Tìm điện thoại 10 triệu trở xuống', None, 10 * MILLION),
    ('Tìm điện thoại 10 triệu đổ lại', None, 10 * MILLION),
    ('Tìm điện thoại 10 triệu trở lên', 10 * MILLION, None),
])
def test_nx04_suffix_bounds_are_read(prompt, low, high):
    search = normalized(prompt).product_search
    assert (search.category, search.min_price, search.max_price) == ('Điện thoại', low, high)


@pytest.mark.parametrize('prompt', ['Tìm laptop giá 15–20 triệu', 'Tìm laptop 15 đến 20 triệu', 'Tìm laptop giá 15-20 triệu'])
def test_nx05_unit_ranges_are_read(prompt):
    search = normalized(prompt).product_search
    assert (search.min_price, search.max_price) == (15 * MILLION, 20 * MILLION)


def test_nx06_existing_range_and_contradiction_control():
    search = normalized('Tìm laptop giá từ 15 đến 20tr').product_search
    assert (search.min_price, search.max_price) == (15 * MILLION, 20 * MILLION)
    t14 = normalized('Tìm laptop giá từ 25 triệu đến 20 triệu.')
    assert t14.product_search is None and 'mâu thuẫn' in t14.clarification


@pytest.mark.parametrize('prompt', ['Tìm laptop tầm 15 triệu', 'Tìm laptop khoảng 15 triệu', 'Tìm laptop cỡ 15 triệu',
                                    'Tìm laptop giá khoảng 15 triệu', 'Tìm laptop giá tầm 15 triệu'])
def test_nx07_approximate_amount_is_a_band_around_the_amount(prompt):
    # DR-01: "khoảng/tầm/cỡ X" = X ± 15%, ranked by closeness to X.
    search = normalized(prompt).product_search
    assert (search.min_price, search.max_price, search.target_price) == (Decimal('12750000'), Decimal('17250000'), 15 * MILLION)


@pytest.mark.parametrize('prompt', ['Tìm laptop ngân sách 20tr', 'Tìm laptop với ngân sách 20tr'])
def test_nx08_budget_amount_is_a_maximum(prompt):
    search = normalized(prompt).product_search
    assert (search.min_price, search.max_price, search.target_price) == (None, 20 * MILLION, None)


@pytest.mark.parametrize('prompt,check', [
    ('Xiaomi 15 giá bao nhiêu', lambda s: [t.product_name for t in s.targets] == ['Xiaomi 15']),
    ('So sánh Dell XPS 13 và Dell Inspiron 15', lambda s: [t.product_name for t in s.targets] == ['Dell XPS 13', 'Dell Inspiron 15']),
    ('Chỉ gợi ý đúng 2 điện thoại Xiaomi', lambda s: (s.top_k, s.brands, s.min_price, s.max_price) == (2, ['Xiaomi'], None, None)),
    ('Xiaomi 15 bản 12GB/256GB', lambda s: [(t.ram_gb, t.storage_gb) for t in s.targets] == [(12, 256)]),
    ('Tìm laptop dưới 20 triệu, CPU Intel i5', lambda s: (s.cpu_keywords, s.max_price) == (['i5'], 20 * MILLION)),
])
def test_nx09_model_numbers_capacity_count_cpu_are_not_money(prompt, check):
    plan = normalized(prompt)
    assert not plan.clarification and check(plan.product_search)


def test_nx10_zero_price_bound_is_kept():
    assert price_slots(folded('Tìm điện thoại giá tối đa 0 đồng.')) == (None, Decimal('0'))
    search = normalized('Tìm điện thoại giá tối đa 0 đồng.').product_search
    assert search.max_price is not None and search.max_price == Decimal('0') and search.min_price is None


# ---------------------------------------------------------------- Group A: references (A3)

def test_nx11_new_scope_without_price_does_not_reuse_phone_snapshot():
    plan = pipeline('Có laptop Dell nào rẻ hơn không?', phones())
    assert plan.product_search is None and plan.clarification == EXISTING_CLARIFICATION


def test_nx12_first_time_phrase_is_not_an_ordinal_reference():
    plan = pipeline('Đây là lần đầu tiên tôi mua laptop, tư vấn laptop dưới 20 triệu', laptops())
    assert plan.intent == Intent.PRODUCT_DISCOVERY and not plan.product_search.targets
    assert (plan.product_search.category, plan.product_search.max_price) == ('Laptop', 20 * MILLION)


def test_nx13_weekday_is_not_an_ordinal_reference():
    plan = pipeline('Thứ hai tuần sau có hàng laptop Dell không?', laptops())
    assert plan.intent != Intent.PRODUCT_DETAIL
    assert plan.product_search is None or not plan.product_search.targets


def test_nx14_explicit_cheaper_amount_refines_the_snapshot_search():
    # The stated amount is the bound (never a minimum); the product type comes from the owned snapshot.
    plan = pipeline('Có máy nào rẻ hơn 18 triệu không?', laptops(first_price=25 * MILLION))
    assert no_min(plan, 18 * MILLION) and plan.clarification is None
    assert (plan.product_search.category, plan.product_search.max_price) == ('Laptop', 18 * MILLION - 1)


def test_nx15_cheaper_ceiling_and_fresh_session_controls():
    plan = pipeline('Có loại rẻ hơn không?', laptops())
    assert plan.product_search.max_price == Decimal('15989999') and plan.product_search.min_ram_gb == 16
    assert plan.references['baseline'] == 'historical_display_price'
    plan = pipeline('Có chiếc nào rẻ hơn mà RAM vẫn 16GB không?', laptops())
    assert (plan.product_search.category, plan.product_search.min_ram_gb) == ('Laptop', 16)
    fresh = pipeline('Có loại rẻ hơn không?', None, history=False)
    assert fresh.clarification and fresh.product_search is None


def test_nx16_lower_reference_uses_the_same_resolver():
    plan = pipeline('Có loại thấp hơn không?', laptops())
    assert plan.intent == Intent.PRODUCT_DISCOVERY and plan.product_search.max_price == Decimal('15989999')


@pytest.mark.parametrize('prompt,index', [('Máy đầu tiên còn hàng không?', 0), ('Cái thứ hai có giá bao nhiêu?', 1),
                                          ('Máy đầu tiên có giá bao nhiêu?', 0)])
def test_nx17_ordinal_references_still_resolve(prompt, index):
    snapshot = laptops()
    plan = pipeline(prompt, snapshot)
    assert plan.product_search.targets[0].variant_id == snapshot.cards[index].variant_id
    assert plan.references == {'source': 'owned_session_snapshot'}


def test_nx17_last_reference_still_resolves_single_card():
    snapshot = cards(1, 'Laptop', [], 15 * MILLION)
    plan = pipeline('Chiếc máy vừa gợi ý giá bao nhiêu?', snapshot)
    assert plan.product_search.targets[0].variant_id == snapshot.cards[0].variant_id


def test_nx18_new_scope_with_cheaper_amount_failsafe():
    # Shown prices above 18M leave room for a wrong 18M minimum inside the old phone scope.
    plan = pipeline('Có laptop Dell nào rẻ hơn 18 triệu không?', phones(first_price=25 * MILLION))
    assert no_min(plan, 18 * MILLION)
    assert plan.product_search is None or plan.product_search.category != 'Điện thoại'


def test_explicit_minimum_survives_cheaper_follow_up():
    # The removed normalizer patch would have dropped a stated "trên X" on a cheaper follow-up.
    plan = pipeline('Có loại rẻ hơn mà vẫn trên 10 triệu không?', laptops())
    assert (plan.product_search.min_price, plan.product_search.max_price) == (10 * MILLION, Decimal('15989999'))


def test_ordinal_next_to_new_brand_is_a_new_request():
    plan = pipeline('Máy Dell đầu tiên nào dưới 20 triệu?', phones())
    assert plan.intent == Intent.PRODUCT_DISCOVERY and plan.product_search.brands == ['Dell']


# ---------------------------------------------------------------- Group B: ranking (B3)

class FailingEmbedder(FakeEmbedder):
    def __init__(self, error):
        self.error = error

    def embed(self, text):
        raise self.error


def ranking_outcome(advisor):
    trace = TraceStore().create(uuid4(), uuid4(), uuid4())
    with capture(trace):
        matches = advisor.search(ProductSearchPlan(category='Laptop', semantic_query='laptop lập trình'))
        built = advisor.build_cards(matches)
    events = [e for e in trace.snapshot()['events'] if e['name'] == 'product.ranking_degraded']
    return matches, built, events


@pytest.mark.parametrize('error', [
    httpx.HTTPStatusError('embed', request=httpx.Request('POST', 'http://ollama/api/embed'), response=httpx.Response(500)),
    httpx.ReadTimeout('embed timeout'),
    ValueError('Embedding model returned an empty vector'),
])
def test_nx23_embedding_failure_degrades_ranking_only(error):
    matches, built, events = ranking_outcome(RealProductAdvisorService(FakeBackend(), WAREHOUSE, FakeIndex(), FailingEmbedder(error)))
    assert [m.product_id for m in matches] == [PRODUCT] and len(built) == 1
    assert built[0].effective_price == Decimal('12000000') and matches[0].semantic_score is None
    assert len(events) == 1 and events[0]['status'] == 'warning'


def test_nx24_index_database_failure_degrades_without_leaking_driver_message(monkeypatch):
    index = ProductSemanticIndex('postgresql://synthetic-user:synthetic-secret@127.0.0.1:9/none')
    def refuse():
        raise psycopg.OperationalError('connection timeout synthetic-secret')
    monkeypatch.setattr(index, '_connect', refuse)
    matches, built, events = ranking_outcome(RealProductAdvisorService(FakeBackend(), WAREHOUSE, index, FakeEmbedder()))
    assert len(matches) == 1 and len(built) == 1 and len(events) == 1
    assert 'synthetic-secret' not in json.dumps(events, default=str)


def test_index_connections_are_time_bounded(monkeypatch):
    seen = {}
    def connect(url, **kwargs):
        seen.update(kwargs)
        raise psycopg.OperationalError('refused')
    monkeypatch.setattr(psycopg, 'connect', connect)
    with pytest.raises(Exception):
        ProductSemanticIndex('postgresql://synthetic/none').scores([PRODUCT], [0.1], 'fake-model')
    assert seen.get('connect_timeout') == 5


def product_chat(advisor):
    plan = '{"intent":"PRODUCT_DISCOVERY","product_search":{"category":"Laptop","semantic_query":"laptop"}}'
    return ChatService(product_advisor=advisor, query_understanding=OllamaQueryUnderstandingService(FakeChat(plan)),
                       response_generation=OllamaResponseGenerationService(FakeChat()))


def test_nx24_pipeline_returns_verified_cards_when_index_is_down(monkeypatch):
    index = ProductSemanticIndex('postgresql://synthetic/none')
    monkeypatch.setattr(index, '_connect', lambda: (_ for _ in ()).throw(psycopg.OperationalError('down')))
    response = product_chat(RealProductAdvisorService(FakeBackend(), WAREHOUSE, index, FakeEmbedder())).send_message(
        uuid4(), SendMessageRequest(message='Laptop lập trình'))
    assert [card.product_id for card in response.products] == [PRODUCT] and 'RAM 16GB, bộ nhớ 1TB' in response.message


def test_nx25_structured_search_failure_stays_unavailable():
    class Down(FakeBackend):
        def search(self, *args, **kwargs):
            raise httpx.ConnectError('backend down')
    with pytest.raises(HTTPException) as exc:
        product_chat(RealProductAdvisorService(Down(), WAREHOUSE, FakeIndex(), FakeEmbedder())).send_message(
            uuid4(), SendMessageRequest(message='Laptop lập trình'))
    assert exc.value.status_code == 503


@pytest.mark.parametrize('body', [{'message': 'Xung đột khác'},
                                  {'message': 'Product có nhiều Promotion hợp lệ; cần xử lý overlap trước khi bán'}])
def test_nx30_detail_conflicts_stay_unavailable(body):
    # B4 is deferred (DR-13a): even the verified promotion-overlap body keeps the existing 503.
    class Conflict(FakeBackend):
        def detail(self, product_id, warehouse_id=None):
            raise httpx.HTTPStatusError('conflict', request=httpx.Request('GET', 'http://backend/detail'),
                                        response=httpx.Response(409, json=body))
    with pytest.raises(HTTPException) as exc:
        product_chat(RealProductAdvisorService(Conflict(), WAREHOUSE, FakeIndex(), FakeEmbedder())).send_message(
            uuid4(), SendMessageRequest(message='Laptop lập trình'))
    assert exc.value.status_code == 503


# ---------------------------------------------------------------- Group C: runtime (C1/C2)

@pytest.fixture
def restore_app_state():
    from app.main import app
    previous = app.state.chat_service
    yield app
    app.state.chat_service = previous
    app.dependency_overrides.clear()
    get_settings.cache_clear()


def test_nx31_production_lifespan_refuses_mock_memory_without_printing_values(monkeypatch, restore_app_state):
    monkeypatch.setenv('APP_ENV', 'production')
    monkeypatch.setenv('CONVERSATION_DATABASE_URL', 'postgresql://synthetic:SYNTHETIC-SECRET@127.0.0.1:9/chat')
    get_settings.cache_clear()
    with pytest.raises(ValueError) as exc:
        with TestClient(restore_app_state):
            pass
    message = str(exc.value)
    for name in ('CHATBOT_AI_ENABLED', 'PRODUCT_ADVISOR_MODE', 'CONVERSATION_STORAGE', 'PRODUCT_INDEX_DATABASE_URL'):
        assert name in message
    assert 'SYNTHETIC-SECRET' not in message and 'postgresql://' not in message


def production_settings(**overrides):
    from app.shared.config import Settings
    values = dict(app_env='production', chatbot_ai_enabled=True, product_advisor_mode='real',
                  conversation_storage='postgres', conversation_database_url='postgresql://s:SECRET@h/c',
                  product_index_database_url='postgresql://s:SECRET@h/i')
    return Settings(_env_file=None, **{**values, **overrides})


@pytest.mark.parametrize('override,name', [
    ({'chatbot_ai_enabled': False}, 'CHATBOT_AI_ENABLED'), ({'product_advisor_mode': 'mock'}, 'PRODUCT_ADVISOR_MODE'),
    ({'conversation_storage': 'memory'}, 'CONVERSATION_STORAGE'), ({'conversation_database_url': None}, 'CONVERSATION_DATABASE_URL'),
    ({'product_index_database_url': ''}, 'PRODUCT_INDEX_DATABASE_URL'), ({'chatbot_trace_enabled': True}, 'CHATBOT_TRACE_ENABLED'),
    ({'chatbot_trace_file_enabled': True}, 'CHATBOT_TRACE_FILE_ENABLED'),
])
def test_nx31_validator_matrix_names_only_the_variable(override, name):
    from app.shared.config import validate_runtime_settings
    with pytest.raises(ValueError) as exc:
        validate_runtime_settings(production_settings(**override))
    assert str(exc.value).endswith(name) and 'SECRET' not in str(exc.value)


def test_validator_accepts_complete_production_and_any_development_profile():
    from app.shared.config import Settings, validate_runtime_settings
    validate_runtime_settings(production_settings())
    for env in ('development', 'local', 'test'):
        validate_runtime_settings(Settings(_env_file=None, app_env=env))


def test_nx32_implicit_app_env_warns_without_secret_and_still_starts(monkeypatch, caplog, restore_app_state):
    import app.main as main
    from app.shared.config import Settings
    monkeypatch.delenv('APP_ENV', raising=False)
    monkeypatch.setenv('CONVERSATION_DATABASE_URL', 'postgresql://synthetic:SYNTHETIC-SECRET@127.0.0.1:9/chat')
    settings = Settings(_env_file=None)
    monkeypatch.setattr(main, 'get_settings', lambda: settings)
    with caplog.at_level(logging.WARNING, logger='chatbot.runtime'):
        with TestClient(restore_app_state) as http:
            assert http.get('/api/v1/health').status_code == 200
    record = next(r.getMessage() for r in caplog.records if r.name == 'chatbot.runtime')
    payload = json.loads(record.split(' ', 1)[1])
    assert payload['app_env_explicit'] is False and payload['app_env'] == 'development'
    assert {'ai_disabled', 'product_advisor_mock', 'conversation_memory', 'app_env_implicit'} <= set(payload['warnings'])
    assert 'SYNTHETIC-SECRET' not in record


class GreetingUnderstanding:
    def understand(self, ctx):
        return QueryPlan(intent=Intent.GREETING)


class NoModel:
    def chat(self, *args, **kwargs):
        raise AssertionError('Unexpected generation')


def test_nx34_slow_repository_does_not_block_event_loop(restore_app_state):
    from app.features.chat.router import get_authenticated_customer
    class SlowConversation(ConversationService):
        # Slow I/O outside the in-memory lock, like a per-call PostgreSQL connection.
        def get_session(self, session_id, customer_id):
            time.sleep(2)
            return super().get_session(session_id, customer_id)
    repo = InMemoryConversationRepository()
    customer = AuthenticatedCustomer(user_id=uuid4(), customer_id=uuid4(), display_name='Synthetic')
    restore_app_state.state.chat_service = ChatService(conversation_service=SlowConversation(repo),
        query_understanding=GreetingUnderstanding(), response_generation=OllamaResponseGenerationService(NoModel()))
    restore_app_state.dependency_overrides[get_authenticated_customer] = lambda: customer
    sessions = [uuid4() for _ in range(5)]

    async def run():
        transport = httpx.ASGITransport(app=restore_app_state)
        async with httpx.AsyncClient(transport=transport, base_url='http://chatbot') as http:
            async def probe():
                await asyncio.sleep(0.2)
                return await http.get('/api/v1/health'), time.monotonic()
            # The clock starts before the posts, so a frozen loop cannot hide inside the probe's own wait.
            started = time.monotonic()
            posts = [asyncio.create_task(http.post(f'/api/v1/chat/sessions/{s}/messages', json={'message': f'Xin chào {i}'}))
                     for i, s in enumerate(sessions)]
            health, finished = await asyncio.create_task(probe())
            return health, finished - started, await asyncio.gather(*posts)

    health, latency, responses = asyncio.run(run())
    # Each POST spends 2s in get_session; liveness must answer while all five are in flight.
    assert health.status_code == 200 and latency < 1.0
    assert [r.status_code for r in responses] == [200] * 5
    for i, s in enumerate(sessions):
        history = repo.history_page(s, customer.customer_id)
        assert [(m.sender_type.value, m.message) for m in history][0] == ('USER', f'Xin chào {i}')
        assert [m.sender_type.value for m in history] == ['USER', 'ASSISTANT']


@pytest.mark.asyncio
async def test_nx35_cancel_during_user_write_releases_lease_after_write_settles():
    started, release = Event(), Event()
    class LeaseRepository(InMemoryConversationRepository):
        events = []
        @contextmanager
        def request_lock(self, session_id, customer_id):
            self.events.append('acquire')
            try:
                yield
            finally:
                self.events.append('release')
        def add_message(self, *args, **kwargs):
            message = super().add_message(*args, **kwargs)
            started.set()
            release.wait(3)
            self.events.append('user_written')
            return message
    repo = LeaseRepository()
    customer = AuthenticatedCustomer(user_id=uuid4(), customer_id=uuid4(), display_name='Synthetic')
    session = repo.create_session(customer.customer_id).id
    service = ChatService(conversation_service=ConversationService(repo), query_understanding=GreetingUnderstanding(),
                          response_generation=OllamaResponseGenerationService(NoModel()))
    task = asyncio.create_task(service.send_message_async(session, SendMessageRequest(message='cancelled write'), customer))
    try:
        assert await asyncio.to_thread(started.wait, 3)
        task.cancel()
        await asyncio.sleep(0.05)
        assert 'release' not in repo.events
    finally:
        release.set()
    with pytest.raises(asyncio.CancelledError):
        await task
    assert repo.events.index('user_written') < repo.events.index('release') and repo.events[-1] == 'release'
    assert repo.get_context_messages(session, customer.customer_id) == []


# ---------------------------------------------------------------- Group E

def order(code, oid=None, day=1):
    return {'orderId': oid or str(uuid4()), 'orderCode': code, 'orderDate': f'2026-10-{day:02}T08:00:00Z',
            'status': 'PENDING', 'totalAmount': 100, 'warehouseId': None, 'warehouseName': None}


def order_chat(status, payload):
    client = BackendClient('http://synthetic', httpx.MockTransport(lambda r: httpx.Response(status, json=payload)))
    return ChatService(customer_context_service=CustomerContextService(client),
                       response_generation=OllamaResponseGenerationService(NoModel()))


def ask_orders(service):
    return service.send_message(uuid4(), SendMessageRequest(message='Các đơn hàng gần đây của tôi đang ở trạng thái nào?'),
                                cookies={'synthetic': 'session'}).message


def test_nx36_verified_empty_order_list_is_answered_as_no_orders():
    assert 'chưa có đơn hàng' in ask_orders(order_chat(200, {'content': [], 'totalElements': 0}))
    assert 'chưa có đơn hàng' in ask_orders(order_chat(200, []))


def test_unavailable_order_list_is_never_reported_as_empty():
    assert ask_orders(order_chat(404, None)) == INSUFFICIENT
    with pytest.raises(HTTPException) as exc:
        ask_orders(order_chat(500, {'message': 'down'}))
    assert exc.value.status_code == 502


def load(rows, code):
    client = BackendClient('http://synthetic', httpx.MockTransport(lambda r: httpx.Response(200, json=rows)))
    return asyncio.run(CustomerContextService(client).load_context('ORDER_STATUS', {'synthetic': 'session'}, order_code=code))


def test_nx39_lower_case_order_code_matches_the_single_owned_order():
    code = 'ORD-0142ABCD-3B48-48AE-8313-E0A8A8AF2F29'
    typed = read_only_plan(f'Đơn hàng {code.lower()} của tôi có trạng thái gì?').references['order_code']
    context = load([order(code), order('ORD-OTHER', day=2)], typed)
    assert [o.order_code for o in context.recent_orders] == [code]


def test_nx40_duplicate_owned_code_stays_ambiguous():
    with pytest.raises(HTTPException) as exc:
        load([order('ORD-DUP'), order('ORD-DUP', day=2)], 'ORD-DUP')
    assert exc.value.status_code == 502


def test_case_variant_duplicates_are_ambiguous_not_chosen():
    with pytest.raises(HTTPException) as exc:
        load([order('ORD-DUP'), order('ord-dup', day=2)], 'Ord-Dup')
    assert exc.value.status_code == 502


def test_nx41_status_endpoint_is_hidden_outside_development(monkeypatch, restore_app_state):
    from app.features.chat.router import get_authenticated_customer
    restore_app_state.dependency_overrides[get_authenticated_customer] = lambda: AuthenticatedCustomer(
        user_id=uuid4(), customer_id=uuid4(), display_name='Synthetic')
    monkeypatch.setenv('OLLAMA_BASE_URL', 'http://127.0.0.1:9')
    monkeypatch.setenv('APP_ENV', 'production')
    get_settings.cache_clear()
    http = TestClient(restore_app_state)
    assert http.get('/api/v1/chat/status').status_code == 404
    assert http.get('/api/v1/health').status_code == 200
    monkeypatch.setenv('APP_ENV', 'development')
    get_settings.cache_clear()
    assert http.get('/api/v1/chat/status').status_code == 200


def test_nx42_model_authored_references_are_discarded():
    model = FakeChat('{"intent":"ORDER_STATUS","references":{"order_code":"ORD-INJECTED","order_selection":"latest"}}')
    plan = OllamaQueryUnderstandingService(model).understand(context('Tình trạng mua sắm của tôi thế nào?'))
    assert plan.intent == Intent.ORDER_STATUS and plan.references == {}


class RecordingContext(CustomerContextService):
    def __init__(self):
        super().__init__(None)
        self.calls = []

    async def load_context(self, intent, cookies, **options):
        self.calls.append((intent, options))
        return await super().load_context(intent, cookies, **options)


def test_nx42_injected_code_does_not_filter_owned_context():
    recorder = RecordingContext()
    model = FakeChat('{"intent":"ORDER_STATUS","references":{"order_code":"ORD-INJECTED"}}')
    ChatService(customer_context_service=recorder, query_understanding=OllamaQueryUnderstandingService(model),
                response_generation=OllamaResponseGenerationService(NoModel())).send_message(
        uuid4(), SendMessageRequest(message='Tình trạng mua sắm của tôi thế nào?'))
    assert recorder.calls == [(Intent.ORDER_STATUS, {})]


def test_nx43_read_only_guard_still_supplies_order_code():
    message = 'Đơn hàng ORD-DIAG-NOPAY của tôi đã thanh toán chưa?'
    assert read_only_plan(message).references == {'order_code': 'ORD-DIAG-NOPAY'}
    recorder = RecordingContext()
    ChatService(customer_context_service=recorder, response_generation=OllamaResponseGenerationService(NoModel())).send_message(
        uuid4(), SendMessageRequest(message=message))
    assert recorder.calls == [(Intent.PAYMENT_STATUS, {'order_code': 'ORD-DIAG-NOPAY'})]


def test_nx44_warranty_device_identifiers_are_redacted():
    payload = {'imeiNumbers': ['356938035643809'], 'serialNumber': 'SN-SYNTH-1', 'imei_numbers': ['1'],
               'serial_number': 'SN-2', 'imei': '3', 'productName': 'Xiaomi 15'}
    cleaned = sanitize(payload)
    assert all(cleaned[k] == '[REDACTED]' for k in payload if k != 'productName')
    assert cleaned['productName'] == 'Xiaomi 15'
    assert '356938035643809' not in json.dumps(sanitize(json.dumps(payload)))
