"""Internal clause-scoped action analysis; it never grants mutation capabilities."""
from dataclasses import dataclass
from enum import Enum
import re
from app.features.query_understanding.normalization import folded


class ActionScope(Enum):
    AFFIRMATIVE = 'affirmative'
    NEGATED = 'negated'
    GUIDANCE = 'guidance'
    INFORMATION = 'information'
    QUOTED = 'quoted'
    HYPOTHETICAL = 'hypothetical'


@dataclass(frozen=True)
class Action:
    kind: str
    scope: ActionScope


ACTIONS = {
    'order': r'\b(?:dat hang|dat ngay|tao don|place\s+(?:an?\s+)?order)\b',
    'payment': r'\b(?:thanh toan|pay(?: for me)?)\b',
    'cancel': r'\b(?:huy|cancel)\b[^,;.!?\n]*?\b(?:don|order)\b',
    'change': r'\b(?:sua|thay doi|cap nhat|doi)\b[^,;.!?\n]*?\b(?:dia chi|don hang|gio hang)\b',
    'cart': r'\b(?:them|xoa|bo)\b[^,;.!?\n]*?\bgio hang\b',
    'ticket': r'\btao phieu\b',
}
DELEGATE = r'\b(?:hay|giup|ho toi|ngay|please|for me|thuc hien|tien hanh)\b'
GUIDE = r'\b(?:cach|huong dan|lam sao|how to|nghia la gi)\b'
NEGATED_PREFIX = r'\b(?:dung|khong(?:\s+(?:yeu cau|nho|muon|can|duoc))?|do not|dont)\s*(?:(?:ban|toi|hay|giup|ho)\s+)*$'
QUOTES = re.compile(r'"[^"\n]*"|“[^”\n]*”|‘[^’\n]*’|\x27[^\x27\n]*\x27|`[^`\n]*`')
CLAUSES = re.compile(r'[,;.!?\n]+|\b(?:nhung|tuy nhien|roi|sau do|but|and then|ma hay)\b')


def _actions(text):
    return sorted((m.start(), m.end(), kind) for kind, grammar in ACTIONS.items() for m in re.finditer(grammar, text))


def analyze_actions(message):
    q = folded(message)
    actions = []
    # Quoted instructions are data unless an explicit outer request delegates them.
    for quote in QUOTES.finditer(q):
        outer = q[:quote.start()] + ' ' + q[quote.end():]
        delegated = bool(re.search(r'\b(?:thuc hien|tien hanh|hay lam)\b', outer)) and not re.search(GUIDE, outer)
        quote_prefix = re.sub(r'\b(?:thuc hien|tien hanh|lam)\s*$', '', q[:quote.start()])
        negated = bool(re.search(NEGATED_PREFIX, quote_prefix))
        for _, _, kind in _actions(quote.group()):
            actions.append(Action(kind, ActionScope.NEGATED if negated else ActionScope.AFFIRMATIVE if delegated else ActionScope.QUOTED))
    q = QUOTES.sub(lambda m: ' ' * len(m.group()), q)
    for clause in CLAUSES.split(q):
        matches = _actions(clause)
        local = []
        for start, end, kind in matches:
            prefix, suffix = clause[:start], clause[end:]
            negated = bool(re.search(NEGATED_PREFIX, prefix))
            # A coordinated bare action inherits local negation/guidance, unless an
            # explicit new imperative resets scope ("đừng X và hãy Y").
            coordinated = prefix.rsplit(' va ', 1)
            if not negated and len(coordinated) == 2 and not re.search(DELEGATE, coordinated[1]):
                earlier = local[-1:] if local and local[-1].scope in {ActionScope.NEGATED, ActionScope.GUIDANCE} else []
                if earlier and matches[0][0] < start:
                    action = Action(kind, earlier[-1].scope)
                    actions.append(action); local.append(action)
                    continue
            guide_positions = [m.start() for m in re.finditer(GUIDE, prefix)]
            imperative_positions = [m.start() for m in re.finditer(r'\b(?:hay|ngay|please|thuc hien|tien hanh)\b', prefix)]
            guide = bool(guide_positions) and (not imperative_positions or max(guide_positions) > max(imperative_positions))
            status = bool(re.search(r'\b(?:da\s*$|trang thai|tinh trang|co cho|co ho tro|ho tro|chap nhan)', prefix) or re.search(r'^\s*(?:chua|the nao)\b', suffix))
            hypothetical = bool(re.search(r'\b(?:neu|gia su|if|suppose)\b', prefix)) and not imperative_positions
            scope = (ActionScope.NEGATED if negated else ActionScope.GUIDANCE if guide else ActionScope.INFORMATION if status else
                     ActionScope.HYPOTHETICAL if hypothetical else ActionScope.AFFIRMATIVE)
            action = Action(kind, scope)
            actions.append(action); local.append(action)
    return actions


def routing_text(message):
    """Remove only clauses that carry a negated/quoted action, for domain routing."""
    q = QUOTES.sub(' ', folded(message))
    return ', '.join(clause for clause in CLAUSES.split(q)
                     if not any(a.scope == ActionScope.NEGATED for a in analyze_actions(clause)))
