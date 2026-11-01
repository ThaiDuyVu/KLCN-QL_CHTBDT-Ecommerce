"""Combo within a budget ("100 triệu cho 1 điện thoại và 1 laptop") and approximate prices."""
from decimal import Decimal
from uuid import uuid4

import pytest

from app.features.product_advisor.bundle import choose_bundle
from app.features.query_understanding.constraints import ConstraintError
from app.features.response_generation.ollama_service import OllamaResponseGenerationService
from app.shared.contracts.context import GroundedContext
from app.shared.contracts.enums import Intent
from app.shared.contracts.product import EligibleVariant, ProductMatch
from app.shared.contracts.query import ProductSearchPlan, QueryPlan
from tests.test_production_remediation import normalized

M = Decimal('1000000')


def match(name, category, *prices, stock=2):
    product_id = uuid4()
    return ProductMatch(product_id=product_id, product_name=name, category_name=category, eligible_variants=[
        EligibleVariant(product_id=product_id, variant_id=uuid4(), sku=f'{name}-{p}', effective_price=p * M,
                        original_price=p * M, available_quantity=stock, ram='8GB', storage='256GB', color='Đen')
        for p in prices])


PHONES = [match('iPhone 16', 'Điện thoại', 22, 31), match('Galaxy A55', 'Điện thoại', Decimal('8.5'))]
LAPTOPS = [match('XPS 13', 'Laptop', 33, 42), match('ROG G14', 'Laptop', 53), match('Vivobook', 'Laptop', 13)]


def test_combo_uses_the_budget_without_exceeding_it():
    chosen, fits = choose_bundle([PHONES, LAPTOPS], 100 * M, 100 * M)
    assert fits and sum(m.eligible_variants[0].effective_price for m in chosen) == 84 * M
    assert [m.product_name for m in chosen] == ['iPhone 16', 'ROG G14']


def test_combo_over_budget_falls_back_to_the_cheapest_and_says_so():
    chosen, fits = choose_bundle([PHONES, LAPTOPS], 15 * M, 15 * M)
    assert not fits and [m.product_name for m in chosen] == ['Galaxy A55', 'Vivobook']


def test_combo_never_proposes_an_out_of_stock_variant():
    chosen, _ = choose_bundle([[match('Out', 'Điện thoại', 50, stock=0), *PHONES], LAPTOPS], 100 * M, 100 * M)
    assert 'Out' not in {m.product_name for m in chosen}
    assert choose_bundle([[match('Out', 'Điện thoại', 50, stock=0)], LAPTOPS], 100 * M, 100 * M) == ([], False)


def test_combo_request_becomes_one_search_per_type_under_one_budget():
    plan = normalized('Giờ tôi có 100tr tôi cần mua 1 điện thoại và 1 laptop')
    assert plan.intent == Intent.PRODUCT_DISCOVERY and plan.budget == 100 * M
    assert [(s.category, s.max_price) for s in plan.bundle] == [('Điện thoại', 100 * M), ('Laptop', 100 * M)]


@pytest.mark.parametrize('prompt', ['Tôi có 100tr mua 2 điện thoại và 1 laptop', 'Laptop RAM 16GB và 1 điện thoại dưới 50tr'])
def test_unsupported_combo_shapes_ask_instead_of_guessing(prompt):
    plan = normalized(prompt)
    assert plan.product_search is None and plan.clarification


def test_combo_answer_lists_each_item_and_the_remaining_budget():
    chosen, fits = choose_bundle([PHONES, LAPTOPS], 100 * M, 100 * M)
    plan = QueryPlan(intent=Intent.PRODUCT_DISCOVERY, product_search=ProductSearchPlan(), budget=100 * M,
                     bundle=[ProductSearchPlan(category='Điện thoại'), ProductSearchPlan(category='Laptop')],
                     references={'budget_kind': 'maximum', 'bundle_within_budget': 'true' if fits else 'false'})
    text = OllamaResponseGenerationService(None).generate(GroundedContext(query_plan=plan, user_query='combo', products=chosen))
    assert 'ngân sách 100.000.000 đ' in text and 'Tổng cộng: 84.000.000 đ, còn dư 16.000.000 đ' in text
    assert 'Điện thoại: iPhone 16' in text and 'Laptop: ROG G14' in text


@pytest.mark.parametrize('prompt,low,high', [('laptop khoảng 25tr', 21250000, 28750000), ('dt tầm 15 triệu', 12750000, 17250000)])
def test_approximate_price_with_product_type_searches_a_band(prompt, low, high):
    search = normalized(prompt).product_search
    assert (search.min_price, search.max_price) == (Decimal(low), Decimal(high)) and search.target_price is not None
