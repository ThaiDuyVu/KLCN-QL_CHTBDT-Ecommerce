"""Frozen diagnostic replays; synthetic fixtures never write business data."""
import json
from pathlib import Path

import pytest

from app.features.query_understanding.normalization import DomainNormalizer
from app.shared.contracts.enums import Intent
from app.shared.contracts.query import QueryPlan
from tests.test_query_product_v2 import Backend, context

DOSSIER = Path(__file__).resolve().parents[1] / 'debug/post-upgrade-validation'
PROMPTS = {
    'N03': 'Tìm laptop Lenovo RAM 16GB.',
    'N04': 'Tìm máy tính xách tay Dell dưới 20 triệu.',
    'N12': 'So sánh Xiaomi 15 bản 12GB/256GB với iPhone 15 bản 6GB/128GB.',
    'N46': 'Tìm laptop dưới 20 triệu nhưng RAM phải dưới 16GB.',
    'N47': 'Chỉ gợi ý đúng 2 điện thoại Xiaomi, nếu không đủ thì nói rõ.',
}


def recorded_plan(case):
    trace = json.loads((DOSSIER / f'evidence/traces/{case}.json').read_text())
    rows = [e['output'] for e in trace['events'] if e['name'] == 'query.validation']
    return QueryPlan.model_validate(rows[0]) if rows else QueryPlan(intent=Intent.UNKNOWN)


@pytest.mark.parametrize('case', PROMPTS)
def test_frozen_diagnostic_queryplan(case):
    plan = DomainNormalizer(Backend()).normalize(recorded_plan(case), context(PROMPTS[case]))
    if case in {'N03', 'N46'}:
        assert plan.clarification and plan.product_search is None
    elif case == 'N04':
        search = plan.product_search
        assert (search.category, search.brands, search.max_price) == ('Laptop', ['Dell'], 20000000)
    elif case == 'N12':
        assert [(t.product_name, t.ram_gb, t.storage_gb) for t in plan.product_search.targets] == [
            ('Xiaomi 15', 12, 256), ('iPhone 15', 6, 128)]
    else:
        assert plan.product_search.top_k == 2


def normalized(prompt, plan=None, backend=None):
    return DomainNormalizer(backend or Backend()).normalize(plan or QueryPlan(intent=Intent.UNKNOWN), context(prompt))


@pytest.mark.parametrize('wording', [
    'RAM dưới 16GB', 'RAM phải dưới 16GB', 'RAM chỉ dưới 16GB',
    'RAM không quá 16GB', 'RAM tối đa 16GB', 'RAM phải không vượt quá 16GB',
    'RAM must be under 16GB', 'RAM at most 16GB', 'RAM <16GB', 'RAM <=16GB',
    'SSD chỉ tối đa 1TB', 'dung lượng phải dưới 512GB', 'RAM từ 8GB đến 16GB',
    'RAM lớn hơn 16GB', 'RAM 16MB', 'RAM 0GB',
])
def test_unsupported_capacity_stops_before_retrieval(wording):
    p = normalized('Tìm laptop giá dưới 20 triệu, ' + wording)
    assert p.intent == Intent.UNKNOWN and p.clarification and p.product_search is None


@pytest.mark.parametrize('wording', ['RAM ít nhất 16GB', 'RAM phải ít nhất 16GB', 'RAM >=16GB', 'RAM at least 16GB', 'RAM 16GB'])
def test_minimum_compatibility_and_mixed_price(wording):
    p = normalized('Tìm laptop ' + wording + ', dưới 20 triệu.').product_search
    assert p.min_ram_gb == 16 and p.max_price == 20000000 and p.min_storage_gb is None


@pytest.mark.parametrize('brand', ['Lenovo', 'Acer', 'Unlisted', 'Acme'])
@pytest.mark.parametrize('prefix', ['', 'hãng ', 'thương hiệu ', 'brand '])
def test_unknown_brand_never_becomes_other_brand_search(brand, prefix):
    p = normalized('Tìm laptop ' + prefix + brand + ' RAM 16GB.')
    assert p.clarification and p.product_search is None


@pytest.mark.parametrize('use_case', ['để lập trình', 'phục vụ học tập', 'gaming', 'mỏng nhẹ', 'cho sinh viên', 'có RAM 16GB'])
def test_use_case_words_are_not_brand_identities(use_case):
    p = normalized('Tìm laptop ' + use_case + ' dưới 20 triệu.')
    assert not p.clarification and p.product_search.category == 'Laptop' and p.product_search.brands == []


def test_brands_come_from_taxonomy_not_fixed_list():
    b = Backend()
    b.taxonomy = lambda: (['Laptop', 'Điện thoại'], ['Dell', 'Framework'])
    p = normalized('Tìm laptop Framework RAM 16GB.', backend=b)
    assert p.product_search.brands == ['Framework']


def test_model_invented_sku_filters_and_configuration_removed():
    from app.shared.contracts.query import ProductSearchPlan, ProductSelector
    bad = QueryPlan(intent=Intent.PRODUCT_DETAIL, product_search=ProductSearchPlan(
        targets=[ProductSelector(sku='INVENTED')], category='Điện thoại', brands=['Apple'],
        min_ram_gb=32, min_storage_gb=1024, min_price=1000, in_stock_only=True, top_k=1))
    p = normalized('Tìm laptop Dell dưới 20 triệu.', bad).product_search
    assert p.targets == [] and p.brands == ['Dell'] and p.category == 'Laptop'
    assert p.min_ram_gb is None and p.min_storage_gb is None and p.min_price is None
    assert p.in_stock_only is False and p.top_k == 5


@pytest.mark.parametrize('prompt,expected', [
    ('So sánh iPhone 15 bản 6GB/128GB với Xiaomi 15 bản 12GB/256GB.', [('iPhone 15', 6, 128), ('Xiaomi 15', 12, 256)]),
    ('So sánh Xiaomi 15 RAM 12GB dung lượng 256GB với iPhone 15 RAM 6GB storage 128GB.', [('Xiaomi 15', 12, 256), ('iPhone 15', 6, 128)]),
    ('So sánh Xiaomi 15 bản 12GB/256GB với iPhone 15.', [('Xiaomi 15', 12, 256), ('iPhone 15', None, None)]),
])
def test_target_scoping_and_order_independent_of_catalog(prompt, expected):
    p = normalized(prompt).product_search
    assert [(t.product_name, t.ram_gb, t.storage_gb) for t in p.targets] == expected
    assert p.min_ram_gb is None and p.min_storage_gb is None


def test_target_colors_do_not_leak():
    p = normalized('So sánh Xiaomi 15 RAM 12GB màu Đen với iPhone 15 RAM 6GB màu Trắng.').product_search
    assert [(t.ram_gb, t.color) for t in p.targets] == [(12, 'Đen'), (6, 'Trắng')]


@pytest.mark.parametrize('prompt', [
    'So sánh RAM 16GB Xiaomi 15 và iPhone 15.',
    'So sánh Xiaomi 15 và iPhone 15 cả hai RAM 16GB.',
    'So sánh Xiaomi 15 RAM ít nhất 12GB và iPhone 15 RAM 6GB.',
    'So sánh Xiaomi 15 dưới 20 triệu và iPhone 15 dưới 10 triệu.',
])
def test_ambiguous_or_unrepresentable_target_scope_clarifies(prompt):
    p = normalized(prompt)
    assert p.clarification and p.product_search is None


@pytest.mark.parametrize('prompt,count', [
    (PROMPTS['N47'], 2), ('Tìm 2 chiếc laptop Dell.', 2),
    ('Gợi ý hai điện thoại Xiaomi.', 2), ('Tìm 20 laptop.', 20),
])
@pytest.mark.parametrize('output', ['not json', '{"intent":"PRODUCT_DISCOVERY","product_search":{"targets":[{}],"top_k":2}}'])
def test_cardinality_survives_invalid_json_and_repair(prompt, count, output):
    from app.features.query_understanding.ollama_service import OllamaQueryUnderstandingService
    class Model:
        calls = 0
        def chat(self, *args, **kwargs):
            self.calls += 1
            return output
    model = Model()
    p = OllamaQueryUnderstandingService(model, normalizer=DomainNormalizer(Backend())).understand(context(prompt))
    assert p.product_search.top_k == count and not p.product_search.targets
    assert model.calls == 2


@pytest.mark.parametrize('prompt', ['Tìm 21 laptop.', 'Tìm 0 điện thoại Xiaomi.'])
def test_unsupported_cardinality_clarifies(prompt):
    assert normalized(prompt).clarification


def test_no_count_from_price_capacity_or_model_number():
    assert normalized('Tìm laptop RAM 16GB dưới 2 triệu.').product_search.top_k == 5


@pytest.mark.parametrize('prompt', ['Xiaomi 15 bản RAM 16GB dung lượng 512GB.', 'Xiaomi 15 bản 16GB/512GB.'])
def test_exact_target_card_and_message_use_only_eligible_variant(prompt):
    from app.features.product_advisor.real_service import RealProductAdvisorService
    from tests.test_query_product_v2 import W, answer
    p = normalized(prompt).product_search
    a = RealProductAdvisorService(Backend(), W)
    matches = a.search(p)
    cards = a.build_cards(matches)
    assert p.targets[0].ram_gb == 16 and p.targets[0].storage_gb == 512
    assert len(cards) == 1 and cards[0].sku == 'DEV-X15-GRN-512'
    assert 'DEV-X15-GRN-512' in answer(p, matches) and 'DEV-X15-BLK-256' not in answer(p, matches)


def test_frozen_comparison_matches_cards_and_text():
    from app.features.product_advisor.real_service import RealProductAdvisorService
    from tests.test_query_product_v2 import W, answer
    p = normalized(PROMPTS['N12'], recorded_plan('N12')).product_search
    a = RealProductAdvisorService(Backend(), W)
    matches = a.search(p)
    cards = a.build_cards(matches)
    assert len(matches) == len(cards) == 2
    assert [len(m.eligible_variants) for m in matches] == [1, 1]
    assert [c.sku for c in cards] == ['DEV-X15-BLK-256', 'DEV-IP15-BLK-128']
    text = answer(p, matches)
    assert 'DEV-X15-BLK-256' in text and 'DEV-IP15-BLK-128' in text
    assert 'DEV-X15-GRN-512' not in text


def test_category_filter_removes_synthetic_non_laptop_dell():
    from app.features.product_advisor.real_service import RealProductAdvisorService
    from tests.test_query_product_v2 import P1, P2, W, answer
    from uuid import UUID
    b = Backend()
    for row in b.data.values():
        row['brand']['brandName'] = 'Dell'
    b.data[P1]['category']['categoryName'] = 'Laptop'
    a = RealProductAdvisorService(b, W)
    p = normalized(PROMPTS['N04'], recorded_plan('N04'), b).product_search
    assert a._eligible(b.data[P1], b.data[P1]['variants'][0], p)
    assert not a._eligible(b.data[P2], b.data[P2]['variants'][0], p)
    # Return both from the structured adapter: enrichment must still exclude the phone.
    b.search = lambda *args, **kwargs: [(pid, UUID(v['variantId'])) for pid, row in b.data.items() for v in row['variants']]
    matches = a.search(p)
    cards = a.build_cards(matches)
    assert len(matches) == len(cards) == 1 and cards[0].product_id == P1
    assert 'iPhone 15' not in answer(p, matches)


@pytest.mark.parametrize('taxonomy', [([], ['Dell']), (['Laptop'], [])])
def test_missing_taxonomy_is_unavailability_not_no_match(taxonomy):
    b = Backend()
    b.taxonomy = lambda: taxonomy
    with pytest.raises(ValueError, match='unavailable'):
        normalized('Tìm laptop Dell.', backend=b)


def test_unknown_category_does_not_become_unrestricted():
    from app.shared.contracts.query import ProductSearchPlan
    p = normalized('Tìm máy in Dell.', QueryPlan(intent=Intent.PRODUCT_DISCOVERY,
                    product_search=ProductSearchPlan(category='Máy in')))
    assert p.clarification and p.product_search is None


def test_incomplete_brand_taxonomy_is_reported_as_unavailable():
    import httpx
    from app.features.product_advisor.backend import BackendProductClient
    b = BackendProductClient('http://synthetic-backend')
    def handler(request):
        if request.url.path.endswith('/categories'):
            return httpx.Response(200, json=[{'categoryName': 'Laptop', 'status': 'ACTIVE'}])
        return httpx.Response(200, json={'content': [{'brandName': 'Dell', 'status': 'ACTIVE'}], 'totalPages': 2, 'totalElements': 101})
    b._client = httpx.Client(base_url='http://synthetic-backend', transport=httpx.MockTransport(handler))
    with pytest.raises(ValueError, match='bounded coverage'):
        normalized('Tìm laptop Lenovo.', backend=b)
    b._client.close()


def test_cardinality_limits_actual_matches_cards_and_text_with_three_candidates():
    from copy import deepcopy
    from uuid import uuid4
    from app.features.product_advisor.real_service import RealProductAdvisorService
    from app.features.response_generation.ollama_service import OllamaResponseGenerationService
    from app.shared.contracts.context import GroundedContext
    from tests.test_query_product_v2 import P1, W, NoLLM
    b = Backend()
    b.data = {P1: b.data[P1]}
    for index in range(2):
        pid = uuid4()
        row = deepcopy(b.data[P1])
        row['product'].update(productId=str(pid), productName=f'Xiaomi Demo {index}')
        row['variants'] = [dict(row['variants'][0], variantId=str(uuid4()), sku=f'SYNTH-{index}')]
        b.data[pid] = row
    b.search = lambda *args, **kwargs: [(pid, __import__('uuid').UUID(v['variantId'])) for pid, row in b.data.items() for v in row['variants']]
    p = normalized(PROMPTS['N47'], backend=b)
    a = RealProductAdvisorService(b, W)
    matches = a.search(p.product_search)
    cards = a.build_cards(matches)
    assert len(matches) == len(cards) == 2 and len({c.product_id for c in cards}) == 2
    text = OllamaResponseGenerationService(NoLLM()).generate(GroundedContext(query_plan=p, user_query=PROMPTS['N47'], products=matches))
    assert all(c.product_name in text for c in cards)
    assert not any(row['product']['productName'] in text for pid, row in b.data.items() if pid not in {c.product_id for c in cards})
    short = OllamaResponseGenerationService(NoLLM()).generate(GroundedContext(query_plan=p, user_query=PROMPTS['N47'], products=matches[:1]))
    assert '1/2 sản phẩm' in short


def test_integrity_gate_rejects_downstream_broadening():
    from app.features.query_understanding.constraints import ProductEvidence, ConstraintError
    from app.shared.contracts.query import ProductSearchPlan, ProductSelector
    selector = ProductSelector(product_name='Xiaomi 15', ram_gb=12, storage_gb=256)
    evidence = ProductEvidence('Điện thoại', ('Xiaomi',), (selector,), None, None, None, None, 2)
    with pytest.raises(ConstraintError):
        evidence.validate(ProductSearchPlan(category='Điện thoại', brands=['Xiaomi'], targets=[ProductSelector(product_name='Xiaomi 15')], top_k=2))
    with pytest.raises(ConstraintError):
        evidence.validate(ProductSearchPlan(category='Điện thoại', brands=['Xiaomi'], targets=[selector], top_k=5))


def test_followup_grammatical_fillers_and_history_do_not_conflict():
    from datetime import datetime, timezone
    from app.shared.contracts.context import ChatMessage
    from app.shared.contracts.enums import MessageRole
    ctx = context('Có chiếc nào rẻ hơn mà RAM vẫn 16GB không?')
    ctx.recent_messages = [
        ChatMessage(role=MessageRole.USER, content='Tìm laptop dưới 20 triệu, RAM ít nhất 16GB.', created_at=datetime.now(timezone.utc)),
        ChatMessage(role=MessageRole.ASSISTANT, content='Laptop RAM 16GB giá 15 triệu.', created_at=datetime.now(timezone.utc)),
    ]
    p = DomainNormalizer(Backend()).normalize(QueryPlan(intent=Intent.UNKNOWN), ctx)
    assert not p.clarification and p.product_search.min_ram_gb == 16
    ctx.current_message = 'Có loại rẻ hơn không?'
    p = DomainNormalizer(Backend()).normalize(recorded_plan('N04'), ctx)
    assert not p.clarification and p.product_search.min_ram_gb == 16


@pytest.mark.parametrize('prompt', [
    'Tìm laptop không phải Dell RAM 16GB.', 'Tìm laptop không phải Lenovo.',
    'Tìm laptop Dell nhưng không phải laptop.',
    'Xiaomi 15 bản 256GB còn hàng không?',
    'Xiaomi 15 RAM 12GB bản 256GB còn hàng không?',
    'So sánh Xiaomi 15 bản 12GB với iPhone 15 bản 128GB.',
    'Xiaomi 15 màu Graphite còn hàng không?',
])
def test_unrepresentable_explicit_constraints_never_broaden(prompt):
    p = normalized(prompt)
    assert p.clarification and p.product_search is None


@pytest.mark.parametrize('history', [False, True])
def test_storage_reference_is_deferred_to_owned_snapshot_resolution(history):
    from app.features.conversation.references import resolve_reference
    from tests.test_persistent_conversation import snapshot
    ctx = context('Có phiên bản 512GB không?')
    if history:
        from datetime import datetime, timezone
        from app.shared.contracts.context import ChatMessage
        ctx.recent_messages = [ChatMessage(role='USER', content='Xiaomi 15 còn hàng không?', created_at=datetime.now(timezone.utc))]
    p = DomainNormalizer(Backend()).normalize(QueryPlan(intent=Intent.UNKNOWN), ctx)
    p = resolve_reference(ctx.current_message, p, snapshot() if history else None)
    if history:
        assert not p.clarification and p.product_search.targets[0].storage_gb == 512
    else:
        assert p.clarification and p.product_search is None
