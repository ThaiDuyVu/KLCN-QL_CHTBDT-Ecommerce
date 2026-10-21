"""Resolve only from owned displayed evidence, never from raw history facts."""
from decimal import Decimal
from app.features.query_understanding.normalization import folded, contains, capacity
from app.shared.contracts.enums import Intent, RequestedField
from app.shared.contracts.query import QueryPlan, ProductSearchPlan, ProductSelector
from app.shared.tracing import event
from app.features.query_understanding.constraints import (
    CATEGORY_ALIASES, GENERAL_CLARIFICATION, NO_ANTECEDENT_CLARIFICATION, PENDING_REFERENCE_CLARIFICATION,
    ReferenceKind, detect_reference, price_evidence, storage_reference,
)

# Application-controlled antecedent requests the resolver may replace; exact strings, never substrings.
ANTECEDENT_CLARIFICATIONS = frozenset((NO_ANTECEDENT_CLARIFICATION, PENDING_REFERENCE_CLARIFICATION, GENERAL_CLARIFICATION))


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


def resolve_reference(message, plan, snapshot):
    q=folded(message)
    if plan.intent not in {Intent.PRODUCT_DISCOVERY, Intent.PRODUCT_DETAIL, Intent.PRODUCT_COMPARE, Intent.UNKNOWN}:
        return plan
    if any(term in q for term in ('gio hang', 'don hang', 'thanh toan', 'phieu bao hanh', 'bao hanh cua toi')):
        return plan
    kind=detect_reference(q)
    if kind is None or (plan.product_search and plan.product_search.targets): return plan
    if plan.clarification and plan.clarification not in ANTECEDENT_CLARIFICATIONS:
        # Resolving an antecedent must not discard a price/capacity/domain error.
        return plan
    def clarify(reason,text,case=None):
        event('conversation.reference_resolution',{'kind':kind.value,'status':reason,'case':case})
        return QueryPlan(intent=Intent.UNKNOWN,clarification=text)
    prices=price_evidence(q)
    # The current utterance's price is read independently of the antecedent and is never dropped.
    # A bounded comparative ("rẻ hơn X") stays a clarification until DR-02/DR-12 define R2/R3.
    if prices.unresolved or kind==ReferenceKind.CHEAPER_BOUNDED:
        return clarify('UNSUPPORTED_PRICE',GENERAL_CLARIFICATION)
    categories,brands=stated_scope(q,plan)
    if kind!=ReferenceKind.CHEAPER and (categories or brands):
        # A newly stated category/brand is a new request, not an ordinal/last reference.
        event('conversation.reference_resolution',{'kind':kind.value,'status':'NOT_REFERENCE','case':'R3'})
        return plan
    if snapshot is None or not snapshot.cards:
        return clarify('NO_REFERENCE','Mình chưa có sản phẩm tham chiếu trong phiên này. Bạn vui lòng nêu tên sản phẩm hoặc SKU.','R4')
    if kind==ReferenceKind.CHEAPER:
        if scope_changed(categories,brands,snapshot.search):
            # No baseline price exists for a new scope; never apply the old snapshot scope to it.
            return clarify('SCOPE_CHANGED',GENERAL_CLARIFICATION,'R3b')
        prices_shown=[c.effective_price for c in snapshot.cards if c.effective_price is not None]
        if not prices_shown:return clarify('NO_BASELINE','Chưa có mốc giá của lần gợi ý trước để so sánh rẻ hơn.','R1')
        baseline=min(prices_shown);ceiling=baseline-Decimal('1')
        data=(snapshot.search or plan.product_search or ProductSearchPlan()).model_dump()
        data.update(targets=[],semantic_query=message)
        ram,_=capacity(q,'ram');storage_min,_=capacity(q,'storage')
        if ram is not None:data['min_ram_gb']=ram
        if storage_min is not None:data['min_storage_gb']=storage_min
        # Only an explicitly stated minimum ("trên X") sets min_price; comparatives never do.
        if prices.min_price is not None:data['min_price']=prices.min_price
        data['max_price']=min([v for v in (ceiling,prices.max_price,data['max_price']) if v is not None])
        if ceiling < 0 or data['min_price'] is not None and data['min_price'] > data['max_price']:
            return clarify('NO_PRICE_RANGE','Không có khoảng giá rẻ hơn mốc đã gợi ý phù hợp với mức tối thiểu hiện tại.','R1')
        event('conversation.reference_resolution',{'kind':kind.value,'status':'RESOLVED','case':'R1','baseline':'historical_display_price',
               'baseline_price':baseline,'max_price':data['max_price'],'captured_at':snapshot.captured_at})
        return QueryPlan(intent=Intent.PRODUCT_DISCOVERY,requested_fields=[RequestedField.PRICE],
                         references={'baseline':'historical_display_price'},product_search=ProductSearchPlan.model_validate(data))
    index=1 if kind==ReferenceKind.ORDINAL_SECOND else 0
    if index >= len(snapshot.cards):return clarify('NO_REFERENCE','Lần gợi ý trước không có sản phẩm thứ hai. Bạn vui lòng nêu tên hoặc SKU cần hỏi.')
    if kind in {ReferenceKind.LAST,ReferenceKind.STORAGE} and len(snapshot.cards)!=1:
        return clarify('AMBIGUOUS','Có nhiều sản phẩm vừa được gợi ý. Bạn muốn hỏi máy nào? Hãy nêu tên hoặc SKU.')
    card=snapshot.cards[index]
    storage=storage_reference(q)
    if storage:
        selector=ProductSelector(product_id=card.product_id,storage_gb=int(storage[1])*(1024 if storage[2]=='tb' else 1))
        search=ProductSearchPlan(targets=[selector], min_ram_gb=snapshot.search.min_ram_gb if snapshot.search else None)
    else:
        selector=ProductSelector(variant_id=card.variant_id) if card.variant_id else ProductSelector(product_id=card.product_id)
        search=ProductSearchPlan(targets=[selector])
    fields=[RequestedField.STOCK] if 'con hang' in q else [RequestedField.PRICE] if 'gia' in q else [RequestedField.SPECIFICATIONS]
    event('conversation.reference_resolution',{'kind':kind.value,'status':'RESOLVED','product_id':card.product_id,
            'variant_id':card.variant_id,'captured_at':snapshot.captured_at,'refresh':'live_backend'})
    return QueryPlan(intent=Intent.PRODUCT_DETAIL,requested_fields=fields,product_search=search,
                     references={'source':'owned_session_snapshot'})
