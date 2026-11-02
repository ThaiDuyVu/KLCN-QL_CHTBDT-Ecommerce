"""Unit grammar for relational price evidence (A1/A2) and shared reference detection (A3)."""
from decimal import Decimal

import pytest

from app.features.query_understanding.constraints import (
    ConstraintError, PriceRelation, ReferenceKind, detect_reference, price_evidence, validate_price_coverage,
)
from app.features.query_understanding.normalization import folded

M = Decimal('1000000')


def evidence(text):
    return price_evidence(folded(text))


@pytest.mark.parametrize('text,low,high', [
    ('dưới 20 triệu', None, 20 * M), ('không quá 20 triệu', None, 20 * M), ('không vượt quá 20tr', None, 20 * M),
    ('tối đa là 20 triệu', None, 20 * M), ('trên 16 triệu và dưới 40 triệu', 16 * M, 40 * M),
    ('giá tối thiểu 5 triệu', 5 * M, None), ('giá từ 5 triệu', 5 * M, None), ('15,5 triệu trở xuống', None, Decimal('15500000')),
    ('dưới 20.000.000 đồng', None, 20 * M), ('dưới 500k', None, Decimal('500000')), ('giữa 10 và 12 triệu', 10 * M, 12 * M),
    ('giá tối đa 0 đồng', None, Decimal('0')), ('giá dưới 20000000', None, 20 * M),
])
def test_resolved_bounds(text, low, high):
    e = evidence(text)
    assert (e.min_price, e.max_price, e.unresolved) == (low, high, ())
    validate_price_coverage(folded(text), e)


@pytest.mark.parametrize('text,low,high,target', [
    # DR-02: "rẻ/thấp/ít hơn X" is strictly below X.
    ('rẻ hơn 15 triệu', None, 15 * M - 1, None), ('giá thấp hơn 20 triệu', None, 20 * M - 1, None),
    ('ít hơn 20 triệu', None, 20 * M - 1, None), ('< 20 triệu', None, 20 * M - 1, None), ('rẽ hơn 15tr', None, 15 * M - 1, None),
    # DR-01: approximate amounts are X ± 15%; a budget or "tôi có X" is a maximum.
    ('tầm 15 triệu', Decimal('12750000'), Decimal('17250000'), 15 * M), ('khoảng 25tr', Decimal('21250000'), Decimal('28750000'), 25 * M),
    ('giá 15 triệu', Decimal('12750000'), Decimal('17250000'), 15 * M), ('ngân sách 20tr', None, 20 * M, None),
    ('tôi có 100tr', None, 100 * M, None),
])
def test_comparative_approximate_and_budget_amounts_resolve(text, low, high, target):
    e = evidence(text)
    assert (e.min_price, e.max_price, e.target, e.unresolved) == (low, high, target, ())


@pytest.mark.parametrize('text,relation', [
    ('không dưới 10 triệu', PriceRelation.UNSPECIFIED), ('từ 15 triệu', PriceRelation.UNSPECIFIED),
    ('trên 10 triệu trở xuống', PriceRelation.UNSPECIFIED),
])
def test_unresolved_amounts_are_reported_and_block_coverage(text, relation):
    e = evidence(text)
    assert [r for r, _ in e.unresolved] == [relation]
    assert e.min_price is None and e.max_price is None
    with pytest.raises(ConstraintError):
        validate_price_coverage(folded(text), e)


@pytest.mark.parametrize('text', [
    'Xiaomi 15 giá bao nhiêu', 'iPhone 15 còn hàng không', 'Dell XPS 13', 'Inspiron 15', 'bản 12GB/256GB',
    'đúng 2 điện thoại Xiaomi', 'Intel i5', 'iPhone 15 cũ', 'màn hình 4k', 'SKU DEV-X15-BLK-256 giá bao nhiêu',
    'Samsung Galaxy A55 bảo hành bao nhiêu tháng', 'ram >=16, ssd >=512', 'RAM thấp hơn 16GB',
    # R1 (benchmark G8-14): a model number after "giá <tên máy>" is part of the name, not an amount.
    'chỉ hỏi giá iPhone 15', 'giá iPhone 15 bao nhiêu', 'giá Xiaomi 14T Pro',
])
def test_non_money_numbers_are_not_tokens(text):
    e = evidence(text)
    assert (e.min_price, e.max_price, e.spans, e.unresolved) == (None, None, (), ())
    validate_price_coverage(folded(text), e)


def test_zero_is_not_lost_by_truthiness():
    e = evidence('dưới 0 đồng và dưới 5 triệu')
    assert e.max_price == Decimal('0') and e.max_price is not None


@pytest.mark.parametrize('text,kind', [
    ('Máy đầu tiên còn hàng không?', ReferenceKind.ORDINAL_FIRST),
    ('Cái thứ hai là gì?', ReferenceKind.ORDINAL_SECOND), ('Sản phẩm thứ 2 giá bao nhiêu?', ReferenceKind.ORDINAL_SECOND),
    ('Chiếc máy vừa gợi ý có giá bao nhiêu?', ReferenceKind.LAST),
    ('Có loại rẻ hơn không?', ReferenceKind.CHEAPER), ('Có loại thấp hơn không?', ReferenceKind.CHEAPER),
    ('Có chiếc nào rẻ hơn mà RAM vẫn 16GB không?', ReferenceKind.CHEAPER),
    ('Có máy nào rẻ hơn 18 triệu không?', ReferenceKind.CHEAPER_BOUNDED),
    ('Bản 512GB thì sao?', ReferenceKind.STORAGE), ('Có phiên bản 512GB không?', ReferenceKind.STORAGE),
])
def test_reference_kinds(text, kind):
    assert detect_reference(folded(text)) == kind


@pytest.mark.parametrize('text', [
    'Đây là lần đầu tiên tôi mua laptop.', 'Thứ hai tuần sau có hàng không?', 'Tôi muốn tìm laptop Dell mới.',
    'Có chiếc nào RAM thấp hơn không?', 'Tôi muốn máy vừa rẻ vừa mạnh, tư vấn giúp.', 'Ngày thứ hai có giao hàng không?',
])
def test_non_references(text):
    assert detect_reference(folded(text)) is None


@pytest.mark.parametrize('text', ['Có sản phẩm nào mắc hơn xíu', 'Có loại đắt hơn không?', 'Giá cao hơn chút được không?'])
def test_pricier_follow_up_is_a_reference(text):
    assert detect_reference(folded(text)) == ReferenceKind.PRICIER


@pytest.mark.parametrize('text', ['Laptop mắc hơn 30 triệu', 'Có chiếc nào RAM cao hơn không?', 'Cấu hình cao hơn thì sao?'])
def test_pricier_non_references(text):
    # A stated amount is an ordinary minimum price; capacity/spec comparisons are not prices.
    assert detect_reference(folded(text)) is None


def test_pricier_keeps_previous_scope_and_starts_above_shown_price():
    from uuid import uuid4
    from app.features.conversation.models import EvidenceSnapshot
    from app.features.conversation.references import resolve_reference
    from app.shared.contracts.enums import Intent
    from app.shared.contracts.query import ProductSearchPlan, QueryPlan
    from app.shared.contracts.response import ProductCard
    card = ProductCard(product_id=uuid4(), product_name='Dell Inspiron 15', effective_price=16 * M)
    snapshot = EvidenceSnapshot(cards=[card], search=ProductSearchPlan(category='Laptop', min_ram_gb=16))
    plan = resolve_reference('Có sản phẩm nào mắc hơn xíu', QueryPlan(intent=Intent.PRODUCT_DISCOVERY, product_search=ProductSearchPlan()), snapshot)
    search = plan.product_search
    assert plan.intent == Intent.PRODUCT_DISCOVERY and plan.clarification is None
    assert (search.category, search.min_ram_gb, search.min_price, search.max_price) == ('Laptop', 16, 16 * M + 1, None)
    capped = resolve_reference('Mắc hơn nhưng dưới 10 triệu', QueryPlan(intent=Intent.PRODUCT_DISCOVERY, product_search=ProductSearchPlan()), snapshot)
    assert capped.intent == Intent.UNKNOWN and 'đắt hơn' in capped.clarification


def test_model_number_does_not_hide_a_real_price_bound():
    e = evidence('giá iPhone 15 dưới 20 triệu')
    assert (e.min_price, e.max_price, e.target) == (None, 20 * M, None)
