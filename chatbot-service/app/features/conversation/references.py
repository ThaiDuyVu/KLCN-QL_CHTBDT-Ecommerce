"""Resolve only from owned displayed evidence, never from raw history facts."""
import re
from decimal import Decimal
from app.features.query_understanding.normalization import folded, contains, capacity
from app.shared.contracts.enums import Intent, RequestedField
from app.shared.contracts.query import QueryPlan, ProductSearchPlan, ProductSelector
from app.shared.tracing import event
from app.features.query_understanding.constraints import (
    CATEGORY_ALIASES, GENERAL_CLARIFICATION, NO_ANTECEDENT_CLARIFICATION, PENDING_REFERENCE_CLARIFICATION,
    PRICE_COMPARATIVES, SELECTIONS, WHICH_COMPARATIVE, ReferenceKind, detect_reference, price_evidence, storage_reference,
)

# Application-controlled antecedent requests the resolver may replace; exact strings, never substrings.
ANTECEDENT_CLARIFICATIONS = frozenset((NO_ANTECEDENT_CLARIFICATION, PENDING_REFERENCE_CLARIFICATION, GENERAL_CLARIFICATION))
PRODUCT_REFERENCES = frozenset((ReferenceKind.LAST, ReferenceKind.ORDINAL_FIRST, ReferenceKind.ORDINAL_SECOND))
# Words that make a warranty question about the customer's own records rather than the product shown.
PERSONAL_WARRANTY = ('cua toi', 'toi da mua', 'phieu', 'yeu cau bao hanh', 'gui bao hanh', 'dang bao hanh')
CLEAR_PRICE = re.compile(r'(?<!\w)(?:bo|khong can|khong quan tam|bat ky|khong gioi han)\s+(?:dieu kien\s+|muc\s+)?(?:gia|ngan sach)(?!\w)')


def stated_scope(q, plan):
    """Category/brand present in the current utterance; model or history slots do not count."""
    categories = {canonical for canonical, aliases in CATEGORY_ALIASES.items() if any(contains(q, a) for a in aliases)}
    search = plan.product_search
    if search and search.category and contains(q, folded(search.category)):
        categories.add(search.category)
    brands = {folded(b) for b in (search.brands if search else []) if contains(q, folded(b))}
    return categories, brands


def scope_changed(categories, brands, base):
    if categories:
        current = folded(base.category) if base and base.category else None
        if not any(current in (folded(c), *CATEGORY_ALIASES.get(c, ())) for c in categories):
            return True
    return bool(brands - {folded(b) for b in (base.brands if base else [])})


def _gb(value):
    m = re.match(r'\s*(\d+)\s*(gb|tb)', (value or '').lower())
    return None if not m else int(m[1]) * (1024 if m[2] == 'tb' else 1)


def _bound(value):
    return '' if value is None else str(value)


def _detail(kind, q, card, snapshot, storage=None):
    if storage:
        selector=ProductSelector(product_id=card.product_id,storage_gb=storage)
        search=ProductSearchPlan(targets=[selector], min_ram_gb=snapshot.search.min_ram_gb if snapshot.search else None)
    else:
        selector=ProductSelector(variant_id=card.variant_id) if card.variant_id else ProductSelector(product_id=card.product_id)
        search=ProductSearchPlan(targets=[selector])
    fields=([RequestedField.STOCK] if 'con hang' in q else [RequestedField.PRICE] if 'gia' in q or 're' in q.split()
            else [RequestedField.WARRANTY] if 'bao hanh' in q else [RequestedField.SPECIFICATIONS])
    event('conversation.reference_resolution',{'kind':kind.value,'status':'RESOLVED','product_id':card.product_id,
            'variant_id':card.variant_id,'captured_at':snapshot.captured_at,'refresh':'live_backend'})
    return QueryPlan(intent=Intent.PRODUCT_DETAIL,requested_fields=fields,product_search=search,
                     references={'source':'owned_session_snapshot'})


def refine(message, q, plan, snapshot, storage=None):
    """A follow-up that restates only some filters ("chỉ Apple thôi", "dưới 35 triệu") keeps the rest of the
    owned snapshot search; a stated product type is a new request and is left alone."""
    base = snapshot.search if snapshot and snapshot.cards else None
    search = plan.product_search
    if base is None or (search and search.targets) or plan.intent not in {Intent.PRODUCT_DISCOVERY, Intent.UNKNOWN}:
        return None
    if plan.clarification and plan.clarification not in ANTECEDENT_CLARIFICATIONS:
        return None
    categories, _ = stated_scope(q, plan)
    prices = price_evidence(q)
    if categories or prices.unresolved:
        return None
    brands = [b for b in (search.brands if search else []) if contains(q, folded(b))]
    ram, _ = capacity(q, 'ram')
    size = storage or capacity(q, 'storage')[0]
    clear = CLEAR_PRICE.search(q) is not None
    if not (brands or ram or size or clear or prices.min_price is not None or prices.max_price is not None):
        return None
    data = base.model_dump()
    data['semantic_query'] = message
    targets = [] if brands else list(base.targets)
    if brands:
        data['brands'] = brands
    if clear:
        data['min_price'] = data['max_price'] = None
    if prices.min_price is not None or prices.max_price is not None:
        # A newly stated price replaces the previous price band (and its "khoảng" target).
        data['min_price'], data['max_price'], data['target_price'] = prices.min_price, prices.max_price, prices.target
    if clear:
        data['target_price'] = None
    if targets:
        products = {c.product_id for c in snapshot.cards}
        if len(targets) > 1 or len(products) != 1:
            return None
        # A variant question about the one product shown: exact configuration, not a discovery minimum.
        if ram or size:
            targets = [ProductSelector(product_id=next(iter(products)), ram_gb=ram, storage_gb=size)]
    else:
        if ram:
            data['min_ram_gb'] = ram
        if size:
            data['min_storage_gb'] = size
        if base.targets:
            data['top_k'] = 5
    data['targets'] = [t.model_dump() for t in targets]
    if data['min_price'] is not None and data['max_price'] is not None and data['min_price'] > data['max_price']:
        return QueryPlan(intent=Intent.UNKNOWN, clarification='Khoảng giá bị mâu thuẫn. Bạn muốn giá tối thiểu và tối đa bao nhiêu?')
    refined = ProductSearchPlan.model_validate(data)
    event('conversation.reference_resolution', {'kind': 'REFINEMENT', 'status': 'RESOLVED', 'search': refined,
                                                'captured_at': snapshot.captured_at})
    if targets:
        return QueryPlan(intent=Intent.PRODUCT_DETAIL, requested_fields=plan.requested_fields, product_search=refined,
                         references={'source': 'owned_session_snapshot'})
    return QueryPlan(intent=Intent.PRODUCT_DISCOVERY, requested_fields=plan.requested_fields, product_search=refined,
                     references={'refinement': 'owned_session_snapshot'})


def resolve_reference(message, plan, snapshot):
    q=folded(message)
    kind=detect_reference(q)
    if (plan.intent == Intent.WARRANTY_STATUS and kind in PRODUCT_REFERENCES and snapshot and snapshot.cards
            and not any(term in q for term in PERSONAL_WARRANTY)):
        # "máy này bảo hành mấy tháng" asks about the shown product, not the account's warranty records.
        plan = QueryPlan(intent=Intent.UNKNOWN, clarification=PENDING_REFERENCE_CLARIFICATION)
    if plan.intent not in {Intent.PRODUCT_DISCOVERY, Intent.PRODUCT_DETAIL, Intent.PRODUCT_COMPARE, Intent.UNKNOWN}:
        return plan
    if any(term in q for term in ('gio hang', 'don hang', 'thanh toan', 'phieu bao hanh', 'bao hanh cua toi')):
        return plan
    if kind in {None, ReferenceKind.CHEAPER_BOUNDED}:
        # "rẻ hơn 15 triệu" carries its own bound: an ordinary price filter, refined like any other.
        return refine(message, q, plan, snapshot) or plan
    if plan.product_search and plan.product_search.targets: return plan
    if plan.clarification and plan.clarification not in ANTECEDENT_CLARIFICATIONS:
        # Resolving an antecedent must not discard a price/capacity/domain error.
        return plan
    def clarify(reason,text,case=None):
        event('conversation.reference_resolution',{'kind':kind.value,'status':reason,'case':case})
        return QueryPlan(intent=Intent.UNKNOWN,clarification=text)
    prices=price_evidence(q)
    # The current utterance's price is read independently of the antecedent and is never dropped.
    # A bounded comparative ("rẻ hơn X") stays a clarification until DR-02/DR-12 define R2/R3.
    if prices.unresolved:
        return clarify('UNSUPPORTED_PRICE',GENERAL_CLARIFICATION)
    categories,brands=stated_scope(q,plan)
    shown_products={c.product_id for c in snapshot.cards} if snapshot and snapshot.cards else set()
    # Ordinals and picks address the latest list, even after a detail answer about one of its items.
    listed=(snapshot.list_cards or snapshot.cards) if snapshot else []
    if (kind in PRICE_COMPARATIVES and len({c.product_id for c in listed}) > 1 and WHICH_COMPARATIVE.search(q)
            and not re.search(r'(?<!\w)co\s+(?:\w+\s+){0,2}nao(?!\w)', q) and not q.rstrip(' ?!.').endswith('khong')):
        # "cái nào rẻ hơn" chooses between the products on screen; "có cái nào rẻ hơn không" searches.
        kind = ReferenceKind.CHEAPEST if kind == ReferenceKind.CHEAPER else ReferenceKind.PRICIEST
    if kind in SELECTIONS and (categories or brands):
        # "laptop rẻ nhất" is a new request, not a pick among the products already shown.
        return plan if plan.product_search else clarify('NOT_REFERENCE', GENERAL_CLARIFICATION, 'R3')
    if kind not in PRICE_COMPARATIVES | SELECTIONS | {ReferenceKind.ANOTHER} and (categories or brands):
        # A newly stated category/brand is a new request, not an ordinal/last reference.
        event('conversation.reference_resolution',{'kind':kind.value,'status':'NOT_REFERENCE','case':'R3'})
        return plan
    if snapshot is None or not snapshot.cards:
        return clarify('NO_REFERENCE','Mình chưa có sản phẩm tham chiếu trong phiên này. Bạn vui lòng nêu tên sản phẩm hoặc SKU.','R4')
    if kind in SELECTIONS:
        key={ReferenceKind.CHEAPEST:lambda c:c.effective_price,ReferenceKind.PRICIEST:lambda c:c.effective_price,
             ReferenceKind.MOST_RAM:lambda c:_gb(c.ram),ReferenceKind.MOST_STORAGE:lambda c:_gb(c.storage)}[kind]
        usable=[c for c in listed if key(c) is not None]
        if not usable:return clarify('NO_BASELINE','Lần gợi ý trước chưa có đủ thông tin để so sánh. Bạn vui lòng nêu tên sản phẩm.')
        card=(min if kind==ReferenceKind.CHEAPEST else max)(usable,key=key)
        return _detail(kind,q,card,snapshot)
    if kind==ReferenceKind.ANOTHER:
        base=snapshot.search
        if base is None or scope_changed(categories,brands,base):
            return plan if plan.product_search else clarify('NO_BASELINE','Bạn muốn xem thêm loại sản phẩm nào?')
        data=base.model_dump()
        limit=5 if base.targets else base.top_k
        if base.targets and not (base.category or base.brands):
            return clarify('NO_BASELINE','Bạn muốn xem thêm loại sản phẩm nào?')
        data.update(targets=[],semantic_query=message,top_k=min(20,limit+len(shown_products)))
        event('conversation.reference_resolution',{'kind':kind.value,'status':'RESOLVED','excluded':len(shown_products),
               'captured_at':snapshot.captured_at})
        return QueryPlan(intent=Intent.PRODUCT_DISCOVERY,requested_fields=plan.requested_fields,
                         references={'exclude_products':','.join(sorted(map(str,shown_products))),'result_limit':str(limit)},
                         product_search=ProductSearchPlan.model_validate(data))
    if kind in PRICE_COMPARATIVES:
        cheaper=kind==ReferenceKind.CHEAPER;word='rẻ hơn' if cheaper else 'đắt hơn'
        if scope_changed(categories,brands,snapshot.search):
            # No baseline price exists for a new scope; never apply the old snapshot scope to it.
            return clarify('SCOPE_CHANGED',GENERAL_CLARIFICATION,'R3b')
        prices_shown=[c.effective_price for c in snapshot.cards if c.effective_price is not None]
        if not prices_shown:return clarify('NO_BASELINE',f'Chưa có mốc giá của lần gợi ý trước để so sánh {word}.','R1')
        data=(snapshot.search or plan.product_search or ProductSearchPlan()).model_dump()
        data.update(targets=[],semantic_query=message)
        if data.get('target_price') is not None:
            # "khoảng 25 triệu" then "rẻ hơn": the comparative replaces the approximate band.
            data.update(min_price=None,max_price=None,target_price=None)
        ram,_=capacity(q,'ram');storage_min,_=capacity(q,'storage')
        if ram is not None:data['min_ram_gb']=ram
        if storage_min is not None:data['min_storage_gb']=storage_min
        inherited_min,inherited_max=data['min_price'],data['max_price']
        if cheaper:
            # Only an explicitly stated minimum ("trên X") sets min_price; comparatives never do.
            if prices.min_price is not None:data['min_price']=prices.min_price
            baseline=min(prices_shown);ceiling=baseline-Decimal('1')
            data['max_price']=min([v for v in (ceiling,prices.max_price,data['max_price']) if v is not None])
            if ceiling < 0 or data['min_price'] is not None and data['min_price'] > data['max_price']:
                return clarify('NO_PRICE_RANGE','Không có khoảng giá rẻ hơn mốc đã gợi ý phù hợp với mức tối thiểu hiện tại.','R1')
            carry=(data['min_price'],min([v for v in (prices.max_price,inherited_max) if v is not None],default=None))
        else:
            # Mirror of cheaper: only an explicitly stated maximum ("dưới X") replaces max_price.
            if prices.max_price is not None:data['max_price']=prices.max_price
            baseline=max(prices_shown);floor=baseline+Decimal('1')
            data['min_price']=max([v for v in (floor,prices.min_price,data['min_price']) if v is not None])
            if data['max_price'] is not None and data['min_price'] > data['max_price']:
                return clarify('NO_PRICE_RANGE','Không có khoảng giá đắt hơn mốc đã gợi ý phù hợp với mức tối đa hiện tại.','R1')
            carry=(max([v for v in (prices.min_price,inherited_min) if v is not None],default=None),data['max_price'])
        event('conversation.reference_resolution',{'kind':kind.value,'status':'RESOLVED','case':'R1','baseline':'historical_display_price',
               'baseline_price':baseline,'min_price':data['min_price'],'max_price':data['max_price'],'captured_at':snapshot.captured_at})
        # The bound derived from displayed prices is not a user requirement; only stated bounds carry forward.
        return QueryPlan(intent=Intent.PRODUCT_DISCOVERY,requested_fields=[RequestedField.PRICE],
                         references={'baseline':'historical_display_price','carry_min_price':_bound(carry[0]),'carry_max_price':_bound(carry[1])},
                         product_search=ProductSearchPlan.model_validate(data))
    index=1 if kind==ReferenceKind.ORDINAL_SECOND else 0
    ordinal_cards=listed if kind in {ReferenceKind.ORDINAL_FIRST,ReferenceKind.ORDINAL_SECOND} else snapshot.cards
    if index >= len(ordinal_cards):return clarify('NO_REFERENCE','Lần gợi ý trước không có sản phẩm thứ hai. Bạn vui lòng nêu tên hoặc SKU cần hỏi.')
    storage=storage_reference(q)
    size=int(storage[1])*(1024 if storage[2]=='tb' else 1) if storage else None
    if kind==ReferenceKind.STORAGE and len(shown_products) > 1:
        # "có bản 1TB không" over several products narrows the same search instead of asking which one.
        refined=refine(message,q,plan,snapshot,storage=size)
        if refined is not None:
            return refined
    if kind in {ReferenceKind.LAST,ReferenceKind.STORAGE} and len(shown_products)!=1:
        return clarify('AMBIGUOUS','Có nhiều sản phẩm vừa được gợi ý. Bạn muốn hỏi máy nào? Hãy nêu tên hoặc SKU.')
    return _detail(kind,q,ordinal_cards[index],snapshot,size)
