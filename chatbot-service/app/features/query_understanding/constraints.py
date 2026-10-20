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
