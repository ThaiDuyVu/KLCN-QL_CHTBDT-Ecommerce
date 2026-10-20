"""Read-only routing guards. No model output can authorize a business mutation."""
import re

from app.features.query_understanding.normalization import folded
from app.shared.contracts.enums import Intent, RequestedField
from app.shared.contracts.query import QueryPlan

TRANSACTION_REFUSAL = ('Mình chỉ hỗ trợ tư vấn và tra cứu, không thể thực hiện giao dịch. '
                       'Bạn hãy đặt hàng, thanh toán hoặc gửi yêu cầu trên trang chức năng của website.')


def transaction_request(message: str) -> bool:
    q = folded(message)
    # A guide/status question is read-only; imperative/action requests are refused.
    if re.search(r'\b(?:lam sao|cach|huong dan|how to|da thanh toan chua|trang thai)\b', q):
        # Mixed instructions still cannot hide an explicit delegated action.
        if not re.search(r'\b(?:hay|giup|ho toi|ngay|please|for me)\b', q):
            return False
    actions = (r'\b(?:dat ngay|dat hang|tao don|place .*order|pay for me)\b',
               r'\bthanh toan\b.*\b(?:giup|ho toi|ngay)\b',
               r'\b(?:huy|cancel)\b.*\b(?:don|order)\b',
               r'\b(?:sua|thay doi|cap nhat|doi)\b.*\b(?:dia chi|don hang|gio hang)\b',
               r'\b(?:them|xoa|bo)\b.*\bgio hang\b',
               r'\btao phieu\b.*\b(?:giup|ho toi)\b')
    return any(re.search(pattern, q) for pattern in actions)


def read_only_plan(message: str) -> QueryPlan | None:
    """Only explicit domains override the model; never derive ownership from text."""
    q = folded(message)
    if transaction_request(message):
        return QueryPlan(intent=Intent.UNKNOWN, clarification=TRANSACTION_REFUSAL)
    if any(x in q for x in ('khach hang khac', 'tai khoan khac', 'mat khau', 'another customer')):
        return QueryPlan(intent=Intent.UNKNOWN, clarification='Mình không cung cấp thông tin riêng của khách hàng khác hoặc mật khẩu.')
    if any(x in q for x in ('chinh sach', 'doi tra', 'tai lieu', 'huong dan demo')):
        return QueryPlan(intent=Intent.KNOWLEDGE_QA, semantic_query=message)
    if 'giup toi nhung gi' in q or 'co the lam gi' in q:
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
