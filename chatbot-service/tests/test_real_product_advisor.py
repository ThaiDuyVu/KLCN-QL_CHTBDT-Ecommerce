from decimal import Decimal
from uuid import UUID

import httpx
import pytest

from app.features.product_advisor.backend import BackendProductClient
from app.features.product_advisor.normalization import capacity_gb
from app.features.product_advisor.real_service import RealProductAdvisorService
from app.features.product_advisor.semantic import semantic_content
from app.features.product_advisor.semantic import ProductSemanticIndex
from app.shared.contracts.query import ProductSearchPlan
from app.shared.contracts.query import QueryPlan
from app.shared.contracts.enums import Intent
from app.features.chat.service import MockChatService
from app.features.chat.models import SendMessageRequest


PRODUCT = UUID("11111111-1111-1111-1111-111111111111")
VARIANT = UUID("22222222-2222-2222-2222-222222222222")
WAREHOUSE = UUID("33333333-3333-3333-3333-333333333333")


def detail(price="12000000", stock=4, ram="16GB"):
    return {
        "product": {"productId": str(PRODUCT), "productName": "Laptop mẫu", "status": "ACTIVE", "description": "Lập trình"},
        "category": {"categoryName": "Laptop"},
        "brand": {"brandName": "Dell"},
        "variants": [{
            "variantId": str(VARIANT), "sku": "LAP-16", "status": "ACTIVE",
            "originalPrice": "15000000", "effectivePrice": price, "ram": ram,
            "storage": "1TB", "color": "Đen", "availableQuantity": stock,
            "warrantyMonths": 24,
        }],
        "specifications": [{"specKey": "CPU", "specValue": "Intel Core i7"}],
        "images": [{"imageUrl": "/product.jpg", "isPrimary": True}],
    }


class FakeBackend:
    def __init__(self):
        self.current = detail()
        self.calls = []

    def search(self, plan, warehouse_id, limit, offset):
        self.calls.append((plan, warehouse_id, limit, offset))
        return [(PRODUCT, VARIANT)] if offset == 0 else []

    def detail(self, product_id, warehouse_id=None):
        return self.current


class FakeIndex:
    def scores(self, product_ids, vector, model):
        return {PRODUCT: 0.95}


class FakeEmbedder:
    model = "fake-model"

    def embed(self, text):
        return [0.1, 0.2]


def test_capacity_normalization_rejects_unknown_values():
    assert capacity_gb("16GB") == 16
    assert capacity_gb("512 GB") == 512
    assert capacity_gb("1TB") == 1024
    assert capacity_gb("1.5 TB") == 1536
    assert capacity_gb("16 bananas") is None
    assert capacity_gb(None) is None


def test_typed_backend_parameters_and_no_raw_sql():
    client = BackendProductClient("https://backend.example", "token")
    requests = []

    def handler(request):
        requests.append(request)
        return httpx.Response(200, json=[{"productId": str(PRODUCT), "variantId": str(VARIANT)}])

    client._client = httpx.Client(transport=httpx.MockTransport(handler), base_url="https://backend.example",
                                  headers={"Authorization": "Bearer token"})
    plan = ProductSearchPlan(category="Laptop", brands=["Dell"], min_price=Decimal(100),
                             min_ram_gb=16, cpu_keywords=["i7' OR true --"], in_stock_only=True)
    assert client.search(plan, WAREHOUSE, 100, 0) == [(PRODUCT, VARIANT)]
    request = requests[0]
    assert request.url.path == "/api/v1/products/search/advanced"
    assert request.url.params["cpuKeywords"] == "i7' OR true --"
    assert request.url.params["warehouseId"] == str(WAREHOUSE)
    assert request.headers["Authorization"] == "Bearer token"


def test_hard_filters_override_high_semantic_score_and_no_result_is_empty():
    backend = FakeBackend()
    advisor = RealProductAdvisorService(backend, WAREHOUSE, FakeIndex(), FakeEmbedder())
    plan = ProductSearchPlan(category="Laptop", min_ram_gb=32, semantic_query="best laptop")
    assert advisor.search(plan) == []
    assert advisor.build_cards([]) == []


def test_live_refresh_controls_price_stock_and_card():
    backend = FakeBackend()
    advisor = RealProductAdvisorService(backend, WAREHOUSE, FakeIndex(), FakeEmbedder())
    plan = ProductSearchPlan(category="Laptop", max_price=Decimal(13000000),
                             in_stock_only=True, semantic_query="laptop i7")
    matches = advisor.search(plan)
    assert len(matches) == 1
    assert matches[0].eligible_variants[0].product_id == PRODUCT
    backend.current = detail(price="12500000", stock=2)
    cards = advisor.build_cards(matches)
    assert cards[0].effective_price == Decimal("12500000")
    assert cards[0].available_quantity == 2
    assert cards[0].warranty_months == 24
    assert cards[0].image_url == "/product.jpg"
    backend.current = detail(price="14000000", stock=0)
    assert advisor.build_cards(matches) == []


def test_in_stock_search_requires_warehouse():
    advisor = RealProductAdvisorService(FakeBackend())
    with pytest.raises(ValueError, match="warehouse"):
        advisor.search(ProductSearchPlan(in_stock_only=True))


def test_semantic_content_excludes_dynamic_price_and_stock():
    first = semantic_content(detail(price="12000000", stock=4))
    second = semantic_content(detail(price="13000000", stock=0))
    assert first == second
    assert "CPU: Intel Core i7" in first


def test_unchanged_index_content_skips_embedding():
    import hashlib

    content = "Laptop sample"
    index = ProductSemanticIndex("postgresql://unused")
    index.index_signature = lambda product_id: (hashlib.sha256(content.encode()).hexdigest(), "fake-model")

    class FailingEmbedder:
        model = "fake-model"
        def embed(self, text):
            raise AssertionError("unchanged content must not be embedded again")

    assert index.upsert_if_changed(PRODUCT, content, FailingEmbedder()) is False


def test_changed_index_content_or_model_is_upserted():
    calls = []

    class Connection:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return None

        def execute(self, sql, params):
            calls.append((sql, params))

    index = ProductSemanticIndex("postgresql://unused")
    index.index_signature = lambda product_id: ("old-hash", "old-model")
    index._connect = Connection
    assert index.upsert_if_changed(PRODUCT, "new content", FakeEmbedder()) is True
    assert calls[0][1][3] == "fake-model"
    assert calls[0][1][4] == "[0.1,0.2]"


@pytest.mark.parametrize("intent,expected_limit", [
    (Intent.PRODUCT_DETAIL, 1), (Intent.PRODUCT_COMPARE, 3),
])
def test_detail_and_compare_reuse_injected_advisor(intent, expected_limit):
    class Understanding:
        def understand(self, context):
            return QueryPlan(intent=intent, product_search=ProductSearchPlan(top_k=20))

    class Advisor:
        def search(self, plan):
            assert plan.top_k == expected_limit
            return []

        def build_cards(self, matches):
            assert matches == []
            return []

    response = MockChatService(query_understanding=Understanding(), product_advisor=Advisor()).send_message(
        UUID("44444444-4444-4444-4444-444444444444"), SendMessageRequest(message="So sánh sản phẩm")
    )
    assert response.products == []
