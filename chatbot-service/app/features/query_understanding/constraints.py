"""Utterance evidence for the existing product contract, independent of model output.

A small slot grammar carries comparison operators and target scope. It deliberately
fails closed on unsupported operators instead of approximating them as minima.
No new fields are sent to the backend or exposed in the QueryPlan schema.
"""
from dataclasses import dataclass, field
from decimal import Decimal
from enum import Enum
import re

from app.shared.contracts.query import ProductSearchPlan, ProductSelector


class ConstraintError(ValueError):
    """A stated condition cannot be represented safely by this contract."""


class Relation(Enum):
    EXACT = 'exact'
    MINIMUM = 'minimum'
    MAXIMUM = 'maximum'
    RANGE = 'range'
    STRICT_MINIMUM = 'strict_minimum'


@dataclass(frozen=True)
class CapacityConstraint:
    value: int
    relation: Relation
    spans: tuple[tuple[int, int], ...] = field(default=(), compare=False)


LABELS = {'ram': r'\bram\b', 'storage': r'\b(?:ssd|storage|dung luong|bo nho)\b'}
NUMBER = r'\d+(?:[.,]\d+)?'
FILLERS = r'(?:(?:phai|chi|co|la|can|voi|dung|chinh xac|bang|van|still|of|must be|is)\s+)*'
OPERATORS = {
    Relation.MINIMUM: r'it nhat|toi thieu|khong duoi|khong nho hon|at least|minimum|>=|tu',
    Relation.MAXIMUM: r'duoi|nho hon|khong qua|khong vuot(?: qua)?|toi da|at most|under|below|maximum|<=|<',
    Relation.STRICT_MINIMUM: r'tren|lon hon|more than|>',
}
# Ordered, so >= is never confused with >, and <= with <.
OPERATOR = '|'.join('(?:' + v + ')' for v in OPERATORS.values())


def _gb(number, unit):
    value = Decimal(number.replace(',', '.')) * (1024 if unit == 'tb' else 1)
    if value <= 0 or value != int(value):
        raise ConstraintError('Dung lượng phải là số nguyên GB lớn hơn 0. Bạn vui lòng xác nhận cấu hình.')
    return int(value)


def read_capacity(text: str, slot: str) -> CapacityConstraint | None:
    label = LABELS[slot]
    pattern = label + r'\s*[:=]?\s*' + FILLERS + r'(?:(?P<op>' + OPERATOR + r')\s*)?(?P<n>' + NUMBER + r')\s*(?P<u>gb|tb|mb|kb)?'
    matches = list(re.finditer(pattern, text))
    found = []
    for m in matches:
        if m['u'] in {'mb', 'kb'}:
            raise ConstraintError('Chỉ hỗ trợ dung lượng GB/TB; bạn vui lòng xác nhận lại đơn vị.')
        relation = next((kind for kind, grammar in OPERATORS.items()
                         if m['op'] and re.fullmatch(grammar, m['op'])), Relation.EXACT)
        suffix = text[m.end():]
        if re.match(r'\s*(?:den|toi|-)\s*\d', suffix):
            relation = Relation.RANGE
        found.append(CapacityConstraint(_gb(m['n'], m['u']), relation, ((m.start(), m.end()),)))
    if not matches:
        reverse = re.search(r'(' + NUMBER + r')\s*(gb|tb)\s*' + label, text)
        if reverse:
            found.append(CapacityConstraint(_gb(reverse[1], reverse[2]), Relation.EXACT, ((reverse.start(), reverse.end()),)))
    # Compact variant notation: RAM/storage, never a monetary amount or result count.
    pairs = list(re.finditer(r'\b(' + NUMBER + r')\s*(gb|tb)\s*/\s*(' + NUMBER + r')\s*(gb|tb)\b', text))
    for m in pairs:
        i = 1 if slot == 'ram' else 3
        found.append(CapacityConstraint(_gb(m[i], m[i+1]), Relation.EXACT, ((m.start(), m.end()),)))
    if len(set(found)) > 1:
        raise ConstraintError('Có nhiều điều kiện dung lượng chưa xác định rõ phạm vi. Bạn vui lòng nêu cấu hình cho từng sản phẩm.')
    constraint = found[0] if found else None
    if constraint and constraint.relation not in {Relation.EXACT, Relation.MINIMUM}:
        raise ConstraintError('Bộ lọc dung lượng tối đa/khoảng/giới hạn nghiêm ngặt chưa được hỗ trợ. Bạn vui lòng dùng mức tối thiểu GB/TB hoặc một biến thể cụ thể.')
    # A capacity clause that the grammar did not understand must not disappear.
    if not found and re.search(label + r'[^,;.?!]{0,35}\d+\s*(?:gb|tb|mb|kb)\b', text):
        raise ConstraintError('Chưa hiểu chắc chắn điều kiện dung lượng. Bạn vui lòng nêu mức tối thiểu hoặc cấu hình chính xác.')
    if constraint:
        return CapacityConstraint(constraint.value, constraint.relation, tuple(span for c in found for span in c.spans))
    return None


def storage_reference(text):
    """Existing short reference syntax; identity is resolved from an owned snapshot later."""
    return re.search(r'\bban\s+(\d+)\s*(gb|tb)\b', text)


# Existing customer-facing text. New price/scope wording awaits DR-13b, so every
# price or scope clarification below reuses this exact string.
GENERAL_CLARIFICATION = 'Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?'
NO_ANTECEDENT_CLARIFICATION = 'Bạn muốn so sánh với sản phẩm nào? Mình chưa có sản phẩm tham chiếu trong phiên này.'
PENDING_REFERENCE_CLARIFICATION = 'Bạn vui lòng xác nhận tên hoặc SKU của sản phẩm vừa nhắc đến.'


class PriceRelation(Enum):
    MAXIMUM = 'maximum'
    MINIMUM = 'minimum'
    # Unresolved: direction or bound semantics need DR-01/DR-02 before they can filter.
    COMPARATIVE_MAXIMUM = 'comparative_maximum'
    APPROXIMATE = 'approximate'
    BUDGET = 'budget'
    UNSPECIFIED = 'unspecified'


@dataclass(frozen=True)
class PriceEvidence:
    min_price: Decimal | None
    max_price: Decimal | None
    spans: tuple[tuple[int, int], ...] = ()
    unresolved: tuple[tuple[PriceRelation, tuple[int, int]], ...] = ()


MONEY_UNITS = {'trieu': 1000000, 'tr': 1000000, 'cu': 1000000, 'million': 1000000,
               'k': 1000, 'nghin': 1000, 'ngan': 1000, 'vnd': 1, 'dong': 1, 'd': 1}
# These also spell ordinary words or specs ("cũ", "dòng", "ngắn", "4K"): money only in a price context.
WEAK_UNITS = frozenset(('cu', 'k', 'ngan', 'dong', 'd'))
_UNIT = r'(?:trieu|tr|cu|million|k|nghin|ngan|vnd|dong|d)\b'
_NUMBER = r'(?<![\w.,/-])(\d+(?:[.,]\d+)*)'
_AMOUNT = _NUMBER + r'\s*(' + _UNIT + r')?'
_UPPER = r'(\d+(?:[.,]\d+)*)\s*(' + _UNIT + r')?'
_NOT_MONEY = re.compile(r'\s*(?:gb|tb|mb|kb|inch|hz|may|chiec|cai|san pham|mau|dien thoai|smartphone|phone|'
                        r'laptop|notebook|tablet|thang|nam|ngay|gio|phut)\b')
_RANGE_LINK = r'\s*(?:den|toi|-|–|—)\s*'
RANGES = (
    re.compile(r'(?<!\w)(?:gia\s*)?tu\s*' + _AMOUNT + _RANGE_LINK + _UPPER),
    re.compile(r'(?<!\w)giua\s*' + _AMOUNT + r'\s*(?:va|-|–)\s*' + _UPPER),
    re.compile(_AMOUNT + _RANGE_LINK + _UPPER),
)
# Ordered within each alternation; the tail regex anchors at the amount, so the
# longest operator ending there wins ("re hon" over "hon", "khong qua" over "khong ...").
PRICE_OPERATORS = (
    (PriceRelation.MAXIMUM, r'duoi|khong vuot qua|khong vuot|khong qua|toi da|under|<='),
    (PriceRelation.COMPARATIVE_MAXIMUM, r'(?:gia\s+)?(?:re|thap|nho|it)\s+hon|cheaper than|less than|<'),
    (PriceRelation.MINIMUM, r'gia tu|(?:cao|dat|lon)\s+hon|tren|toi thieu|hon'),
    (PriceRelation.APPROXIMATE, r'tam khoang|tam|khoang|co|chung|xap xi|quanh|~'),
    # Any other negated comparison ("khong duoi") is never read as its bare operator.
    (PriceRelation.UNSPECIFIED, r'khong(?:\s+[a-z]+){1,3}|tu|>=|>'),
)
_OPERATOR_TAIL = re.compile(r'(?<!\w)(?P<op>' + '|'.join(
    '(?P<r%d>%s)' % (i, grammar) for i, (_, grammar) in enumerate(PRICE_OPERATORS))
    + r')\s*(?:(?:la|muc|gia)\s+)*$')
_PRICE_CONTEXT = re.compile(r'(?<!\w)(gia|ngan sach|budget|price)(?!\w)')


def _money(number, unit):
    if re.fullmatch(r'\d{1,3}(?:[.,]\d{3})+', number):
        number = number.replace('.', '').replace(',', '')
    return Decimal(number.replace(',', '.')) * MONEY_UNITS.get(unit, 1)


def _price_context(text, start):
    # Only the clause tail since the last number or punctuation can qualify an amount.
    tail = re.split(r'[\d,;.?!]', text[max(0, start - 24):start])[-1]
    found = _PRICE_CONTEXT.findall(tail)
    return found[-1] if found else None


def _operator(text, start):
    m = _OPERATOR_TAIL.search(text[:start])
    if not m:
        return None
    return next(relation for i, (relation, _) in enumerate(PRICE_OPERATORS) if m['r%d' % i])


def _is_money(text, m):
    number, unit = m[1], m[2]
    if unit is None:
        if _NOT_MONEY.match(text, m.end()):
            return False
        return _price_context(text, m.start()) is not None
    if unit not in WEAK_UNITS:
        return True
    raw = _money(number, None)
    if (unit in {'dong', 'd'} and raw >= 1000) or (unit == 'k' and raw >= 10):
        return True
    return _operator(text, m.start()) is not None or _price_context(text, m.start()) is not None


def price_evidence(text: str) -> PriceEvidence:
    """Relational reading of folded text; anything not understood is reported, never dropped."""
    lows, highs, spans, unresolved = [], [], [], []
    for grammar in RANGES:
        for m in grammar.finditer(text):
            if any(start < m.end() and m.start() < end for start, end in spans):
                continue
            unit_low, unit_high = m[2] or m[4], m[4] or m[2]
            # Bare "15-20" (model numbers, specs) is not money without a unit; "tu A den B"
            # keeps the existing price_slots rule (a unit, or "gia" next to the interval).
            if not unit_low and not (grammar is RANGES[0] and 'gia' in text[max(0, m.start()-8):m.start()+4]):
                continue
            lows.append(_money(m[1], unit_low))
            highs.append(_money(m[3], unit_high))
            spans.append((m.start(), m.end()))
    for m in re.finditer(_AMOUNT, text):
        if any(start <= m.start() < end for start, end in spans) or not _is_money(text, m):
            continue
        value, span = _money(m[1], m[2]), (m.start(), m.end())
        relation = _operator(text, m.start())
        suffix = re.match(r'\s*(tro xuong|do lai|tro lai|tro len)\b', text[m.end():])
        if suffix:
            stated = PriceRelation.MINIMUM if suffix[1] == 'tro len' else PriceRelation.MAXIMUM
            relation = stated if relation in {None, stated} else PriceRelation.UNSPECIFIED
            span = (m.start(), m.end() + suffix.end())
        if relation is None:
            context = _price_context(text, m.start())
            relation = PriceRelation.BUDGET if context in {'ngan sach', 'budget'} else PriceRelation.UNSPECIFIED
        if relation == PriceRelation.MAXIMUM:
            highs.append(value)
            spans.append(span)
        elif relation == PriceRelation.MINIMUM:
            lows.append(value)
            spans.append(span)
        else:
            unresolved.append((relation, span))
    # Decimal('0') is a valid bound: emptiness, not truthiness, decides absence.
    return PriceEvidence(max(lows) if lows else None, min(highs) if highs else None,
                         tuple(sorted(spans)), tuple(unresolved))


def validate_price_coverage(text, evidence=None):
    """Mirror of capacity coverage: every monetary token must belong to a parsed bound."""
    evidence = evidence or price_evidence(text)
    if evidence.unresolved:
        raise ConstraintError(GENERAL_CLARIFICATION)
    for m in re.finditer(r'(?<![\w.,])\d+(?:[.,]\d+)*\s*(?:trieu|tr|million|nghin|vnd)\b', text):
        if not any(start <= m.start() and m.end() <= end for start, end in evidence.spans):
            raise ConstraintError(GENERAL_CLARIFICATION)


class ReferenceKind(Enum):
    ORDINAL_FIRST = 'ORDINAL_FIRST'
    ORDINAL_SECOND = 'ORDINAL_SECOND'
    LAST = 'LAST'
    CHEAPER = 'CHEAPER'
    CHEAPER_BOUNDED = 'CHEAPER_BOUNDED'
    STORAGE = 'STORAGE'


_REFERENT = r'(?<!\w)(?:cai|may|chiec|san pham|mau|con|ban|loai|phien ban)\s+'
_COMPARATIVE = re.compile(r'(?<!\w)(?:(?:gia\s+)?(?:re|thap)\s+hon|cheaper)(?!\w)')
REFERENCE_PATTERNS = (
    (ReferenceKind.ORDINAL_SECOND, re.compile(_REFERENT + r'(?:thu\s+(?:hai|2)|so\s+2)(?!\w)')),
    (ReferenceKind.ORDINAL_SECOND, re.compile(r'^\s*(?:cai\s+)?thu hai(?!\w)(?!\s+(?:tuan|toi|nay|sau|ngay|tien))')),
    (ReferenceKind.ORDINAL_FIRST, re.compile(_REFERENT + r'(?:dau tien|thu\s+(?:nhat|1)|so\s+1)(?!\w)')),
    (ReferenceKind.ORDINAL_FIRST, re.compile(r'^\s*(?:cai\s+)?dau tien(?!\w)')),
    (ReferenceKind.LAST, re.compile(r'(?<!\w)vua goi y(?!\w)|' + _REFERENT + r'vua\s+(?:nhac|roi)(?!\w)')),
)


def detect_reference(text: str) -> ReferenceKind | None:
    """Shared by the normalizer and the owned-snapshot resolver (folded text)."""
    for comparative in _COMPARATIVE.finditer(text):
        # "RAM thấp hơn ..." compares capacity, not price.
        if re.search(r'(?:ram|ssd|storage|dung luong|bo nho)(?:\s+\w+){0,2}\s*$', text[:comparative.start()]):
            continue
        bounded = re.match(r'\s*(?:(?:gia|muc|la|than)\s+)*\d', text[comparative.end():])
        return ReferenceKind.CHEAPER_BOUNDED if bounded else ReferenceKind.CHEAPER
    kind = next((kind for kind, pattern in REFERENCE_PATTERNS if pattern.search(text)), None)
    if kind is None and storage_reference(text):
        return ReferenceKind.STORAGE
    return kind


def validate_capacity_coverage(text):
    constraints = [read_capacity(text, slot) for slot in LABELS]
    spans = [span for c in constraints if c for span in c.spans]
    for m in re.finditer(r'\b' + NUMBER + r'\s*(?:gb|tb|mb|kb)\b', text):
        if not any(start <= m.start() and m.end() <= end for start, end in spans):
            raise ConstraintError('Chưa xác định được dung lượng này thuộc RAM hay bộ nhớ của sản phẩm nào. Bạn vui lòng ghi rõ từng cấu hình.')


CATEGORY_ALIASES = {
    'Laptop': ('laptop', 'notebook', 'may tinh xach tay'),
    'Điện thoại': ('dien thoai', 'smartphone', 'phone', 'iphone'),
    'Tablet': ('tablet', 'may tinh bang', 'ipad'),
}


def category_mentions(text, categories, fold, contains):
    result = []
    for canonical, aliases in CATEGORY_ALIASES.items():
        hits = [alias for alias in aliases if contains(text, alias)]
        if hits:
            if any(re.search(r'\b(?:khong(?: phai)?|tru|not|except|excluding)\s+' + re.escape(alias) + r'\b', text) for alias in hits):
                raise ConstraintError('Bộ lọc loại trừ danh mục chưa được hỗ trợ. Bạn vui lòng nêu danh mục muốn tìm.')
            live = next((c for c in categories if fold(c) == fold(canonical)
                         or fold(c) in aliases), None)
            # A supported language concept absent in live taxonomy is not unrestricted.
            if live is None:
                raise ConstraintError('Danh mục này chưa xác minh được trong catalog hiện tại. Bạn vui lòng xác nhận loại sản phẩm.')
            result.append((live, hits))
    for name in categories:
        if contains(text, fold(name)) and not any(name == c for c, _ in result):
            result.append((name, [fold(name)]))
    return result


# These are syntactic/use-case boundaries, not a list of catalog brands.
IDENTITY_BOUNDARIES = frozenset(('de cho dung phuc vu hoc tap lam viec lap trinh gaming van phong '
    'gia duoi tren tu co voi ram ssd storage dung luong bo nho can nao dang con hang '
    'tot re manh va hoac hay nhung mua giup xin toi muon tim canh bao it nhat toi thieu '
    'khong qua toi da ban mau chay choi game phai chi nhu cau price under for with '
    'available in stock below above at least please find recommend cua hang thuong hieu mong nhe sieu sinh vien '
    'so sanh compare versus vs than mot hai ba bon nam chiec cai cac nhung loai one two three a an the some cheap budget lightweight powerful office work performance high' ).split())


def validate_brands(text, plan, mentions, brands, named, fold, contains, original):
    if re.search(r'\b(?:khong phai|khong chon|khong lay|khong dung|ngoai tru|excluding|except|not)\s+[\w-]+', text):
        raise ConstraintError('Bộ lọc loại trừ thương hiệu/sản phẩm chưa được hỗ trợ. Bạn vui lòng nêu hãng hoặc model muốn tìm.')
    known = [b for b in brands if contains(text, fold(b))]
    if contains(text, 'iphone') and 'Apple' in brands and 'Apple' not in known:
        known.append('Apple')
    # Model slots are corroborating evidence only when explicitly present in the utterance.
    stated = [b for b in (plan.product_search.brands if plan.product_search else [])
              if contains(text, fold(b))]
    prefix = re.search(r'\b(?:hãng|thương hiệu|thuong hieu|brand)\s+([\w-]+)', original.casefold())
    if prefix:
        stated.append(fold(prefix[1]))
    # The identity slot immediately following a product type: "laptop Lenovo RAM ...".
    # Never interpret arbitrary residual/use-case tokens elsewhere as a brand.
    for _, aliases in mentions:
        for alias in aliases:
            for m in re.finditer(r'(?<!\w)' + re.escape(alias) + r'(?!\w)\s+([\w-]+)', text):
                token = m[1]
                if token not in IDENTITY_BOUNDARIES and not token.isdigit():
                    if not any(contains(text[m.start():], fold(row['productName']))
                               and fold(row['productName']).startswith(alias + ' ' + token) for row in named):
                        stated.append(token)
    # The same identity slot can precede the type: "Lenovo laptop". Inspect
    # only the adjacent noun-phrase position, not arbitrary residual tokens.
    for _, aliases in mentions:
        for alias in aliases:
            for m in re.finditer(r'(?<!\w)([\w-]+)\s+' + re.escape(alias) + r'(?!\w)', text):
                token = m[1]
                if token in IDENTITY_BOUNDARIES or token.isdigit() or re.fullmatch(NUMBER + r'(?:gb|tb)', token):
                    continue
                prefix_text = text[:m.start() + len(token)]
                grounded_names = known + [row['productName'] for row in named]
                if any(re.search(r'(?<!\w)' + re.escape(fold(name)) + r'$', prefix_text) for name in grounded_names):
                    continue
                stated.append(token)
    for name in stated:
        if not any(fold(name) == fold(b) or fold(b).startswith(fold(name) + ' ') for b in brands):
            raise ConstraintError('Thương hiệu hoặc tên sản phẩm được yêu cầu chưa xác minh được trong catalog. Bạn vui lòng xác nhận hãng/tên hoặc SKU; mình sẽ không thay bằng hãng khác.')
    return known


def requested_count(text):
    numbers = {'mot': 1, 'hai': 2, 'ba': 3, 'bon': 4, 'nam': 5, 'one': 1, 'two': 2, 'three': 3}
    amount = r'(\d+|mot|hai|ba|bon|nam|one|two|three)'
    noun = r'(?:chiec|cai|san pham|mau|dien thoai|smartphone|phone|laptop|notebook|may tinh xach tay|tablet)\b'
    pattern = r'\b(?:(?:goi y|de xuat|hien thi|recommend|show|chi|dung|tim)\s+)*(?:dung\s+|chi\s+)?' + amount + r'\s+' + noun
    values = [int(m[1]) if m[1].isdigit() else numbers[m[1]] for m in re.finditer(pattern, text)]
    if not values:
        return None
    if len(set(values)) != 1 or not 1 <= values[0] <= 20:
        raise ConstraintError('Số sản phẩm yêu cầu chưa rõ hoặc ngoài phạm vi 1–20. Bạn muốn xem bao nhiêu sản phẩm?')
    return values[0]


@dataclass(frozen=True)
class ProductEvidence:
    """Final gate: no subsequent assembly may silently broaden verified evidence."""
    category: str | None
    brands: tuple[str, ...]
    targets: tuple[ProductSelector, ...]
    min_price: Decimal | None
    max_price: Decimal | None
    min_ram_gb: int | None
    min_storage_gb: int | None
    count: int | None

    def validate(self, search: ProductSearchPlan):
        for field in ('category', 'min_price', 'max_price', 'min_ram_gb', 'min_storage_gb'):
            if getattr(search, field) != getattr(self, field):
                raise ConstraintError('Điều kiện tìm kiếm chưa được giữ nguyên an toàn. Bạn vui lòng xác nhận lại yêu cầu.')
        if tuple(search.brands) != self.brands or tuple(search.targets) != self.targets:
            raise ConstraintError('Phạm vi hãng/biến thể chưa khớp yêu cầu. Bạn vui lòng xác nhận từng sản phẩm.')
        if self.count is not None and search.top_k > self.count:
            raise ConstraintError('Số lượng gợi ý chưa khớp yêu cầu. Bạn vui lòng xác nhận lại.')
