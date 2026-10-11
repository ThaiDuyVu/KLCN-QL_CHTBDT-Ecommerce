from uuid import UUID

import httpx
import pytest
from fastapi import HTTPException

from app.features.customer_context.models import AuthenticatedCustomer
from app.shared.backend.client import BackendClient


@pytest.mark.asyncio
async def test_backend_client_maps_userid_to_customer_id() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/auth/me"
        assert request.headers.get("cookie") == "session=abc"
        return httpx.Response(
            200,
            json={
                "userId": "11111111-1111-1111-1111-111111111111",
                "username": "demo-user",
                "displayName": "Demo User",
                "roleName": "CUSTOMER",
            },
        )

    client = BackendClient(
        base_url="http://backend.test",
        transport=httpx.MockTransport(handler),
    )

    customer = await client.get_current_customer({"session": "abc"})

    assert isinstance(customer, AuthenticatedCustomer)
    assert customer.user_id == UUID("11111111-1111-1111-1111-111111111111")
    assert customer.customer_id == UUID("11111111-1111-1111-1111-111111111111")
    assert customer.display_name == "Demo User"


@pytest.mark.asyncio
async def test_backend_client_raises_401_for_unauthorized_response() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, json={"detail": "Unauthorized"})

    client = BackendClient(
        base_url="http://backend.test",
        transport=httpx.MockTransport(handler),
    )

    with pytest.raises(HTTPException, match="401"):
        await client.get_current_user({"session": "abc"})


def test_product_adapter_forwards_cookie_without_shared_bearer():
    from app.features.product_advisor.backend import BackendProductClient, backend_cookies
    from app.shared.contracts.query import ProductSearchPlan
    client = BackendProductClient('http://fake', 'service-token')
    client._client.close()
    def handler(request):
        assert request.headers.get('cookie') == 'access_token=customer-cookie'
        assert 'Authorization' not in request.headers
        return httpx.Response(200, json=[])
    client._client = httpx.Client(base_url='http://fake', headers={'Authorization':'Bearer service-token'}, transport=httpx.MockTransport(handler))
    token = backend_cookies.set({'access_token':'customer-cookie'})
    try:
        assert client.search(ProductSearchPlan(), None, 5, 0) == []
    finally:
        backend_cookies.reset(token)
        client._client.close()
