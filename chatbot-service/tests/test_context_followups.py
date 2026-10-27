"""Multi-turn follow-ups resolved from the owned snapshot (context-eval W1-W6)."""
from decimal import Decimal
from uuid import uuid4

import pytest

from app.features.conversation.models import EvidenceSnapshot
from app.features.conversation.references import resolve_reference
from app.features.query_understanding.constraints import (
    GENERAL_CLARIFICATION, PENDING_REFERENCE_CLARIFICATION, ReferenceKind, detect_reference,
)
from app.features.query_understanding.normalization import folded, variant_color
from app.shared.contracts.enums import Intent
from app.shared.contracts.query import ProductSearchPlan, QueryPlan
from app.shared.contracts.response import ProductCard

M = Decimal('1000000')


def card(name, price, ram='8GB', storage='256GB', product_id=None):
    return ProductCard(product_id=product_id or uuid4(), product_name=name, variant_id=uuid4(),
                       effective_price=price * M, ram=ram, storage=storage)


def snap(cards, list_cards=(), **search):
    return EvidenceSnapshot(cards=list(cards), list_cards=list(list_cards), search=ProductSearchPlan(**search))


def discovery(**search):
    return QueryPlan(intent=Intent.PRODUCT_DISCOVERY, product_search=ProductSearchPlan(**search))


LAPTOPS = snap([card('Dell XPS 13', 33, '16GB'), card('Dell Inspiron 15', 16, '16GB')],
               category='Laptop', brands=['Dell'], min_ram_gb=16)


@pytest.mark.parametrize('message,plan,expected', [
    # W1: a short follow-up restating one filter keeps the rest of the previous search.
    ('Apple thì có mẫu nào', discovery(brands=['Apple']), ('Laptop', ['Apple'], 16, None)),
    ('dưới 35 triệu', discovery(max_price=35 * M), ('Laptop', ['Dell'], 16, 35 * M)),
    ('RAM 32GB', discovery(min_ram_gb=32), ('Laptop', ['Dell'], 32, None)),
])
def test_partial_follow_up_merges_with_previous_search(message, plan, expected):
    search = resolve_reference(message, plan, LAPTOPS).product_search
    assert (search.category, search.brands, search.min_ram_gb, search.max_price) == expected


def test_stated_product_type_is_a_new_request_not_a_refinement():
    plan = discovery(category='Điện thoại', brands=['Samsung'])
    assert resolve_reference('giờ tôi muốn xem điện thoại Samsung', plan, LAPTOPS) is plan


def test_clear_price_and_storage_narrow_a_multi_product_list():
    priced = snap(LAPTOPS.cards, category='Laptop', brands=['Dell'], min_ram_gb=16, max_price=35 * M)
    plan = resolve_reference('bỏ điều kiện giá, có bản 1TB không', QueryPlan(intent=Intent.UNKNOWN), priced)
    search = plan.product_search
    assert plan.intent == Intent.PRODUCT_DISCOVERY
    assert (search.max_price, search.min_storage_gb, search.brands) == (None, 1024, ['Dell'])


def test_variant_follow_up_on_one_product_becomes_an_exact_selector():
    xiaomi = card('Xiaomi 15', 19, '12GB')
    plan = resolve_reference('còn bản RAM 16GB thì sao', discovery(min_ram_gb=16),
                             snap([xiaomi], targets=[{'product_name': 'Xiaomi 15'}]))
    assert plan.intent == Intent.PRODUCT_DETAIL
    assert [(t.product_id, t.ram_gb) for t in plan.product_search.targets] == [(xiaomi.product_id, 16)]


def test_comparative_bound_is_not_carried_as_a_user_requirement():
    # W4: "mắc hơn" then "rẻ hơn" must not keep the floor derived from the first comparative.
    plan = resolve_reference('có sản phẩm nào mắc hơn xíu', discovery(), snap([card('Dell Inspiron 15', 16, '16GB')],
                                                                         category='Laptop', min_ram_gb=16))
    assert plan.product_search.min_price == 16 * M + 1
    assert (plan.references['carry_min_price'], plan.references['carry_max_price']) == ('', '')


@pytest.mark.parametrize('message,kind', [
    ('nó còn hàng không', ReferenceKind.LAST), ('máy này bảo hành mấy tháng', ReferenceKind.LAST),
    ('con đó giá bao nhiêu', ReferenceKind.LAST), ('con nào rẻ nhất', ReferenceKind.CHEAPEST),
    ('máy nào đắt nhất', ReferenceKind.PRICIEST), ('cái nào RAM nhiều hơn', ReferenceKind.MOST_RAM),
    ('còn mẫu nào khác không', ReferenceKind.ANOTHER), ('có mẫu khác không', ReferenceKind.ANOTHER),
])
def test_follow_up_phrases_are_detected(message, kind):
    assert detect_reference(folded(message)) == kind


@pytest.mark.parametrize('message', ['Có chiếc nào RAM cao hơn không?', 'So sánh hai máy khác nhau thế nào', 'No thanks'])
def test_look_alike_phrases_are_not_follow_ups(message):
    assert detect_reference(folded(message)) not in {ReferenceKind.MOST_RAM, ReferenceKind.ANOTHER, ReferenceKind.LAST}


def test_which_is_cheaper_picks_between_shown_products_but_existence_question_searches():
    compared = snap([card('Dell XPS 13', 33, '16GB'), card('MacBook Air M2', 23)], targets=[
        {'product_name': 'Dell XPS 13'}, {'product_name': 'MacBook Air M2'}])
    pick = resolve_reference('cái nào rẻ hơn', QueryPlan(intent=Intent.UNKNOWN, clarification=GENERAL_CLARIFICATION), compared)
    assert pick.intent == Intent.PRODUCT_DETAIL and pick.product_search.targets[0].variant_id == compared.cards[1].variant_id
    search = resolve_reference('có cái nào rẻ hơn không', discovery(), compared)
    assert search.intent == Intent.PRODUCT_DISCOVERY and search.product_search.max_price == 23 * M - 1


def test_ordinal_and_pick_use_the_list_after_a_detail_answer():
    listed = [card('Dell XPS 13', 33, '16GB'), card('MacBook Pro 14 M3', 40), card('ASUS ROG', 40, '16GB')]
    after_detail = snap([listed[1]], list_cards=listed, category='Laptop')
    first = resolve_reference('máy đầu tiên bảo hành bao lâu', QueryPlan(intent=Intent.UNKNOWN, clarification=PENDING_REFERENCE_CLARIFICATION), after_detail)
    assert first.product_search.targets[0].variant_id == listed[0].variant_id
    it = resolve_reference('nó còn hàng không', QueryPlan(intent=Intent.UNKNOWN, clarification=PENDING_REFERENCE_CLARIFICATION), after_detail)
    assert it.product_search.targets[0].variant_id == listed[1].variant_id


def test_product_warranty_question_is_not_routed_to_account_warranties():
    one = snap([card('MacBook Air M2', 23)], category='Laptop')
    plan = resolve_reference('máy này bảo hành mấy tháng', QueryPlan(intent=Intent.WARRANTY_STATUS), one)
    assert plan.intent == Intent.PRODUCT_DETAIL
    own = resolve_reference('máy này của tôi còn bảo hành không', QueryPlan(intent=Intent.WARRANTY_STATUS), one)
    assert own.intent == Intent.WARRANTY_STATUS


def test_another_excludes_products_already_shown():
    plan = resolve_reference('còn mẫu nào khác không', QueryPlan(intent=Intent.UNKNOWN, clarification=GENERAL_CLARIFICATION),
                             snap([card('iPhone 16', 22)], category='Điện thoại', brands=['Apple'], top_k=1))
    assert plan.product_search.brands == ['Apple'] and plan.references['result_limit'] == '1'
    assert plan.references['exclude_products'] and plan.product_search.top_k == 2


def test_model_word_is_not_an_unknown_colour():
    assert variant_color(folded('có mẫu khác không'), 'có mẫu khác không') is None
