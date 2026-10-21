"""Deterministic domain validation. Model text never supplies SQL or catalog IDs."""
import re
import unicodedata
from decimal import Decimal

from app.shared.contracts.enums import Intent, RequestedField
from app.shared.contracts.query import ProductSearchPlan, ProductSelector, QueryPlan
from app.shared.tracing import event
from app.features.query_understanding.constraints import (
    CATEGORY_ALIASES, GENERAL_CLARIFICATION, NO_ANTECEDENT_CLARIFICATION, PENDING_REFERENCE_CLARIFICATION,
    ConstraintError, ProductEvidence, ReferenceKind, Relation, category_mentions, detect_reference, price_evidence,
    read_capacity, requested_count, validate_brands, validate_capacity_coverage, validate_price_coverage, storage_reference,
)


def folded(value: str) -> str:
    value = unicodedata.normalize('NFD', value.casefold().replace('đ', 'd'))
    return ''.join(c for c in value if not unicodedata.combining(c))


def contains(text: str, phrase: str) -> bool:
    return re.search(r'(?<!\w)' + re.escape(phrase) + r'(?!\w)', text) is not None


def capacity(text: str, slot: str) -> tuple[int | None, bool]:
    constraint = read_capacity(text, slot)
    return (constraint.value, constraint.relation == Relation.MINIMUM) if constraint else (None, False)


def price_slots(text: str) -> tuple[Decimal | None, Decimal | None]:
    # Resolved bounds only; callers needing unresolved amounts use price_evidence().
    evidence = price_evidence(text)
    return evidence.min_price, evidence.max_price



class DomainNormalizer:
    def __init__(self, backend):
        self.backend = backend

    def normalize(self, plan: QueryPlan, context) -> QueryPlan:
        try:
            return self._normalize(plan, context)
        except ConstraintError as exc:
            clarification = QueryPlan(intent=Intent.UNKNOWN, clarification=str(exc))
            event('query.domain_validation', {'original': plan, 'normalized': clarification,
                                            'constraint_rejected': True})
            return clarification

    def _normalize(self, plan: QueryPlan, context) -> QueryPlan:
        q = folded(context.current_message)
        personal = any(x in q for x in ('cua toi', 'my order', 'gio hang'))
        product_words = ('laptop', 'may tinh xach tay', 'tablet', 'may tinh bang', 'dien thoai', 'smartphone', 'iphone', 'xiaomi', 'xiaomii', 'samsung', 'dell', 'sku', 'product id', 'variant id', 'ram', 'ssd', 're hon', 'may vua re')
        product_words += tuple(alias for aliases in CATEGORY_ALIASES.values() for alias in aliases)
        if not any(x in q for x in product_words) and plan.intent not in {Intent.PRODUCT_DISCOVERY, Intent.PRODUCT_DETAIL, Intent.PRODUCT_COMPARE}:
            return plan
        if personal and plan.intent in {Intent.CART_STATUS, Intent.ORDER_STATUS, Intent.PAYMENT_STATUS, Intent.WARRANTY_STATUS, Intent.WARRANTY_TICKET_STATUS}:
            return plan
        categories, brands = self.backend.taxonomy()
        if not categories or not brands:
            # Missing taxonomy is a dependency/configuration failure, not NOT_FOUND.
            raise ValueError('Catalog taxonomy unavailable or incomplete')
        products = self.backend.catalog_names()
        count = requested_count(q)
        named = [r for r in products if contains(q, folded(r['productName']))]
        # Prefer a longer name when one catalog name is a prefix of another.
        named = [r for r in named if not any(r is not other and folded(r['productName']) in folded(other['productName']) for other in named)]
        sku = re.search(r'\bsku\s*[:#]?\s*([A-Za-z0-9][A-Za-z0-9_-]*)', context.current_message, re.I)
        ids = re.search(r'(product|variant)\s*id\s*[:#]?\s*([0-9a-fA-F-]{36})', context.current_message, re.I)
        if named and any(re.search(re.escape(folded(r['productName'])) + r'\s+(pro|ultra|plus|max|lite)\b', q) for r in named):
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Model có hậu tố chưa khớp chính xác catalog. Bạn vui lòng xác nhận tên hoặc SKU.')
        scope_prefix = q[:min(q.index(folded(r['productName'])) for r in named)] if len(named) > 1 else q
        pending_storage = storage_reference(q) if not named and not sku and not ids else None
        # Leave a supported reference unresolved for the owned-session resolver; do not
        # interpret its storage amount as either an unscoped filter or a grammar error.
        coverage_text = q[:pending_storage.start()] + q[pending_storage.end():] if pending_storage else scope_prefix
        validate_capacity_coverage(coverage_text)
        prices = price_evidence(q)
        # A monetary condition that was not understood must not disappear from discovery.
        validate_price_coverage(q, prices)
        minimum, maximum = prices.min_price, prices.max_price
        ram, ram_min = capacity(scope_prefix, 'ram')
        storage, storage_min = capacity(scope_prefix, 'storage')
        if ram == 0 or storage == 0:
            return QueryPlan(intent=Intent.UNKNOWN, clarification='RAM và dung lượng phải lớn hơn 0GB. Bạn vui lòng xác nhận lại cấu hình.')
        if minimum is not None and maximum is not None and minimum > maximum:
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Khoảng giá bị mâu thuẫn. Bạn muốn giá tối thiểu và tối đa bao nhiêu?')
        mentions = category_mentions(q, categories, folded, contains)
        category_names = list(dict.fromkeys(c for c, _ in mentions))
        # Product identities may span categories in a comparison; general discovery may not.
        if len(category_names) > 1 and len(named) < 2:
            raise ConstraintError('Mình hỗ trợ tìm từng nhóm sản phẩm. Bạn muốn tìm loại sản phẩm nào trước?')
        category = category_names[0] if len(category_names) == 1 else None
        previous_search = plan.product_search
        if previous_search and previous_search.category:
            model_category = previous_search.category
            if contains(q, folded(model_category)) and model_category not in categories and not mentions:
                raise ConstraintError('Danh mục được yêu cầu chưa xác minh được trong catalog hiện tại.')
        brand_slots = validate_brands(q, plan, mentions, brands, named, folded, contains, context.current_message)
        fields = []
        for field, signals in [(RequestedField.PRICE, ('gia', 'price')), (RequestedField.STOCK, ('con hang', 'het hang', 'ton kho', 'stock', 'bao nhieu may')), (RequestedField.WARRANTY, ('bao hanh', 'warranty')), (RequestedField.SPECIFICATIONS, ('ram', 'ssd', 'mau', 'color', 'cau hinh', 'dung luong'))]:
            if any(x in q for x in signals): fields.append(field)
        targets = []
        if sku:
            targets = [ProductSelector(sku=sku[1])]
        elif ids:
            targets = [ProductSelector(**{ids[1].lower()+'_id': ids[2]})]
        elif named:
            unique_names = sorted(set(r['productName'] for r in named), key=lambda n: q.index(folded(n)))
            if len(unique_names) > 3:
                return QueryPlan(intent=Intent.UNKNOWN, clarification='Bạn vui lòng giới hạn tối đa ba sản phẩm trong một yêu cầu.')
            targets = [ProductSelector(product_name=n) for n in unique_names]
        # Unknown/typo model names must not fall through to unscoped discovery.
        elif re.search(r'(?:xiaom\w*|iphone|samsung galaxy|dell inspiron|dell xps)\s+\d+', q):
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Mình chưa xác định chính xác model trong catalog. Bạn vui lòng xác nhận tên sản phẩm hoặc SKU (ví dụ Xiaomi 15).')
        # Grounded model identities absent from catalog must never degrade to discovery.
        if not targets and previous_search and any(
                t.product_name and contains(q, folded(t.product_name)) for t in previous_search.targets):
            raise ConstraintError('Chưa xác minh được tên sản phẩm trong catalog. Bạn vui lòng xác nhận tên hoặc SKU.')
        if len(targets) > 1 and (ram is not None or storage is not None):
            raise ConstraintError('Cấu hình chung của nhiều sản phẩm chưa rõ. Bạn vui lòng nêu cấu hình ngay sau từng tên sản phẩm.')
        if targets:
            scoped_targets = []
            for i, target in enumerate(targets):
                if len(targets) == 1:
                    scoped = q
                else:
                    start = q.index(folded(target.product_name)) + len(folded(target.product_name))
                    end = q.index(folded(targets[i+1].product_name)) if i+1 < len(targets) else len(q)
                    scoped = q[start:end]
                    if price_evidence(scoped).spans:
                        raise ConstraintError('Giới hạn giá riêng theo từng sản phẩm chưa được hỗ trợ. Bạn vui lòng tách yêu cầu hoặc xác nhận phạm vi giá.')
                validate_capacity_coverage(scoped)
                tr, tr_min = capacity(scoped, 'ram')
                ts, ts_min = capacity(scoped, 'storage')
                color = variant_color(scoped, context.current_message if len(targets) == 1 else context.current_message[start:end])
                if len(targets) > 1 and (tr_min or ts_min or any(x in scoped for x in ('ca hai', 'moi may', 'each'))):
                    raise ConstraintError('Điều kiện theo từng sản phẩm chưa biểu diễn được an toàn. Bạn vui lòng xác nhận cấu hình chính xác cho từng máy.')
                scoped_targets.append(target.model_copy(update={
                    'ram_gb': tr if not tr_min else None,
                    'storage_gb': ts if not ts_min else None, 'color': color}))
            targets = scoped_targets
        else:
            if variant_color(q, context.current_message) or re.search(r'\b(?:dung|chinh xac|exactly)\s+(?:ban\s+)?(?:ram|ssd|storage)\b', q):
                raise ConstraintError('Bạn vui lòng xác định tên sản phẩm hoặc SKU để kiểm tra cấu hình chính xác/màu của biến thể.')
        if len(targets) == 1:
            ram, ram_min = capacity(q, 'ram')
            storage, storage_min = capacity(q, 'storage')
        exact = bool(targets)
        compare = any(x in q for x in ('so sanh', 'compare'))
        if compare and len(targets) != 2:
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Bạn vui lòng xác định đúng hai sản phẩm cần so sánh bằng tên hoặc SKU.')
        # A family search is discovery; an explicit target is detail/compare.
        intent = Intent.PRODUCT_COMPARE if compare else Intent.PRODUCT_DETAIL if targets else Intent.PRODUCT_DISCOVERY
        reference = detect_reference(q)
        # A bounded comparative carries its own price (A3.1 R2-R4); STORAGE stays a filter-less snapshot reference.
        followup = reference in {ReferenceKind.CHEAPER, ReferenceKind.ORDINAL_FIRST, ReferenceKind.ORDINAL_SECOND, ReferenceKind.LAST}
        if followup and reference != ReferenceKind.CHEAPER and (category or brand_slots):
            # An ordinal/last phrase next to a newly stated category/brand is a new request.
            followup = False
        if followup and not targets:
            if not context.recent_messages:
                return QueryPlan(intent=Intent.UNKNOWN, clarification=NO_ANTECEDENT_CLARIFICATION)
            # Ordinal/last identities are resolved only from the owned snapshot.
            if reference != ReferenceKind.CHEAPER:
                return QueryPlan(intent=Intent.UNKNOWN, clarification=PENDING_REFERENCE_CLARIFICATION)
            previous = plan.product_search
            if previous:
                # A category stated now outranks the model plan and history.
                if not category:
                    category = previous.category
                    aliases = {'phone':'Điện thoại','smartphone':'Điện thoại'}
                    category = aliases.get(folded(category or ''),category)
                    if category and category not in categories: category = None
                    history_text = folded(' '.join(m.content for m in context.recent_messages))
                    if not category: category = 'Laptop' if 'laptop' in history_text else None
                if ram is None:
                    # Repeated assistant variant facts are not user filter requirements.
                    # The owned snapshot resolver subsequently verifies current backend evidence.
                    prior_request = next((m for m in reversed(context.recent_messages)
                                          if m.role.value == 'USER' and contains(folded(m.content), 'ram')), None)
                    if prior_request:
                        ram, _ = capacity(folded(prior_request.content), 'ram')
        if not targets and not category and not brand_slots and ram is None and storage is None and minimum is None and maximum is None and not followup:
            return QueryPlan(intent=Intent.UNKNOWN, clarification=GENERAL_CLARIFICATION)
        cpu = re.findall(r'\b(?:i[3579]|ryzen\s*[3579]|intel core\s*i[3579])\b', q)
        if not targets and ('het hang' in q or 'khong can con hang' in q):
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Bạn muốn kiểm tra tồn kho của sản phẩm nào? Bộ lọc còn hàng/hết hàng cần được xác định rõ.')
        if count is not None and targets and count < len(targets):
            raise ConstraintError('Số lượng gợi ý mâu thuẫn với số sản phẩm cần so sánh. Bạn vui lòng xác nhận lại.')
        evidence = ProductEvidence(category, tuple(brand_slots), tuple(targets), minimum, maximum,
            None if exact and not ram_min else ram, None if exact and not storage_min else storage, count)
        search = ProductSearchPlan(category=category, brands=brand_slots,
            min_price=minimum, max_price=maximum, min_ram_gb=None if exact and not ram_min else ram,
            min_storage_gb=None if exact and not storage_min else storage,
            cpu_keywords=cpu, in_stock_only=RequestedField.STOCK in fields and not targets,
            semantic_query=context.current_message, targets=targets, top_k=max(1,len(targets)) if targets else count or 5)
        evidence.validate(search)
        normalized = QueryPlan(intent=intent, requested_fields=fields,
                               product_search=search, references=plan.references if followup else {})
        event('query.domain_validation', {'original': plan, 'normalized': normalized, 'taxonomy_verified': True})
        return normalized


def variant_color(text, original):
    colors = ('Xanh không gian', 'Xám không gian', 'Xanh dương', 'Xanh băng', 'Xanh lưu ly',
              'Xanh lá', 'Xanh đen', 'Đen', 'Trắng', 'Bạc', 'Xám', 'Tím', 'Vàng', 'Đỏ', 'Hồng', 'Kem', 'Xanh', 'Cam')
    color = next((c for c in colors if contains(original.casefold(), c.casefold())
                  or (('mau' in text or 'color' in text) and contains(text, folded(c)))), None)
    if color is None:
        color = next((v for k, v in {'black': 'Đen', 'white': 'Trắng', 'blue': 'Xanh',
                                   'red': 'Đỏ', 'silver': 'Bạc'}.items() if contains(text, k)), None)
    if color is None and re.search(r'\b(?:mau|color)\s+(?!nao\b|gi\b|sac\b|which\b)[\w-]+', text):
        raise ConstraintError('Màu biến thể chưa xác định rõ. Bạn vui lòng xác nhận màu hoặc SKU; mình sẽ không bỏ qua điều kiện màu.')
    return color
