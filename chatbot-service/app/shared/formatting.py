from decimal import Decimal


def vnd(amount: Decimal) -> str:
    # Same shape as the product card (12.990.000 đ); decimals kept only when present.
    text = format(amount, ',.2f').removesuffix('.00')
    return text.replace(',', '_').replace('.', ',').replace('_', '.') + ' đ'


def decimal_text(value: Decimal) -> str:
    """6.1 -> "6,1", 16 -> "16" (Vietnamese decimal comma, no trailing zeros)."""
    text = format(value.normalize(), 'f')
    return text.replace('.', ',')
