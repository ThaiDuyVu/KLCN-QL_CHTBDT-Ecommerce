"""Phase 2 cart hotfix: a never-created cart is a valid empty state, not an upstream error.

GET /api/cart (CartServiceImpl.get -> response(current(userId, false))) returns 200 with
cartId=null, items=[] and subtotal=0 for a customer without a cart; it never creates one.
Tests use only pre-hotfix entry points so the 502 reproduction is an assertion failure.
"""
import asyncio
from uuid import uuid4

import httpx
import pytest
from fastapi import HTTPException

from app.features.chat.models import SendMessageRequest
from app.features.chat.service import ChatService
from app.features.customer_context.service import CustomerContextService
from app.features.response_generation.ollama_service import INSUFFICIENT, OllamaResponseGenerationService
from app.shared.backend.client import BackendClient

EMPTY = 'Giỏ hàng của bạn hiện đang trống.'
CART_ID, VARIANT_ID = str(uuid4()), str(uuid4())
UNINITIALIZED = {'cartId': None, 'warehouseId': None, 'warehouseName': None, 'items': [],
                 'subtotal': 0, 'originalSubtotal': 0, 'discountAmount': 0}


def item(**overrides):
    return {'cartItemId': str(uuid4()), 'variantId': VARIANT_ID, 'productName': 'Xiaomi 15', 'sku': 'DEV-X15-BLK-256',
            'quantity': 2, 'unitPrice': 18990000, 'effectivePrice': 18990000, 'lineTotal': 37980000,
            'availableQuantity': 3, **overrides}


class NoModel:
    def chat(self, *args, **kwargs):
        raise AssertionError('Cart answers must be deterministic, never model-generated')


class Recorder:
    def __init__(self, status, payload=None, error=None):
        self.status, self.payload, self.error, self.requests = status, payload, error, []

    def __call__(self, request):
        self.requests.append(request)
        if self.error:
            raise self.error
        return httpx.Response(self.status, json=self.payload)


def ask(recorder, message='Cho tôi xem giỏ hàng của mình.'):
    client = BackendClient('http://synthetic', httpx.MockTransport(recorder))
    service = ChatService(customer_context_service=CustomerContextService(client),
                          response_generation=OllamaResponseGenerationService(NoModel()))
    return service.send_message(uuid4(), SendMessageRequest(message=message), cookies={'synthetic': 'owner-session'})


def assert_read_only_owned(recorder):
    assert [(r.method, r.url.path) for r in recorder.requests] == [('GET', '/api/cart')]
    assert all(r.headers.get('cookie') == 'synthetic=owner-session' for r in recorder.requests)
    assert all(not r.url.params for r in recorder.requests)


def test_case_a_never_created_cart_is_empty_not_502():
    recorder = Recorder(200, UNINITIALIZED)
    response = ask(recorder)
    assert response.message == EMPTY and response.products == []
    assert_read_only_owned(recorder)


def test_nx37_uninitialized_cart_contract_variant_without_warehouse_fields():
    # NX-37 re-based on the verified contract: 200 + cartId=null (the plan assumed 404/204).
    recorder = Recorder(200, {'cartId': None, 'items': [], 'subtotal': '0.00'})
    assert ask(recorder).message == EMPTY
    assert_read_only_owned(recorder)


def test_case_a_context_has_no_invented_cart():
    client = BackendClient('http://synthetic', httpx.MockTransport(Recorder(200, UNINITIALIZED)))
    try:
        context = asyncio.run(CustomerContextService(client).load_context('CART_STATUS', {'synthetic': 'owner-session'}))
    except Exception as exc:
        # An internal verified-empty outcome is acceptable; an upstream error is not.
        assert not isinstance(exc, HTTPException)
    else:
        assert context.cart is None or context.cart.items == []


def test_case_b_existing_empty_cart_keeps_its_id():
    payload = {**UNINITIALIZED, 'cartId': CART_ID}
    assert ask(Recorder(200, payload)).message == EMPTY
    client = BackendClient('http://synthetic', httpx.MockTransport(Recorder(200, payload)))
    context = asyncio.run(CustomerContextService(client).load_context('CART_STATUS', {'synthetic': 'owner-session'}))
    assert str(context.cart.cart_id) == CART_ID and context.cart.items == []


def test_case_c_cart_items_come_from_backend_unchanged():
    payload = {**UNINITIALIZED, 'cartId': CART_ID, 'items': [item()], 'subtotal': 37980000}
    recorder = Recorder(200, payload)
    message = ask(recorder).message
    assert 'Xiaomi 15 (DEV-X15-BLK-256): 2 sản phẩm' in message and '18990000' in message
    assert 'Tạm tính: 37980000 VND' in message
    assert 'tồn' not in message and 'khuyến mãi' not in message
    assert_read_only_owned(recorder)


@pytest.mark.parametrize('status', [500, 502, 503])
def test_case_d_backend_failure_is_not_an_empty_cart(status):
    with pytest.raises(HTTPException) as exc:
        ask(Recorder(status, {'message': 'down'}))
    assert exc.value.status_code == 502


@pytest.mark.parametrize('error', [httpx.ReadTimeout('cart timeout'), httpx.ConnectError('refused')])
def test_case_d_transport_failure_is_not_an_empty_cart(error):
    with pytest.raises(HTTPException) as exc:
        ask(Recorder(200, error=error))
    assert exc.value.status_code == 503


@pytest.mark.parametrize('payload', [
    {**UNINITIALIZED, 'items': [item()]},                    # no cart identity but items present
    {**UNINITIALIZED, 'subtotal': 1000},                     # no cart, non-zero subtotal
    {**UNINITIALIZED, 'items': None},                        # items missing/invalid
    {k: v for k, v in UNINITIALIZED.items() if k != 'items'},
    {**UNINITIALIZED, 'cartId': CART_ID, 'items': [item(variantId=None)]},
    {**UNINITIALIZED, 'cartId': CART_ID, 'items': [item(effectivePrice=-1)]},
    {**UNINITIALIZED, 'cartId': 'not-a-uuid'},
])
def test_case_e_invalid_dto_is_never_confirmed_empty(payload):
    with pytest.raises(HTTPException) as exc:
        ask(Recorder(200, payload))
    assert exc.value.status_code == 502


def test_case_e_legacy_array_shape_is_not_a_verified_empty_cart():
    # get_cart wraps a bare JSON array as {"items": [...]} without cartId; that is not the CartResponse contract.
    with pytest.raises(HTTPException) as exc:
        ask(Recorder(200, []))
    assert exc.value.status_code == 502


@pytest.mark.parametrize('status', [404, 204])
def test_case_f_404_204_are_not_reinterpreted(status):
    assert ask(Recorder(status, None)).message == INSUFFICIENT


@pytest.mark.parametrize('status', [401, 403])
def test_case_g_auth_failures_are_preserved(status):
    with pytest.raises(HTTPException) as exc:
        ask(Recorder(status, None))
    assert exc.value.status_code == status


def test_case_g_without_customer_cookie_no_backend_call():
    recorder = Recorder(200, UNINITIALIZED)
    client = BackendClient('http://synthetic', httpx.MockTransport(recorder))
    service = ChatService(customer_context_service=CustomerContextService(client),
                          response_generation=OllamaResponseGenerationService(NoModel()))
    message = service.send_message(uuid4(), SendMessageRequest(message='Giỏ hàng của tôi có gì?')).message
    assert recorder.requests == [] and message != EMPTY


def test_cart_mutation_request_is_refused_before_cart_read():
    recorder = Recorder(200, UNINITIALIZED)
    message = ask(recorder, 'Thêm Xiaomi 15 vào giỏ hàng giúp tôi.').message
    assert 'không thể thực hiện giao dịch' in message and recorder.requests == []
