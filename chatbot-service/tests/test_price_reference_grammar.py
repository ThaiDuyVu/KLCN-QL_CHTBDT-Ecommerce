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


@pytest.mark.parametrize('text,relation', [
    ('rẻ hơn 15 triệu', PriceRelation.COMPARATIVE_MAXIMUM), ('giá thấp hơn 20 triệu', PriceRelation.COMPARATIVE_MAXIMUM),
    ('ít hơn 20 triệu', PriceRelation.COMPARATIVE_MAXIMUM), ('< 20 triệu', PriceRelation.COMPARATIVE_MAXIMUM),
    ('tầm 15 triệu', PriceRelation.APPROXIMATE), ('khoảng 15tr', PriceRelation.APPROXIMATE),
    ('ngân sách 20tr', PriceRelation.BUDGET), ('giá 15 triệu', PriceRelation.UNSPECIFIED),
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
