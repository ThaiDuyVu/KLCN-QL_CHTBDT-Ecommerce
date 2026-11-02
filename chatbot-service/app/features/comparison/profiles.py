"""Which technical criteria are compared for each product category.

Adding a category (catalog diversification): add an entry to PROFILES keyed by the folded category
name, built from the criterion helpers below. Examples for categories the shop may add later:

    'tu lanh':  (PRICE, spec_number('capacity', 'Dung tích', ('Dung tích',), 'lít', more='dung tích lớn hơn'),
                 spec_text('energy', 'Tiết kiệm điện', ('Nhãn năng lượng',)), WARRANTY, STOCK)
    'tivi':     (PRICE, spec_number('screen', 'Kích thước', ('Kích thước màn hình',), 'inch', more='màn hình lớn hơn'),
                 spec_text('resolution', 'Độ phân giải', ('Độ phân giải',)), WARRANTY, STOCK)

A criterion only claims "better" when it has a direction (`better`) and a number on both sides;
text specifications (CPU, pin, độ phân giải as text) are displayed side by side, never judged.
Categories without a profile fall back to DEFAULT plus every specification the products share.
"""
import re
from dataclasses import dataclass
from decimal import Decimal

from app.features.query_understanding.normalization import folded


@dataclass(frozen=True)
class Criterion:
    key: str
    label: str
    source: str                      # 'price' | 'variant' | 'spec' | 'stock'
    field: str = ''                  # variant attribute, e.g. 'ram'
    spec_keys: tuple[str, ...] = ()  # specification keys, matched case/accent-insensitively
    unit: str = ''
    numeric: bool = False
    better: str | None = None        # 'higher' | 'lower' | None (neutral fact)
    more: str = ''                   # summary phrase for the higher value, e.g. "RAM cao hơn"


def variant_number(key, label, field, unit, more):
    return Criterion(key, label, 'variant', field=field, unit=unit, numeric=True, better='higher', more=more)


def spec_number(key, label, spec_keys, unit, more, better=None):
    return Criterion(key, label, 'spec', spec_keys=spec_keys, unit=unit, numeric=True, better=better, more=more)


def spec_text(key, label, spec_keys):
    return Criterion(key, label, 'spec', spec_keys=spec_keys)


PRICE = Criterion('price', 'Giá', 'price', unit='đ', numeric=True, better='lower', more='giá khởi điểm cao hơn')
RAM = variant_number('ram', 'RAM', 'ram', 'GB', 'RAM cao hơn')
STORAGE = variant_number('storage', 'Bộ nhớ', 'storage', 'GB', 'bộ nhớ lớn hơn')
WARRANTY = Criterion('warranty', 'Bảo hành', 'variant', field='warranty_months', unit='tháng', numeric=True,
                     better='higher', more='bảo hành dài hơn')
STOCK = Criterion('stock', 'Tồn kho tại chi nhánh', 'stock')
CPU = spec_text('cpu', 'CPU', ('CPU', 'Vi xử lý', 'Chip'))
# A larger screen is a fact, not "better" (portability), so it has no direction.
SCREEN = spec_number('screen', 'Màn hình', ('Màn hình',), 'inch', 'màn hình lớn hơn')
BATTERY = spec_text('battery', 'Pin', ('Pin',))

TECH_DEVICE = (PRICE, RAM, STORAGE, CPU, SCREEN, BATTERY, WARRANTY, STOCK)
PROFILES = {
    'laptop': TECH_DEVICE,
    'dien thoai': TECH_DEVICE,
    'tablet': TECH_DEVICE,
}
DEFAULT = (PRICE, WARRANTY, STOCK)


def profile_for(category: str | None, spec_keys: list[str]) -> tuple[Criterion, ...]:
    profile = PROFILES.get(folded(category or ''))
    if profile is not None:
        return profile
    extra = tuple(spec_text(f'spec:{folded(k)}', k, (k,)) for k in spec_keys)
    return DEFAULT[:1] + extra + DEFAULT[1:]


def number(text: str | None, unit: str) -> Decimal | None:
    """First number in a value: "16GB" -> 16, "1TB" -> 1024 (GB), "OLED 6,1 inch" -> 6.1."""
    m = re.search(r'(\d+(?:[.,]\d+)?)\s*(tb|gb)?', (text or '').lower())
    if not m:
        return None
    value = Decimal(m[1].replace(',', '.'))
    return value * 1024 if unit == 'GB' and m[2] == 'tb' else value
