"""Side-by-side technical comparison built only from verified product evidence (no model text)."""
from decimal import Decimal

from app.features.comparison.profiles import Criterion, number, profile_for
from app.features.query_understanding.normalization import folded
from app.shared.contracts.comparison import ComparedProduct, ComparisonRow, ProductComparison
from app.shared.contracts.product import ProductMatch
from app.shared.contracts.response import ProductCard
from app.shared.formatting import decimal_text, vnd

MISSING = '—'


def mixed_categories(matches: list[ProductMatch]) -> bool:
    return len({folded(m.category_name or '') for m in matches}) > 1


def cross_category_message(matches: list[ProductMatch]) -> str:
    parts = [f"{m.product_name} thuộc nhóm {m.category_name or 'khác'}" for m in matches]
    names = ' với '.join(m.product_name for m in matches)
    kinds = ' hoặc '.join(dict.fromkeys(f"hai {(m.category_name or 'sản phẩm').lower()}" for m in matches))
    return (f"{', còn '.join(parts)}. Mình chỉ so sánh thông số kỹ thuật giữa các sản phẩm cùng loại để kết quả "
            f"có ý nghĩa, nên không so sánh {names}. Bạn có thể chọn {kinds} để mình so sánh nhé.")


def _spec(match: ProductMatch, criterion: Criterion) -> str | None:
    wanted = {folded(k) for k in criterion.spec_keys}
    return next((v for k, v in match.specifications.items() if folded(k) in wanted and v), None)


def _numbers(match: ProductMatch, criterion: Criterion) -> list[Decimal]:
    variants = match.eligible_variants
    if criterion.source == 'price':
        values = [v.effective_price for v in variants]
    elif criterion.source == 'variant':
        values = [Decimal(raw) if isinstance(raw, int) else number(raw, criterion.unit)
                  for raw in (getattr(v, criterion.field) for v in variants)]
    else:
        values = [number(_spec(match, criterion), criterion.unit)]
    return sorted(v for v in values if v is not None)


def _amount(value: Decimal, unit: str) -> str:
    if unit == 'đ':
        return vnd(value)
    if unit == 'GB' and value >= 1024 and value % 1024 == 0:
        return f'{decimal_text(value / 1024)}TB'
    return f'{decimal_text(value)}{"" if unit == "GB" else " "}{unit}'


def _display(match: ProductMatch, criterion: Criterion) -> str:
    if criterion.source == 'stock':
        known = [v.available_quantity for v in match.eligible_variants if v.available_quantity is not None]
        if not known:
            return 'Chưa xác định'
        total = sum(known)
        return f'Còn {total} máy' if total else 'Hết hàng'
    if criterion.source == 'spec':
        # Specifications are shown as written in the catalog; numbers are read only to compare.
        return _spec(match, criterion) or MISSING
    values = list(dict.fromkeys(_numbers(match, criterion)))
    if not values:
        return MISSING
    if criterion.source == 'price':
        return vnd(values[0]) if len(values) == 1 else f'{vnd(values[0])} – {vnd(values[-1])}'
    return ' / '.join(_amount(v, criterion.unit) for v in values)


def _best(numbers: list[list[Decimal]], better: str | None) -> list[int]:
    """A product is better only when every one of its configurations beats every other product's."""
    if better is None or any(not n for n in numbers):
        return []
    if better == 'lower':
        # Price compares the starting price customers see ("từ X").
        lows = [n[0] for n in numbers]
        winner = min(range(len(lows)), key=lows.__getitem__)
        return [winner] if all(lows[winner] < lows[j] for j in range(len(lows)) if j != winner) else []
    for i, own in enumerate(numbers):
        if all(own[0] > other[-1] for j, other in enumerate(numbers) if j != i):
            return [i]
    return []


def _summary(criterion: Criterion, names: list[str], numbers: list[list[Decimal]], best: list[int]) -> str | None:
    two = len(names) == 2
    if criterion.key == 'price' and best:
        i = best[0]
        if two:
            return f'{names[i]} có giá khởi điểm thấp hơn {vnd(numbers[1 - i][0] - numbers[i][0])}.'
        return f'{names[i]} có giá khởi điểm thấp nhất ({vnd(numbers[i][0])}).'
    def span(n):
        return _amount(n[0], criterion.unit) if n[0] == n[-1] else f'{_amount(n[0], criterion.unit)}–{_amount(n[-1], criterion.unit)}'
    if best:
        i = best[0]
        phrase = criterion.more if two else criterion.more.replace(' hơn', ' nhất')
        others = ' so với '.join(span(numbers[j]) for j in range(len(names)) if j != i) if two else ''
        return f'{names[i]} có {phrase} ({span(numbers[i])}{" so với " + others if others else ""}).'
    if criterion.numeric and criterion.better is None and all(len(n) == 1 for n in numbers):
        # Neutral fact (screen size): only when every product has one clear value.
        values = [n[0] for n in numbers]
        top = max(range(len(values)), key=values.__getitem__)
        if two and values[0] != values[1]:
            return f'{names[top]} có {criterion.more} ({span(numbers[top])} so với {span(numbers[1 - top])}).'
    return None


def build_comparison(matches: list[ProductMatch], cards: list[ProductCard] = ()) -> ProductComparison | None:
    if not 2 <= len(matches) <= 3 or mixed_categories(matches):
        return None
    images = {c.product_id: c.image_url for c in cards}
    names = [m.product_name or str(m.product_id) for m in matches]
    shared_specs = [k for k in matches[0].specifications if all(k in m.specifications for m in matches[1:])]
    rows, summary = [], []
    for criterion in profile_for(matches[0].category_name, shared_specs):
        values = [_display(m, criterion) for m in matches]
        if all(v == MISSING for v in values):
            continue
        numbers = [_numbers(m, criterion) for m in matches] if criterion.numeric else []
        best = _best(numbers, criterion.better) if numbers else []
        rows.append(ComparisonRow(key=criterion.key, label=criterion.label, values=values, best=best))
        line = _summary(criterion, names, numbers, best) if numbers else None
        if line:
            summary.append(line)
    return ProductComparison(
        category_name=matches[0].category_name,
        products=[ComparedProduct(product_id=m.product_id, product_name=n, category_name=m.category_name,
                                  image_url=images.get(m.product_id)) for m, n in zip(matches, names)],
        rows=rows, summary=summary)


def comparison_text(comparison: ProductComparison) -> str:
    names = [p.product_name for p in comparison.products]
    joined = ' và '.join([', '.join(names[:-1]), names[-1]]) if len(names) > 2 else ' và '.join(names)
    category = f' (cùng nhóm {comparison.category_name})' if comparison.category_name else ''
    lines = [f'So sánh {joined}{category}:', '', 'Tóm lại:']
    lines += [f'• {line}' for line in comparison.summary] or ['• Các thông số đo được của các sản phẩm tương đương nhau.']
    lines += ['', 'Thông số chi tiết của từng máy nằm trong bảng so sánh bên dưới.']
    return '\n'.join(lines)
