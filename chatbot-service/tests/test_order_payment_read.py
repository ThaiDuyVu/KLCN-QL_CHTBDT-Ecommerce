"""Owned read integrity: precise existing409, no fabricated payment, no global remapping."""
from copy import deepcopy
from uuid import uuid4
import httpx
import pytest
from fastapi import HTTPException
from app.shared.backend.client import BackendClient
from app.shared.backend.errors import (
    OwnedOrderPaymentIntegrityError, PAYMENT_CARDINALITY_MESSAGE, PAYMENT_UNAVAILABLE_MESSAGE,
)
from app.features.customer_context.service import CustomerContextService
from app.features.chat.service import ChatService
from app.features.chat.models import SendMessageRequest
from app.features.response_generation.ollama_service import OllamaResponseGenerationService

OID = str(uuid4())
OWNED_PATH = '/api/orders/mine/' + OID


def order(code='ORD-READ-EXACT', day=1):
    return {'orderId': OID, 'orderCode': code, 'orderDate': f'2026-10-{day:02}T08:00:00Z',
            'status': 'DELIVERED', 'totalAmount': 100, 'warehouseId': None, 'warehouseName': None}


@pytest.mark.asyncio
async def test_proven_owned_detail409_becomes_internal_unavailable_outcome():
    client = BackendClient('http://synthetic', httpx.MockTransport(
        lambda r: httpx.Response(409, json={'message': PAYMENT_CARDINALITY_MESSAGE})))
    with pytest.raises(OwnedOrderPaymentIntegrityError) as exc:
        await client.get_order_detail(OID, {'synthetic': 'session'})
    assert str(exc.value) == PAYMENT_UNAVAILABLE_MESSAGE


@pytest.mark.asyncio
@pytest.mark.parametrize('status,payload', [
    (409, {'message': 'Payment INSTALLMENT thiếu hồ sơ trả góp'}),
    (409, {'message': 'Không thể chuyển trạng thái đơn hàng'}),
    (409, {'message': PAYMENT_CARDINALITY_MESSAGE, 'extra': 'not-the-existing-contract'}),
    (409, {'detail': PAYMENT_CARDINALITY_MESSAGE}),
    (409, [PAYMENT_CARDINALITY_MESSAGE]), (409, None),
    (500, {'message': PAYMENT_CARDINALITY_MESSAGE}),
    (502, {'message': PAYMENT_CARDINALITY_MESSAGE}),
])
async def test_unrelated_conflicts_and_servers_keep_generic502(status, payload):
    client = BackendClient('http://synthetic', httpx.MockTransport(lambda r: httpx.Response(status, json=payload)))
    with pytest.raises(HTTPException) as exc:
        await client.get_order_detail(OID, {'synthetic': 'session'})
    assert exc.value.status_code == 502


@pytest.mark.asyncio
@pytest.mark.parametrize('method,path', [
    ('POST', OWNED_PATH), ('DELETE', OWNED_PATH), ('GET', '/api/orders/' + OID),
    ('GET', '/api/orders/mine'), ('GET', '/api/cart'),
    ('GET', '/api/orders/mine/not-a-uuid'), ('GET', OWNED_PATH + '/cancel'),
])
async def test_same_message_on_other_operations_is_not_reclassified(method, path):
    client = BackendClient('http://synthetic', httpx.MockTransport(lambda r: httpx.Response(409, json={'message': PAYMENT_CARDINALITY_MESSAGE})))
    with pytest.raises(HTTPException) as exc:
        await client._request_json(method, path)
    assert exc.value.status_code == 502


@pytest.mark.asyncio
@pytest.mark.parametrize('status', [401,403,404])
async def test_owned_auth_and_not_found_states_unchanged(status):
    client = BackendClient('http://synthetic', httpx.MockTransport(lambda r: httpx.Response(status, json={'message': PAYMENT_CARDINALITY_MESSAGE})))
    if status == 404:
        assert await client.get_order_detail(OID, {}) is None
    else:
        with pytest.raises(HTTPException) as exc:
            await client.get_order_detail(OID, {})
        assert exc.value.status_code == status


@pytest.mark.asyncio
@pytest.mark.parametrize('body', ['not-json', '<html>conflict</html>', '"' + PAYMENT_CARDINALITY_MESSAGE + '"'])
async def test_malformed_conflict_body_is_not_payment_evidence(body):
    client = BackendClient('http://synthetic', httpx.MockTransport(lambda r: httpx.Response(409, text=body)))
    with pytest.raises(HTTPException) as exc:
        await client.get_order_detail(OID, {})
    assert exc.value.status_code == 502


@pytest.mark.asyncio
async def test_order_read_timeout_keeps503():
    def timeout(r):
        raise httpx.ReadTimeout('synthetic timeout', request=r)
    client = BackendClient('http://synthetic', httpx.MockTransport(timeout))
    with pytest.raises(HTTPException) as exc:
        await client.get_order_detail(OID, {})
    assert exc.value.status_code == 503


@pytest.mark.asyncio
@pytest.mark.parametrize('state', ['PENDING','PAID','FAILED',None])
async def test_valid_or_compatible_absent_payment_dto_not_inferred_from_delivered(state):
    row = order()
    row['payment'] = None if state is None else {'paymentId': str(uuid4()), 'paymentMethod': 'COD', 'status': state, 'amount': 100}
    def handler(r):
        return httpx.Response(200, json={'content': [row]} if r.url.path.endswith('/mine') else row)
    service = CustomerContextService(BackendClient('http://synthetic', httpx.MockTransport(handler)))
    ctx = await service.load_context('PAYMENT_STATUS', {'synthetic': 'session'}, order_code=row['orderCode'])
    assert ctx.recent_orders[0].order_code == row['orderCode']
    assert (ctx.recent_orders[0].payment.status if state else ctx.recent_orders[0].payment) == state


class NoModel:
    def chat(self, *args, **kwargs):
        raise AssertionError('Known unavailable payment must not use generative answer')


def pipeline(handler):
    return ChatService(customer_context_service=CustomerContextService(BackendClient('http://synthetic', httpx.MockTransport(handler))),
                       response_generation=OllamaResponseGenerationService(NoModel()))


def test_exact_owned_inconsistent_order_no_substitution_no_payment_context():
    exact = order()
    newer = dict(order('ORD-NEWER',2), orderId=str(uuid4()))
    before = deepcopy([newer,exact]); calls=[]; grounding=[]
    def handler(r):
        calls.append((r.method,r.url.path))
        if r.url.path.endswith('/mine'):
            return httpx.Response(200,json={'content': [newer,exact]})
        assert r.url.path == OWNED_PATH
        return httpx.Response(409,json={'message':PAYMENT_CARDINALITY_MESSAGE})
    service=pipeline(handler)
    response=service.send_message(uuid4(),SendMessageRequest(message='Đơn hàng ORD-READ-EXACT của tôi đã thanh toán chưa?'),cookies={'synthetic':'session'})
    assert response.message == PAYMENT_UNAVAILABLE_MESSAGE and response.products == [] and response.sources == []
    assert calls == [('GET','/api/orders/mine'),('GET',OWNED_PATH)]
    assert [newer,exact] == before
    assert not any(word in response.message for word in ['PAID','UNPAID','PENDING','ORD-NEWER',OID,PAYMENT_CARDINALITY_MESSAGE])


def test_latest_selection_and_exact_missing_do_not_choose_another_order():
    older=order(); latest=dict(order('ORD-LATEST',2),orderId=str(uuid4()));calls=[]
    def handler(r):
        calls.append(r.url.path)
        if r.url.path.endswith('/mine'):return httpx.Response(200,json={'content':[older,latest]})
        assert r.url.path.endswith(latest['orderId'])
        return httpx.Response(409,json={'message':PAYMENT_CARDINALITY_MESSAGE})
    service=pipeline(handler)
    response=service.send_message(uuid4(),SendMessageRequest(message='Đơn hàng mới nhất của tôi đã thanh toán chưa?'),cookies={'synthetic':'session'})
    assert response.message == PAYMENT_UNAVAILABLE_MESSAGE and len(calls)==2
    calls.clear()
    response=service.send_message(uuid4(),SendMessageRequest(message='Đơn hàng ORD-NOTFOUND của tôi đã thanh toán chưa?'),cookies={'synthetic':'session'})
    assert 'không tìm thấy mã đơn' in response.message and calls==['/api/orders/mine']


@pytest.mark.parametrize('status',[401,403,500])
def test_pipeline_preserves_nonintegrity_backend_failures(status):
    def handler(r):
        return httpx.Response(200,json={'content':[order()]}) if r.url.path.endswith('/mine') else httpx.Response(status,json={'message':'Unrelated'})
    with pytest.raises(HTTPException) as exc:
        pipeline(handler).send_message(uuid4(),SendMessageRequest(message='Đơn hàng ORD-READ-EXACT đã thanh toán chưa?'),cookies={'synthetic':'session'})
    assert exc.value.status_code == (status if status in [401,403] else 502)


@pytest.mark.asyncio
async def test_b03_malformed_cart_remains502():
    service=CustomerContextService(BackendClient('http://synthetic',httpx.MockTransport(lambda r:httpx.Response(200,json={'items':[]}))))
    with pytest.raises(HTTPException) as exc:await service.load_context('CART_STATUS',{'synthetic':'session'})
    assert exc.value.status_code==502


@pytest.mark.asyncio
async def test_b04_mismatched_detail_remains502():
    def handler(r):return httpx.Response(200,json={'content':[order()]} if r.url.path.endswith('/mine') else dict(order(),orderCode='ORD-OTHER'))
    service=CustomerContextService(BackendClient('http://synthetic',httpx.MockTransport(handler)))
    with pytest.raises(HTTPException) as exc:await service.load_context('PAYMENT_STATUS',{'synthetic':'session'},order_code='ORD-READ-EXACT')
    assert exc.value.status_code==502
