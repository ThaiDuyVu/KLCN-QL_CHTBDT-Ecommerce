from uuid import uuid4

import httpx
import pytest
from fastapi import HTTPException

from app.features.customer_context.service import CustomerContextService
from app.shared.backend.client import BackendClient
from app.shared.contracts.context import CartContext, OrderContext


@pytest.mark.asyncio
async def test_real_camelcase_cart_is_mapped_and_validated():
    async def handler(request):
        assert request.url.path == '/api/cart'
        return httpx.Response(200, json={'cartId': str(uuid4()), 'subtotal': 900,
            'originalSubtotal': 1000, 'items': [{'variantId': str(uuid4()), 'productName': 'Laptop thử nghiệm',
            'sku': 'TEST', 'quantity': 1, 'effectivePrice': 900, 'unitPrice': 1000, 'lineTotal': 900}]})
    context = await CustomerContextService(BackendClient('http://fake', httpx.MockTransport(handler))).load_context('CART_STATUS', {'session': 'fake'})
    assert isinstance(context.cart, CartContext)
    assert context.cart.items[0].effective_price == 900
    assert context.cart.items[0].product_name == 'Laptop thử nghiệm'


@pytest.mark.asyncio
async def test_orders_content_and_payment_use_locked_contract():
    order_id = str(uuid4())
    order = {'orderId': order_id, 'orderCode': 'DH-TEST', 'orderDate': '2026-10-06T08:00:00Z',
        'status': 'PENDING', 'totalAmount': 900, 'payment': {'paymentId': str(uuid4()), 'paymentMethod': 'COD', 'status': 'PENDING', 'amount': 900}}
    async def handler(request):
        if request.url.path.endswith('/mine'):
            return httpx.Response(200, json={'content': [order], 'totalElements': 1})
        assert request.url.path.endswith(order_id)
        return httpx.Response(200, json=order)
    service = CustomerContextService(BackendClient('http://fake', httpx.MockTransport(handler)))
    for intent in ['ORDER_STATUS', 'PAYMENT_STATUS']:
        context = await service.load_context(intent, {'session': 'fake'})
        assert isinstance(context.recent_orders[0], OrderContext)
        assert context.recent_orders[0].payment.status == 'PENDING'
        assert context.recent_orders[0].order_code == 'DH-TEST'


@pytest.mark.asyncio
async def test_warranty_and_ticket_pages_map_content():
    warranty_id = str(uuid4())
    warranty = {'warrantyId': warranty_id, 'orderId': str(uuid4()), 'productName': 'Laptop', 'sku': 'SKU',
        'startDate': '2026-01-01', 'endDate': '2027-01-01', 'status': 'ACTIVE', 'eligible': True, 'tickets': []}
    ticket = {'ticketId': str(uuid4()), 'ticketCode': 'BH-TEST', 'warrantyId': warranty_id, 'productName': 'Laptop',
        'sku': 'SKU', 'issueDescription': 'Không bật được máy', 'status': 'RECEIVED', 'createdAt': '2026-10-06T08:00:00Z'}
    async def handler(request):
        row = ticket if request.url.path.endswith('/tickets') else warranty
        return httpx.Response(200, json={'content': [row], 'totalElements': 1})
    service = CustomerContextService(BackendClient('http://fake', httpx.MockTransport(handler)))
    assert (await service.load_context('WARRANTY_STATUS', {'session': 'fake'})).warranties[0].eligible
    assert (await service.load_context('WARRANTY_TICKET_STATUS', {'session': 'fake'})).warranty_tickets[0].ticket_code == 'BH-TEST'


@pytest.mark.asyncio
async def test_missing_payment_does_not_invent_payment():
    order = {'orderId': str(uuid4()), 'orderCode': 'TEST', 'orderDate': '2026-10-06T08:00:00Z', 'status': 'PENDING', 'totalAmount': 0}
    async def handler(request):
        return httpx.Response(200, json={'content': [order]}) if request.url.path.endswith('/mine') else httpx.Response(200, json=order)
    context = await CustomerContextService(BackendClient('http://fake', httpx.MockTransport(handler))).load_context('PAYMENT_STATUS', {'session': 'fake'})
    assert context.recent_orders[0].payment is None


@pytest.mark.asyncio
@pytest.mark.parametrize('failure,status', [('connection', 503), ('json', 502), ('server', 502), ('auth', 403), ('schema', 502)])
async def test_expected_backend_failures_have_explicit_status(failure, status):
    async def handler(request):
        if failure == 'connection':
            raise httpx.ConnectError('offline', request=request)
        if failure == 'json':
            return httpx.Response(200, text='bad-json')
        if failure == 'server':
            return httpx.Response(500)
        if failure == 'auth':
            return httpx.Response(403)
        return httpx.Response(200, json={'items': []})
    service = CustomerContextService(BackendClient('http://fake', httpx.MockTransport(handler)))
    with pytest.raises(HTTPException) as exc:
        await service.load_context('CART_STATUS', {'session': 'fake'})
    assert exc.value.status_code == status


def test_payment_without_evidence_and_empty_cart_skip_model():
    from tests.test_ai_services import FakeChat
    from app.features.response_generation.ollama_service import OllamaResponseGenerationService
    from app.shared.contracts.context import CustomerContext, GroundedContext
    from app.shared.contracts.query import QueryPlan
    from app.shared.contracts.enums import Intent
    model = FakeChat()
    empty_cart = CustomerContext(cart=CartContext(cart_id=uuid4(), items=[], subtotal=0))
    assert 'trống' in OllamaResponseGenerationService(model).generate(GroundedContext(query_plan=QueryPlan(intent=Intent.CART_STATUS), user_query='Giỏ của tôi?', customer_context=empty_cart))
    order = OrderContext(order_id=uuid4(), order_code='TEST', order_date='2026-10-06T08:00:00Z', status='PENDING', total_amount=0)
    context = GroundedContext(query_plan=QueryPlan(intent=Intent.PAYMENT_STATUS), user_query='Đã thanh toán?', customer_context=CustomerContext(recent_orders=[order]))
    assert 'chưa có dữ liệu thanh toán' in OllamaResponseGenerationService(model).generate(context)
    assert not model.calls


def test_mutation_request_has_deterministic_no_action_response():
    from tests.test_ai_services import FakeChat
    from app.features.response_generation.ollama_service import OllamaResponseGenerationService
    from app.shared.contracts.context import GroundedContext
    from app.shared.contracts.query import QueryPlan
    from app.shared.contracts.enums import Intent
    model = FakeChat()
    response = OllamaResponseGenerationService(model).generate(GroundedContext(
        query_plan=QueryPlan(intent=Intent.UNKNOWN), user_query='Hãy đặt ngay laptop và thanh toán giúp tôi.'))
    assert 'không thể thực hiện giao dịch' in response
    assert not model.calls
