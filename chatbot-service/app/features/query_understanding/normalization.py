"""Deterministic domain validation. Model text never supplies SQL or catalog IDs."""
import re
import unicodedata
from decimal import Decimal

from app.shared.contracts.enums import Intent, RequestedField
from app.shared.contracts.query import ProductSearchPlan, ProductSelector, QueryPlan
from app.shared.tracing import event


def folded(value: str) -> str:
    value = unicodedata.normalize('NFD', value.casefold().replace('đ', 'd'))
    return ''.join(c for c in value if not unicodedata.combining(c))


def contains(text: str, phrase: str) -> bool:
    return re.search(r'(?<!\w)' + re.escape(phrase) + r'(?!\w)', text) is not None


def capacity(text: str, slot: str) -> tuple[int | None, bool]:
    label = 'ram' if slot == 'ram' else r'(?:ssd|storage|dung luong|bo nho)'
    m = re.search(label + r'\s*(?:(it nhat|toi thieu|>=|>|tu)\s*)?(\d+(?:[.,]\d+)?)\s*(gb|tb)?', text)
    if not m:
        m = re.search(r'(\d+(?:[.,]\d+)?)\s*(gb|tb)\s*' + label, text)
        if m:
            return int(Decimal(m[1].replace(',', '.')) * (1024 if m[2] == 'tb' else 1)), False
        return None, False
    return int(Decimal(m[2].replace(',', '.')) * (1024 if m[3] == 'tb' else 1)), bool(m[1])


def price_slots(text: str) -> tuple[Decimal | None, Decimal | None]:
    amount = r'(\d+(?:[.,]\d+)*)\s*(trieu|tr|million|k|nghin|vnd|dong)?'
    def money(number, unit):
        if re.fullmatch(r'\d{1,3}(?:[.,]\d{3})+', number):
            number = number.replace('.', '').replace(',', '')
        return Decimal(number.replace(',', '.')) * {'trieu': 1000000, 'tr': 1000000, 'million': 1000000, 'k': 1000, 'nghin': 1000}.get(unit, 1)
    interval = re.search(r'(?:gia\s*)?tu\s*' + amount + r'\s*(?:den|toi|-)\s*' + amount, text)
    if interval and (interval[2] or interval[4] or 'gia' in text[max(0, interval.start()-8):interval.start()+4]):
        return money(interval[1], interval[2] or interval[4]), money(interval[3], interval[4] or interval[2])
    low = re.search(r'(?:gia\s*)?(?:tren|hon|toi thieu|gia tu)\s*' + amount, text)
    high = re.search(r'(?:duoi|khong qua|khong vuot|toi da|under|<=)\s*' + amount, text)
    # Capacity language such as RAM tối thiểu 12GB is not a monetary slot.
    if low and low[2] is None and 'gia' not in text[max(0, low.start()-8):low.start()+4]:
        low = None
    if high and high[2] is None and 'gia' not in text[max(0, high.start()-8):high.start()+4]:
        high = None
    return (money(low[1], low[2]) if low else None, money(high[1], high[2]) if high else None)


class DomainNormalizer:
    def __init__(self, backend):
        self.backend = backend

    def normalize(self, plan: QueryPlan, context) -> QueryPlan:
        q = folded(context.current_message)
        personal = any(x in q for x in ('cua toi', 'my order', 'gio hang'))
        product_words = ('laptop', 'dien thoai', 'smartphone', 'iphone', 'xiaomi', 'xiaomii', 'samsung', 'dell', 'sku', 'product id', 'variant id', 'ram', 'ssd', 're hon', 'may vua re')
        if not any(x in q for x in product_words) and plan.intent not in {Intent.PRODUCT_DISCOVERY, Intent.PRODUCT_DETAIL, Intent.PRODUCT_COMPARE}:
            return plan
        if personal and plan.intent in {Intent.CART_STATUS, Intent.ORDER_STATUS, Intent.PAYMENT_STATUS, Intent.WARRANTY_STATUS, Intent.WARRANTY_TICKET_STATUS}:
            return plan
        unsupported_capacity = re.search(r'(?:ram|ssd|storage|dung luong|bo nho)\s*(?:duoi|toi da|<|tu\s*\d+\s*(?:gb|tb)?\s*(?:den|toi)|-\d)', q)
        wrong_unit = re.search(r'(?:ram|ssd|storage|dung luong|bo nho)\s*(?:>=|it nhat|toi thieu)?\s*\d+\s*(mb|kb)\b', q)
        if unsupported_capacity or wrong_unit:
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Bộ lọc dung lượng này chưa được hỗ trợ rõ ràng. Bạn vui lòng dùng mức tối thiểu GB/TB hoặc xác định một biến thể bằng SKU.')
        categories, brands = self.backend.taxonomy()
        products = self.backend.catalog_names()
        named = [r for r in products if contains(q, folded(r['productName']))]
        # Prefer a longer name when one catalog name is a prefix of another.
        named = [r for r in named if not any(r is not other and folded(r['productName']) in folded(other['productName']) for other in named)]
        sku = re.search(r'\bsku\s*[:#]?\s*([A-Za-z0-9][A-Za-z0-9_-]*)', context.current_message, re.I)
        ids = re.search(r'(product|variant)\s*id\s*[:#]?\s*([0-9a-fA-F-]{36})', context.current_message, re.I)
        if named and any(re.search(re.escape(folded(r['productName'])) + r'\s+(pro|ultra|plus|max|lite)\b', q) for r in named):
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Model có hậu tố chưa khớp chính xác catalog. Bạn vui lòng xác nhận tên hoặc SKU.')
        minimum, maximum = price_slots(q)
        ram, ram_min = capacity(q, 'ram')
        storage, storage_min = capacity(q, 'storage')
        if ram == 0 or storage == 0:
            return QueryPlan(intent=Intent.UNKNOWN, clarification='RAM và dung lượng phải lớn hơn 0GB. Bạn vui lòng xác nhận lại cấu hình.')
        if minimum is not None and maximum is not None and minimum > maximum:
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Khoảng giá bị mâu thuẫn. Bạn muốn giá tối thiểu và tối đa bao nhiêu?')
        laptop = contains(q, 'laptop')
        phone = any(contains(q, x) for x in ('dien thoai', 'smartphone', 'phone', 'iphone'))
        if laptop and phone:
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Mình hỗ trợ tìm từng nhóm sản phẩm. Bạn muốn tìm laptop hay điện thoại trước?')
        category = 'Laptop' if laptop else 'Điện thoại' if phone else None
        if category and category not in categories:
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Danh mục này chưa xác định được trong catalog hiện tại.')
        brand_slots = [b for b in brands if contains(q, folded(b))]
        if contains(q, 'iphone') and 'Apple' in brands and 'Apple' not in brand_slots:
            brand_slots.append('Apple')
        explicit_brand = re.search(r'(?:hãng|thương hiệu|brand)\s+(\w+)', context.current_message.casefold())
        if explicit_brand and not any(folded(b) == folded(explicit_brand[1]) for b in brands):
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Thương hiệu này chưa xác định được trong catalog. Bạn vui lòng xác nhận hãng sản phẩm.')
        fields = []
        for field, signals in [(RequestedField.PRICE, ('gia', 'price')), (RequestedField.STOCK, ('con hang', 'het hang', 'ton kho', 'stock', 'bao nhieu may')), (RequestedField.WARRANTY, ('bao hanh', 'warranty')), (RequestedField.SPECIFICATIONS, ('ram', 'ssd', 'mau', 'color', 'cau hinh', 'dung luong'))]:
            if any(x in q for x in signals): fields.append(field)
        targets = []
        if sku:
            targets = [ProductSelector(sku=sku[1])]
        elif ids:
            targets = [ProductSelector(**{ids[1].lower()+'_id': ids[2]})]
        elif named:
            unique_names = list(dict.fromkeys(r['productName'] for r in named))
            if len(unique_names) > 3:
                return QueryPlan(intent=Intent.UNKNOWN, clarification='Bạn vui lòng giới hạn tối đa ba sản phẩm trong một yêu cầu.')
            targets = [ProductSelector(product_name=n) for n in unique_names]
        # Unknown/typo model names must not fall through to unscoped discovery.
        elif re.search(r'(?:xiaom\w*|iphone|samsung galaxy|dell inspiron|dell xps)\s+\d+', q):
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Mình chưa xác định chính xác model trong catalog. Bạn vui lòng xác nhận tên sản phẩm hoặc SKU (ví dụ Xiaomi 15).')
        colors = ('Xanh không gian', 'Xám không gian', 'Xanh dương', 'Xanh băng', 'Xanh lưu ly', 'Xanh lá', 'Xanh đen', 'Đen', 'Trắng', 'Bạc', 'Xám', 'Tím', 'Vàng', 'Đỏ', 'Hồng', 'Kem', 'Xanh', 'Cam')
        color = next((c for c in colors if contains(context.current_message.casefold(), c.casefold()) or (('mau' in q or 'color' in q) and contains(q, folded(c)))), None)
        if color is None:
            color = next((v for k,v in {'black':'Đen','white':'Trắng','blue':'Xanh','red':'Đỏ','silver':'Bạc'}.items() if contains(q,k)),None)
        if color and not targets:
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Bạn vui lòng xác định tên sản phẩm hoặc SKU để mình kiểm tra đúng màu của biến thể.')
        exact = bool(targets) and (ram is not None or storage is not None or color is not None)
        if targets:
            targets = [t.model_copy(update={'ram_gb': ram if exact and not ram_min else None,
                        'storage_gb': storage if exact and not storage_min else None, 'color': color}) for t in targets]
        compare = any(x in q for x in ('so sanh', 'compare'))
        if compare and len(targets) != 2:
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Bạn vui lòng xác định đúng hai sản phẩm cần so sánh bằng tên hoặc SKU.')
        # A family search is discovery; an explicit target is detail/compare.
        intent = Intent.PRODUCT_COMPARE if compare else Intent.PRODUCT_DETAIL if targets else Intent.PRODUCT_DISCOVERY
        followup = any(x in q for x in ('re hon', 'vua goi y', 'thu hai', 'dau tien'))
        if followup and not targets:
            if not context.recent_messages:
                return QueryPlan(intent=Intent.UNKNOWN, clarification='Bạn muốn so sánh với sản phẩm nào? Mình chưa có sản phẩm tham chiếu trong phiên này.')
            # General reference resolution is deferred; keep only a supported cheaper follow-up.
            if 're hon' not in q:
                return QueryPlan(intent=Intent.UNKNOWN, clarification='Bạn vui lòng xác nhận tên hoặc SKU của sản phẩm vừa nhắc đến.')
            previous = plan.product_search
            if previous:
                category = previous.category
                aliases = {'phone':'Điện thoại','smartphone':'Điện thoại'}
                category = aliases.get(folded(category or ''),category)
                if category and category not in categories: category = None
                history_text = folded(' '.join(m.content for m in context.recent_messages))
                if not category: category = 'Laptop' if 'laptop' in history_text else None
                if ram is None and 'ram' in history_text: ram, _ = capacity(history_text, 'ram')
        if not targets and not category and not brand_slots and ram is None and storage is None and minimum is None and maximum is None and not followup:
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?')
        cpu = re.findall(r'\b(?:i[3579]|ryzen\s*[3579]|intel core\s*i[3579])\b', q)
        if 're hon' in q and context.recent_messages and not targets:
            minimum = None
        if not targets and ('het hang' in q or 'khong can con hang' in q):
            return QueryPlan(intent=Intent.UNKNOWN, clarification='Bạn muốn kiểm tra tồn kho của sản phẩm nào? Bộ lọc còn hàng/hết hàng cần được xác định rõ.')
        search = ProductSearchPlan(category=category, brands=brand_slots,
            min_price=minimum, max_price=maximum, min_ram_gb=None if exact and not ram_min else ram,
            min_storage_gb=None if exact and not storage_min else storage,
            cpu_keywords=cpu, in_stock_only=RequestedField.STOCK in fields and not targets,
            semantic_query=context.current_message, targets=targets, top_k=max(1,len(targets)) if targets else 5)
        normalized = QueryPlan(intent=intent, requested_fields=fields,
                               product_search=search, references=plan.references if followup else {})
        event('query.domain_validation', {'original': plan, 'normalized': normalized, 'taxonomy_verified': True})
        return normalized
