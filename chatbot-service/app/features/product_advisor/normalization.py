"""Parsing of catalog capacity strings. Unknown formats never become zero."""

import re
from decimal import Decimal

_CAPACITY = re.compile(r"^\s*(\d+(?:\.\d+)?)\s*(GB|TB)\s*$", re.IGNORECASE)


def capacity_gb(value: str | None) -> Decimal | None:
    if value is None:
        return None
    match = _CAPACITY.fullmatch(value)
    if match is None:
        return None
    amount = Decimal(match.group(1))
    return amount * (1024 if match.group(2).upper() == "TB" else 1)
