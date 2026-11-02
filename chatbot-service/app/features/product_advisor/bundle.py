"""Pick one verified variant per product type so the combo total best uses the customer's budget."""
from decimal import Decimal
from itertools import product

from app.shared.contracts.product import ProductMatch

# 40 options per type keeps a three-type search at 64k combinations.
MAX_OPTIONS = 40


def choose_bundle(candidates: list[list[ProductMatch]], goal: Decimal | None,
                  cap: Decimal | None) -> tuple[list[ProductMatch], bool]:
    """Return one match per type (each narrowed to the chosen variant) and whether it fits the budget.

    With a budget the combo closest to the goal without exceeding the cap wins (for a maximum budget
    that is the most complete use of it). Nothing fits: the cheapest combo, flagged as over budget.
    Variants known to be out of stock at the selected branch are never proposed.
    """
    options = []
    for matches in candidates:
        variants = [(m, v) for m in matches for v in m.eligible_variants if v.available_quantity != 0]
        if not variants:
            return [], False
        options.append(variants[:MAX_OPTIONS])

    def total(combo):
        return sum((v.effective_price for _, v in combo), Decimal('0'))

    if cap is None:
        # No budget: the best-ranked product of each type, at its cheapest eligible variant.
        best = [min((x for x in o if x[0] is o[0][0]), key=lambda x: x[1].effective_price) for o in options]
        fits = True
    else:
        combos = list(product(*options))
        fitting = [c for c in combos if total(c) <= cap]
        fits = bool(fitting)
        target = goal if goal is not None else cap
        best = min(fitting, key=lambda c: abs(target - total(c))) if fitting else min(combos, key=total)
    return [m.model_copy(update={'eligible_variants': [v]}) for m, v in best], fits
