"""Technical comparison: same category compared by numbers, mixed categories declined."""
from decimal import Decimal
from uuid import uuid4

import pytest

from app.features.comparison.builder import build_comparison, comparison_text, cross_category_message, mixed_categories
from app.features.comparison.profiles import PROFILES, profile_for
from app.shared.contracts.product import EligibleVariant, ProductMatch
from tests.test_production_remediation import normalized

M = Decimal('1000000')


def match(name, category, variants, **specs):
    product_id = uuid4()
    return ProductMatch(product_id=product_id, product_name=name, category_name=category, specifications=specs,
                        eligible_variants=[EligibleVariant(product_id=product_id, variant_id=uuid4(), sku=f'{name}-{i}',
                                                           original_price=p * M, effective_price=p * M, ram=ram, storage=storage,
                                                           available_quantity=stock, warranty_months=warranty)
                                           for i, (p, ram, storage, stock, warranty) in enumerate(variants)])


XPS = match('Dell XPS 13', 'Laptop', [(33, '16GB', '512GB', 2, 24), (42, '32GB', '1TB', 2, 24)],
            **{'CPU': 'Intel Core Ultra', 'Màn hình': '13,4 inch', 'Pin': 'Pin tích hợp'})
MBA = match('MacBook Air M2', 'Laptop', [(23, '8GB', '256GB', 0, 12)],
            **{'CPU': 'Apple M2', 'Màn hình': '13,6 inch', 'Pin': 'MagSafe'})
IPHONE = match('iPhone 15', 'Điện thoại', [(18, '6GB', '128GB', 3, 12)])


def rows(comparison):
    return {r.key: (r.values, r.best) for r in comparison.rows}


def test_same_category_rows_use_catalog_numbers_and_mark_only_measurable_wins():
    table = rows(build_comparison([XPS, MBA]))
    assert table['price'] == (['33.000.000 đ – 42.000.000 đ', '23.000.000 đ'], [1])
    assert table['ram'] == (['16GB / 32GB', '8GB'], [0])
    assert table['storage'] == (['512GB / 1TB', '256GB'], [0])
    assert table['warranty'] == (['24 tháng', '12 tháng'], [0])
    # Text specifications and screen size are shown, never judged.
    assert table['cpu'] == (['Intel Core Ultra', 'Apple M2'], [])
    assert table['screen'] == (['13,4 inch', '13,6 inch'], [])
    assert table['stock'] == (['Còn 4 máy', 'Hết hàng'], [])


def test_summary_is_short_and_numeric():
    comparison = build_comparison([XPS, MBA])
    assert comparison.summary == [
        'MacBook Air M2 có giá khởi điểm thấp hơn 10.000.000 đ.',
        'Dell XPS 13 có RAM cao hơn (16GB–32GB so với 8GB).',
        'Dell XPS 13 có bộ nhớ lớn hơn (512GB–1TB so với 256GB).',
        'MacBook Air M2 có màn hình lớn hơn (13,6 inch so với 13,4 inch).',
        'Dell XPS 13 có bảo hành dài hơn (24 tháng so với 12 tháng).',
    ]
    assert comparison_text(comparison).startswith('So sánh Dell XPS 13 và MacBook Air M2 (cùng nhóm Laptop):')


def test_overlapping_configurations_have_no_winner():
    a = match('A', 'Laptop', [(20, '8GB', '256GB', 1, 12), (25, '16GB', '512GB', 1, 12)])
    b = match('B', 'Laptop', [(21, '12GB', '512GB', 1, 12)])
    assert rows(build_comparison([a, b]))['ram'][1] == []


def test_mixed_categories_are_declined_with_an_explanation():
    assert mixed_categories([IPHONE, XPS]) and build_comparison([IPHONE, XPS]) is None
    message = cross_category_message([IPHONE, XPS])
    assert 'iPhone 15 thuộc nhóm Điện thoại, còn Dell XPS 13 thuộc nhóm Laptop' in message
    assert 'không so sánh' in message


def test_unknown_category_falls_back_to_price_warranty_and_shared_specifications():
    fridge = [match(n, 'Tủ lạnh', [(p, None, None, 1, 24)], **{'Dung tích': v}) for n, p, v in (('F1', 10, '300 lít'), ('F2', 12, '420 lít'))]
    keys = [r.key for r in build_comparison(fridge).rows]
    assert keys[0] == 'price' and 'spec:dung tich' in keys and 'warranty' in keys
    assert profile_for('Laptop', []) is PROFILES['laptop']


@pytest.mark.parametrize('prompt', ['so sánh iPhone 15 với Dell XPS 13', 'Xiaomi 15 với iPhone 15 cái nào hơn'])
def test_named_comparison_is_not_filtered_by_a_category_alias(prompt):
    plan = normalized(prompt)
    assert plan.intent.value == 'PRODUCT_COMPARE' and plan.product_search.category is None
