"""Controlled pagination contracts: incomplete is unavailable, never false NOT_FOUND."""
import httpx
import pytest
from fastapi import HTTPException
from app.features.product_advisor.backend import BackendProductClient
from app.shared.backend.client import BackendClient


@pytest.mark.parametrize('method', ['catalog_names','identity_candidates'])
def test_b06_catalog_and_identity_caps_do_not_return_partial_candidates(method):
    calls=[]
    def handler(request):
        calls.append(request.url.params['page'])
        return httpx.Response(200,json={'content':[{'productName':'Máy kiểm thử'}]*100,'totalPages':6})
    client=BackendProductClient('http://synthetic');client._client.close()
    client._client=httpx.Client(base_url='http://synthetic',transport=httpx.MockTransport(handler))
    try:
        with pytest.raises(ValueError,match='bounded coverage'):
            getattr(client,method)(*(['SYNTHETIC'] if method=='identity_candidates' else []))
        assert calls==['0','1','2','3','4']
    finally:client._client.close()


def test_b07_taxonomy_incomplete_still_fails_closed():
    def handler(r):
        return httpx.Response(200,json=[{'categoryName':'Laptop','status':'ACTIVE'}] if r.url.path.endswith('/categories') else {'content':[{'brandName':'Dell','status':'ACTIVE'}]*100,'totalPages':2,'totalElements':101})
    client=BackendProductClient('http://synthetic');client._client.close()
    client._client=httpx.Client(base_url='http://synthetic',transport=httpx.MockTransport(handler))
    try:
        with pytest.raises(ValueError,match='bounded coverage'):client.taxonomy()
    finally:client._client.close()


@pytest.mark.asyncio
@pytest.mark.parametrize('total', [501,21])
async def test_b05_order_cap_or_incomplete_page_is503(total):
    def handler(r):
        return httpx.Response(200,json={'content':[{'orderId':'synthetic'}],'totalElements':total})
    with pytest.raises(HTTPException) as exc:
        await BackendClient('http://synthetic',httpx.MockTransport(handler)).get_recent_orders({'synthetic':'cookie'})
    assert exc.value.status_code==503


def test_structured_product_search_cap_does_not_claim_complete_matches():
    from uuid import uuid4
    from app.features.product_advisor.real_service import RealProductAdvisorService
    from app.shared.contracts.query import ProductSearchPlan
    calls=[];pid,vid=uuid4(),uuid4()
    class Backend:
        def search(self,plan,warehouse_id,limit,offset):
            calls.append(offset)
            return [(pid,vid)]*100
        def detail(self,*args):raise AssertionError('Do not enrich or claim complete results after incomplete paging')
    with pytest.raises(ValueError,match='bounded coverage'):
        RealProductAdvisorService(Backend(),uuid4()).search(ProductSearchPlan(category='Laptop'))
    assert calls==[0,100,200,300,400]
