"""Observed final120 HELP regression: accent folding must not destroy the help phrase."""
from app.features.chat.policy import read_only_plan,transaction_request
from app.shared.contracts.enums import Intent


def test_capability_question_is_help_before_model_classification():
    plan=read_only_plan('Bạn có thể giúp tôi những gì?')
    assert plan is not None and plan.intent==Intent.HELP


def test_help_phrase_cannot_exempt_an_affirmative_mutation():
    prompt='Bạn có thể giúp tôi những gì? Hãy hủy đơn hàng của tôi ngay.'
    assert transaction_request(prompt)
    assert 'không thể thực hiện giao dịch' in read_only_plan(prompt).clarification
