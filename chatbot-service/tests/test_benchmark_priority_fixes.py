"""Benchmark 27/10/2026, priority causes R1, R2, R3, R5, R6, R7 (BAO_CAO_KET_QUA_BENCHMARK.md §4–5)."""
import pytest

from app.features.chat.actions import analyze_actions
from app.features.chat.policy import TRANSACTION_REFUSAL, read_only_plan
from app.features.query_understanding.constraints import ConstraintError, validate_capacity_coverage
from app.features.query_understanding.normalization import folded
from app.shared.contracts.enums import Intent
from tests.test_production_remediation import normalized


def targets(message):
    plan = normalized(message)
    assert not plan.clarification, plan.clarification
    return [(t.product_name, t.sku, t.storage_gb) for t in plan.product_search.targets]


# R2: "bản 512GB" after a named product; a capacity inside a SKU.
def test_storage_variant_of_a_named_product():
    assert targets('Xiaomi 15 bản 512GB giá bao nhiêu') == [('Xiaomi 15', None, 512)]


def test_capacity_inside_sku_is_not_a_condition():
    validate_capacity_coverage(folded('SKU DEV-ROGG14-WH-1TB còn hàng không'))
    assert targets('SKU DEV-ROGG14-WH-1TB còn hàng không') == [(None, 'DEV-ROGG14-WH-1TB', None)]


def test_small_variant_amount_stays_ambiguous():
    assert normalized('Xiaomi 15 bản 16GB').clarification
    with pytest.raises(ConstraintError):
        validate_capacity_coverage(folded('ban 16gb'))


# R3: product names typed without their brand.
@pytest.mark.parametrize('message,names', [
    ('Inspiron 15 giá bao nhiêu', ['Dell Inspiron 15']),
    ('Galaxy A55 bảo hành bao nhiêu tháng', ['Samsung Galaxy A55']),
    ('so sánh XPS 13 và Inspiron 15', ['Dell XPS 13', 'Dell Inspiron 15']),
    ('so sánh Galaxy A55 với iPhone 15', ['Samsung Galaxy A55', 'iPhone 15']),
])
def test_brandless_product_names_resolve_to_catalog(message, names):
    assert [name for name, _, _ in targets(message)] == names


def test_brand_remainder_that_is_only_a_number_is_not_a_name():
    # "Xiaomi 15" without its brand is "15"; that must not turn "iPhone 15" into Xiaomi.
    assert [name for name, _, _ in targets('iPhone 15 còn hàng không')] == ['iPhone 15']


def test_family_word_alone_is_not_a_product():
    plan = normalized('galaxy có những mẫu nào')
    assert not (plan.product_search and plan.product_search.targets)


# R5: delegated orders are refused; price complaints are not orders.
@pytest.mark.parametrize('message', ['đặt giúp tôi 1 iPhone 15', 'đặt luôn cho mình', 'mua giúp mình cái này',
                                     'đặt cho tôi 2 cái', 'thêm vào giỏ giúp tôi'])
def test_delegated_order_is_refused(message):
    plan = read_only_plan(message)
    assert plan is not None and plan.clarification == TRANSACTION_REFUSAL


@pytest.mark.parametrize('message', ['tìm máy đắt hơn', 'cái nào đắt nhất', 'đắt cho mình quá', 'giá đó đắt cho tôi thế'])
def test_expensive_is_not_an_order(message):
    assert analyze_actions(message) == []


# R6: an order code alone routes to the owned-order lookup with that code.
@pytest.mark.parametrize('message,intent', [
    ('đơn ORD-KHONG-TON-TAI của tôi', Intent.ORDER_STATUS),
    ('ORD-ABC123 tới đâu rồi', Intent.ORDER_STATUS),
    ('đơn hàng ORD-ABC123 thanh toán chưa', Intent.PAYMENT_STATUS),
])
def test_order_code_is_kept_for_owned_lookup(message, intent):
    plan = read_only_plan(message)
    assert plan.intent == intent and plan.references['order_code'].startswith('ORD-')


# R7: store information and documented how-tos go to documents.
@pytest.mark.parametrize('message', ['giờ mở cửa của cửa hàng', 'bảo hành ở đâu, mang máy tới chỗ nào',
                                     'hướng dẫn kiểm tra thanh toán', 'có xuất hóa đơn VAT không',
                                     'cửa hàng có giao hàng tận nơi không'])
def test_store_information_is_a_knowledge_question(message):
    assert read_only_plan(message).intent == Intent.KNOWLEDGE_QA


def test_owned_records_and_undocumented_how_tos_keep_their_routes():
    assert read_only_plan('bảo hành của tôi').intent == Intent.WARRANTY_STATUS
    assert 'chưa có tài liệu chính thức' in read_only_plan('Giúp tôi xem cách hủy đơn hàng.').clarification
    assert read_only_plan('chào bạn') is None
