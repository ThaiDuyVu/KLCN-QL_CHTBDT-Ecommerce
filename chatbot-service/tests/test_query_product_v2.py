"""Schema-faithful regression without changing development stock or seed data."""
from copy import deepcopy
from datetime import datetime, timezone
from uuid import UUID, uuid4

import httpx
import pytest

from app.features.query_understanding.normalization import DomainNormalizer
from app.features.product_advisor.real_service import RealProductAdvisorService, ProductResolutionError
from app.features.response_generation.ollama_service import OllamaResponseGenerationService
from app.shared.contracts.context import QueryContext, AuthenticatedCustomer, GroundedContext
from app.shared.contracts.enums import Intent, RequestedField
from app.shared.contracts.query import QueryPlan, ProductSearchPlan, ProductSelector

P1, P2, W = uuid4(), uuid4(), uuid4()
V1,V2,V3 = uuid4(),uuid4(),uuid4()


def product(pid, name, variants):
    return {'product':{'productId':str(pid),'productName':name,'status':'ACTIVE'},
        'category':{'categoryName':'Điện thoại'},'brand':{'brandName':'Xiaomi'},
        'variants':variants,'specifications':[],'images':[]}


def variant(vid, sku, price, ram, storage, color, qty):
    return {'variantId':str(vid),'sku':sku,'originalPrice':price,'effectivePrice':price,
        'ram':ram,'storage':storage,'color':color,'availableQuantity':qty,'warrantyMonths':12,'status':'ACTIVE'}


class Backend:
    def __init__(self):
        self.data={P1:product(P1,'Xiaomi 15',[
            variant(V1,'DEV-X15-BLK-256',18990000,'12GB','256GB','Đen',0),
            variant(V2,'DEV-X15-GRN-512',21990000,'16GB','512GB','Xanh lá',2)]),
            P2:product(P2,'iPhone 15',[variant(V3,'DEV-IP15-BLK-128',17990000,'6GB','128GB','Đen',1)])}
    def taxonomy(self):return ['Laptop','Điện thoại'],['Xiaomi','Dell','Apple','Samsung']
    def catalog_names(self):
        rows=[{'productId':str(pid),'productName':d['product']['productName']} for pid,d in self.data.items()]
        return rows+[{'productId':str(uuid4()),'productName':n} for n in ['Dell Inspiron 15','Dell XPS 13','Samsung Galaxy A55']]
    def identity_candidates(self, keyword):
        return [dict(v,productId=str(pid),productName=d['product']['productName']) for pid,d in self.data.items()
                for v in d['variants'] if keyword.casefold() in (v['sku']+' '+d['product']['productName']).casefold()]
    def detail(self,pid,warehouse_id=None):
        if pid not in self.data:raise httpx.HTTPStatusError('missing',request=httpx.Request('GET','https://backend/detail'),response=httpx.Response(404))
        d=deepcopy(self.data[pid])
        if warehouse_id is None:
            for v in d['variants']:v['availableQuantity']=None
        return d
    def variant(self,vid):return next(r for r in self.identity_candidates('') if UUID(r['variantId'])==vid)


def context(message):
    return QueryContext(customer=AuthenticatedCustomer(user_id=uuid4(),customer_id=uuid4(),display_name='Synthetic'),
        session_id=uuid4(),current_message=message)


def normalize(message):
    # Deliberately bad model extraction must never turn into business filters.
    return DomainNormalizer(Backend()).normalize(QueryPlan(intent=Intent.PRODUCT_DISCOVERY,
        product_search=ProductSearchPlan(category='Smartphone',min_ram_gb=32,min_storage_gb=1,
            cpu_keywords=['Dell'],min_price=1)),context(message))


@pytest.mark.parametrize('prompt', ['Tìm điện thoại Xiaomi dưới 20 triệu.','Smartphone Xiaomi nào còn hàng?'])
def test_live_canonical_taxonomy_brand(prompt):
    p=normalize(prompt).product_search
    assert p.category=='Điện thoại' and p.brands==['Xiaomi']
    assert p.min_ram_gb is None and p.min_storage_gb is None and p.cpu_keywords==[] and p.min_price is None


@pytest.mark.parametrize('prompt',[
    'Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB.',
    'Tìm laptop Dell có RAM 16GB, giá không quá 20 triệu.',
    'Tìm laptop Dell dưới 20tr, ram >=16, ssd >=512.'])
def test_hard_price_ram_brand_storage(prompt):
    p=normalize(prompt).product_search
    assert p.category=='Laptop' and p.max_price==20000000 and p.min_ram_gb==16
    if 'Dell' in prompt:assert p.brands==['Dell']
    assert p.min_storage_gb==(512 if 'ssd' in prompt else None)


def test_capacity_tb_and_minimum_not_exact():
    p=normalize('Xiaomi 15 RAM ít nhất 16GB, dung lượng tối thiểu 1TB.').product_search
    assert p.min_ram_gb==16 and p.min_storage_gb==1024
    assert p.targets[0].ram_gb is None and p.targets[0].storage_gb is None


@pytest.mark.parametrize('prompt', ['Xiaomi 15 còn hàng không?', 'Is Xiaomi 15 in stock?', 'iPhone 15 còn hàng không?', 'Samsung Galaxy A55 được bảo hành bao nhiêu tháng?'])
def test_named_identity_not_discovery(prompt):
    p=normalize(prompt)
    assert p.intent==Intent.PRODUCT_DETAIL and len(p.product_search.targets)==1
    assert p.product_search.in_stock_only is False
    assert RequestedField.WARRANTY in p.requested_fields if 'bảo hành' in prompt else RequestedField.STOCK in p.requested_fields


def test_price_conflict_clarifies_without_query():
    p=normalize('Tìm laptop giá từ 25 triệu đến 20 triệu.')
    assert p.clarification and p.product_search is None


@pytest.mark.parametrize('prompt',['Có loại rẻ hơn không?','Xiaomi 999 còn hàng không?','Xiaomii 15 còn hàng hong?','Xiaomi 15 Pro còn hàng không?','Tìm laptop RAM 16GB và điện thoại Xiaomi, mỗi loại một chiếc.'])
def test_unresolved_or_unsupported_clarifies(prompt):
    p=normalize(prompt)
    assert p.clarification and p.product_search is None


def test_comparison_separate_targets_not_cpu():
    p=normalize('So sánh Dell Inspiron 15 và Dell XPS 13 về giá và RAM.')
    assert p.intent==Intent.PRODUCT_COMPARE
    assert [t.product_name for t in p.product_search.targets]==['Dell Inspiron 15','Dell XPS 13']
    assert p.product_search.cpu_keywords==[]


def test_exact_variant_and_color():
    p=normalize('Xiaomi 15 bản RAM 16GB, dung lượng 512GB màu Xanh lá còn hàng không?').product_search
    assert (p.targets[0].ram_gb,p.targets[0].storage_gb,p.targets[0].color)==(16,512,'Xanh lá')
    assert p.min_ram_gb is None and p.min_storage_gb is None
    a=RealProductAdvisorService(Backend(),W)
    assert a.search(p)[0].eligible_variants[0].variant_id==V2


class NoLLM:
    def chat(self,*args,**kwargs):raise AssertionError('identity response must stay bound to variants')


def answer(plan,matches):
    return OllamaResponseGenerationService(NoLLM()).generate(GroundedContext(
        query_plan=QueryPlan(intent=Intent.PRODUCT_DETAIL,product_search=plan),user_query='giá tồn bảo hành',products=matches))


@pytest.mark.parametrize('selector',[
    ProductSelector(sku='DEV-X15-GRN-512'),ProductSelector(variant_id=V2),ProductSelector(product_id=P1,ram_gb=16)])
def test_exact_non_cheapest_sku_id_variant_cards_and_message(selector):
    a=RealProductAdvisorService(Backend(),W);p=ProductSearchPlan(targets=[selector])
    m=a.search(p);cards=a.build_cards(m)
    assert len(cards)==1 and cards[0].variant_id==V2 and cards[0].effective_price==21990000
    text=answer(p,m)
    assert 'DEV-X15-GRN-512' in text and '21,990,000' in text and 'còn 2' in text and '12 tháng' in text
    assert 'DEV-X15-BLK-256' not in text


def test_mixed_stock_preserved_and_no_semantic_identity():
    a=RealProductAdvisorService(Backend(),W)
    p=ProductSearchPlan(targets=[ProductSelector(product_name='Xiaomi 15')],in_stock_only=True)
    m=a.search(p);cards=a.build_cards(m);text=answer(p,m)
    assert [v.available_quantity for v in m[0].eligible_variants]==[0,2]
    assert len(cards)==1 and cards[0].variant_id==V1
    assert 'hết hàng' in text and 'còn 2' in text


def test_sold_out_vs_not_found():
    b=Backend()
    for v in b.data[P1]['variants']:v['availableQuantity']=0
    a=RealProductAdvisorService(b,W);p=ProductSearchPlan(targets=[ProductSelector(product_name='Xiaomi 15')])
    m=a.search(p);assert 'hết hàng' in answer(p,m) and len(a.build_cards(m))==1
    with pytest.raises(ProductResolutionError,match='Không tìm thấy') as e:
        a.search(ProductSearchPlan(targets=[ProductSelector(sku='MISSING')]))
    assert e.value.reason=='NOT_FOUND'


def test_unknown_warehouse_does_not_claim_zero():
    a=RealProductAdvisorService(Backend());p=ProductSearchPlan(targets=[ProductSelector(product_id=P1)],in_stock_only=True)
    m=a.search(p)
    assert all(v.available_quantity is None for v in m[0].eligible_variants)
    assert 'chưa xác định tồn kho' in answer(p,m)


def test_compare_resolves_both_products_without_unrelated_cards():
    a=RealProductAdvisorService(Backend(),W);p=ProductSearchPlan(targets=[ProductSelector(product_id=P1),ProductSelector(product_id=P2)])
    m=a.search(p);cards=a.build_cards(m)
    assert {c.product_id for c in cards}=={P1,P2}
    assert 'Xiaomi 15' in answer(p,m) and 'iPhone 15' in answer(p,m)


def test_backend_failure_is_not_out_of_stock():
    b=Backend()
    def unavailable(*args):raise httpx.ConnectError('offline')
    b.detail=unavailable
    with pytest.raises(httpx.ConnectError):RealProductAdvisorService(b,W).search(ProductSearchPlan(targets=[ProductSelector(product_id=P1)]))


def test_ambiguous_duplicate_name_and_missing_configuration():
    b=Backend();b.data[P2]['product']['productName']='Xiaomi 15'
    a=RealProductAdvisorService(b,W)
    with pytest.raises(ProductResolutionError) as e:a.search(ProductSearchPlan(targets=[ProductSelector(product_name='Xiaomi 15')]))
    assert e.value.reason=='AMBIGUOUS'
    with pytest.raises(ProductResolutionError) as e:a.search(ProductSearchPlan(targets=[ProductSelector(product_id=P1,storage_gb=1024)]))
    assert e.value.reason=='VARIANT_NOT_FOUND'


def test_ram_minimum_is_not_a_price_and_ambiguous_advice_clarifies():
    p=normalize('Tìm điện thoại RAM tối thiểu 12GB.').product_search
    assert p.min_ram_gb==12 and p.min_price is None and p.max_price is None
    p=normalize('Tôi muốn máy vừa rẻ vừa mạnh, tư vấn giúp.')
    assert p.clarification and p.product_search is None


def test_discovery_text_uses_only_eligible_variants_not_general_ram_spec():
    a=RealProductAdvisorService(Backend(),W)
    p=ProductSearchPlan(targets=[ProductSelector(product_id=P1,ram_gb=16)])
    m=a.search(p)
    ctx=GroundedContext(query_plan=QueryPlan(intent=Intent.PRODUCT_DISCOVERY,product_search=ProductSearchPlan(min_ram_gb=16)),
                        user_query='RAM ít nhất16',products=m)
    text=OllamaResponseGenerationService(NoLLM()).generate(ctx)
    assert 'DEV-X15-GRN-512' in text and 'DEV-X15-BLK-256' not in text


@pytest.mark.parametrize('phrase,expected', [('dưới 20.000.000 VND',20000000),('dưới 20,5 triệu',20500000),('giá từ 15000000 VND',None)])
def test_vnd_price_formats(phrase,expected):
    p=normalize('Tìm laptop '+phrase).product_search
    assert p.max_price==expected
    if expected is None: assert p.min_price==15000000


def test_product_exists_without_variants_not_reported_missing():
    b=Backend();b.data[P1]['variants']=[]
    with pytest.raises(ProductResolutionError) as e:
        RealProductAdvisorService(b,W).search(ProductSearchPlan(targets=[ProductSelector(product_name='Xiaomi 15')]))
    assert e.value.reason=='VARIANT_NOT_FOUND'


def test_unknown_explicit_brand_clarifies():
    assert normalize('Tìm điện thoại hãng Unlisted dưới 20 triệu.').clarification


@pytest.mark.parametrize('prompt', ['Tìm laptop RAM dưới 16GB.','Tìm laptop RAM từ 8 tới 16GB.','Tìm laptop RAM 0GB.','Tìm laptop RAM 16MB.'])
def test_unsupported_or_invalid_capacity_never_becomes_price_or_broad_query(prompt):
    p=normalize(prompt)
    assert p.clarification and p.product_search is None


def test_color_english_and_vietnamese_not_ignored():
    p=normalize('Xiaomi 15 color black còn hàng không?').product_search
    assert p.targets[0].color=='Đen'
    p=normalize('Xiaomi 15 bản màu đỏ còn hàng không?').product_search
    assert p.targets[0].color=='Đỏ'


def test_model_failure_can_use_verified_domain_fallback():
    from app.features.query_understanding.ollama_service import OllamaQueryUnderstandingService
    from app.shared.ollama.client import OllamaUnavailable
    class Offline:
        def chat(self,*args,**kwargs):raise OllamaUnavailable('offline')
    plan=OllamaQueryUnderstandingService(Offline(),normalizer=DomainNormalizer(Backend())).understand(context('Xiaomi 15 còn hàng không?'))
    assert plan.intent==Intent.PRODUCT_DETAIL and plan.product_search.targets[0].product_name=='Xiaomi 15'


def test_fractional_promotion_price_is_not_rounded_away():
    b=Backend();b.data[P1]['variants'][0]['effectivePrice']='18990000.25'
    p=ProductSearchPlan(targets=[ProductSelector(sku='DEV-X15-BLK-256')])
    a=RealProductAdvisorService(b,W);m=a.search(p)
    assert '18,990,000.25 VND' in answer(p,m)


def test_pipeline_missing_sku_clarifies_without_cards():
    from app.features.chat.service import ChatService
    from app.features.chat.models import SendMessageRequest
    class Understanding:
        def understand(self,context):
            return QueryPlan(intent=Intent.PRODUCT_DETAIL,requested_fields=[RequestedField.STOCK],
                product_search=ProductSearchPlan(targets=[ProductSelector(sku='MISSING')]))
    service=ChatService(query_understanding=Understanding(),product_advisor=RealProductAdvisorService(Backend(),W),
                        response_generation=OllamaResponseGenerationService(NoLLM()))
    response=service.send_message(uuid4(),SendMessageRequest(message='SKU MISSING còn hàng không?'))
    assert response.products==[] and 'Không tìm thấy' in response.message and 'không phải kết luận hết hàng' in response.message


def test_pipeline_backend_outage_returns_503_instead_of_stock_answer():
    from app.features.chat.service import ChatService
    from app.features.chat.models import SendMessageRequest
    from fastapi import HTTPException
    class Understanding:
        def understand(self,context):
            return QueryPlan(intent=Intent.PRODUCT_DETAIL,
                product_search=ProductSearchPlan(targets=[ProductSelector(product_id=P1)]))
    b=Backend()
    def unavailable(*args):raise httpx.ConnectError('offline')
    b.detail=unavailable
    service=ChatService(query_understanding=Understanding(),product_advisor=RealProductAdvisorService(b,W),
                        response_generation=OllamaResponseGenerationService(NoLLM()))
    with pytest.raises(HTTPException) as e:service.send_message(uuid4(),SendMessageRequest(message='Xiaomi 15 còn hàng không?'))
    assert e.value.status_code==503


def test_model_cannot_bypass_grounding_via_clarification_field():
    from app.features.query_understanding.ollama_service import OllamaQueryUnderstandingService
    class Untrusted:
        def chat(self,*args,**kwargs):return '{"intent":"ORDER_STATUS","clarification":"Đã thanh toán xong"}'
    assert OllamaQueryUnderstandingService(Untrusted()).understand(context('Đơn của tôi?')).clarification is None
