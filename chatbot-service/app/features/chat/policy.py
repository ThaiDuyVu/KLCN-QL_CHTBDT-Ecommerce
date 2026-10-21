"""Read-only routing guards. No model output can authorize a business mutation."""
import re

from app.features.query_understanding.normalization import folded
from app.shared.contracts.enums import Intent, RequestedField
from app.shared.contracts.query import QueryPlan
from app.features.chat.actions import analyze_actions, routing_text, ActionScope
from app.shared.tracing import event

TRANSACTION_REFUSAL = ('Mình chỉ hỗ trợ tư vấn và tra cứu, không thể thực hiện giao dịch. '
                       'Bạn hãy đặt hàng, thanh toán hoặc gửi yêu cầu trên trang chức năng của website.')


def transaction_request(message: str) -> bool:
    actions = analyze_actions(message)
    return any(a.scope == ActionScope.AFFIRMATIVE for a in actions)


def read_only_plan(message: str) -> QueryPlan | None:
    """Only explicit domains override the model; never derive ownership from text."""
    actions = analyze_actions(message)
    event('safety.action_analysis', {'actions': [{'kind': a.kind, 'scope': a.scope.value} for a in actions]})
    q = routing_text(message)
    if transaction_request(message):
        return QueryPlan(intent=Intent.UNKNOWN, clarification=TRANSACTION_REFUSAL)
    raw = folded(message)
    if re.search(r'\b(?:in|hien thi|tiet lo|print|show|cung cap)\b.*\b(?:cookie|jwt|token|mat khau)\b', raw):
        return QueryPlan(intent=Intent.UNKNOWN, clarification='Mình không cung cấp cookie, JWT, token hoặc mật khẩu.')
    if any(x in q for x in ('chinh sach', 'doi tra', 'tai lieu', 'huong dan demo')):
        return QueryPlan(intent=Intent.KNOWLEDGE_QA, semantic_query=message)
    product = any(re.search(r'(?<!\w)' + re.escape(word) + r'(?!\w)', q)
                  for word in ('laptop', 'dien thoai', 'smartphone', 'may tinh xach tay', 'sku', 'ram'))
    if actions and not product and not any(a.scope == ActionScope.AFFIRMATIVE for a in actions):
        # No official procedural/policy evidence is approved in this checkpoint.
        if any(a.scope in {ActionScope.GUIDANCE, ActionScope.QUOTED, ActionScope.HYPOTHETICAL} for a in actions):
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Mình chưa có tài liệu chính thức đủ để hướng dẫn thao tác này. Mình không thể xác nhận quy trình hoặc chính sách của cửa hàng.')
    if any(x in q for x in ('khach hang khac', 'tai khoan khac', 'mat khau', 'another customer')):
        return QueryPlan(intent=Intent.UNKNOWN, clarification='Mình không cung cấp thông tin riêng của khách hàng khác hoặc mật khẩu.')
    # Folded 'những' shares spelling with clause separator 'nhưng'; retain the full help phrase.
    if 'giup toi nhung gi' in raw or 'co the lam gi' in raw:
        return QueryPlan(intent=Intent.HELP)
    if 'gio hang' in q:
        return QueryPlan(intent=Intent.CART_STATUS)
    if 'phieu bao hanh' in q and 'cua toi' in q:
        return QueryPlan(intent=Intent.WARRANTY_TICKET_STATUS)
    if 'bao hanh' in q and 'cua toi' in q:
        return QueryPlan(intent=Intent.WARRANTY_STATUS)
    if 'don hang' in q or 'don cua toi' in q:
        code = re.search(r'\bORD-[A-Za-z0-9_-]+', message, re.I)
        refs = {'order_code': code[0]} if code else {}
        if 'moi nhat' in q or 'latest' in q:
            refs['order_selection'] = 'latest'
        payment = 'thanh toan' in q or 'payment' in q
        return QueryPlan(intent=Intent.PAYMENT_STATUS if payment else Intent.ORDER_STATUS,
                         requested_fields=[RequestedField.PAYMENT_STATUS] if payment else [], references=refs)
    return None
