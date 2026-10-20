"""Resolve only from owned displayed evidence, never from raw history facts."""
import re
from decimal import Decimal
from app.features.query_understanding.normalization import folded, price_slots, capacity
from app.shared.contracts.enums import Intent, RequestedField
from app.shared.contracts.query import QueryPlan, ProductSearchPlan, ProductSelector
from app.shared.tracing import event


def resolve_reference(message, plan, snapshot):
    q=folded(message)
    if plan.intent not in {Intent.PRODUCT_DISCOVERY, Intent.PRODUCT_DETAIL, Intent.PRODUCT_COMPARE, Intent.UNKNOWN}:
        return plan
    if any(term in q for term in ('gio hang', 'don hang', 'thanh toan', 'phieu bao hanh', 'bao hanh cua toi')):
        return plan
    storage=re.search(r'\bban\s+(\d+)\s*(gb|tb)\b',q)
    kind=('CHEAPER' if 're hon' in q or 'cheaper' in q else 'SECOND' if 'thu hai' in q else
          'FIRST' if 'dau tien' in q else 'LAST' if 'vua goi y' in q else 'STORAGE' if storage else None)
    if kind is None or (plan.product_search and plan.product_search.targets): return plan
    if plan.clarification and not any(term in plan.clarification for term in
            ('tham chiếu', 'sản phẩm vừa nhắc', 'so sánh với sản phẩm', 'loại máy nào')):
        # Resolving an antecedent must not discard a price/capacity/domain error.
        return plan
    def clarify(reason,text):
        event('conversation.reference_resolution',{'kind':kind,'status':reason})
        return QueryPlan(intent=Intent.UNKNOWN,clarification=text)
    if snapshot is None or not snapshot.cards:
        return clarify('NO_REFERENCE','Mình chưa có sản phẩm tham chiếu trong phiên này. Bạn vui lòng nêu tên sản phẩm hoặc SKU.')
    if kind=='CHEAPER':
        prices=[c.effective_price for c in snapshot.cards if c.effective_price is not None]
        if not prices:return clarify('NO_BASELINE','Chưa có mốc giá của lần gợi ý trước để so sánh rẻ hơn.')
        baseline=min(prices);ceiling=baseline-Decimal('1')
        data=(snapshot.search or plan.product_search or ProductSearchPlan()).model_dump()
        data.update(targets=[],semantic_query=message)
        low,high=price_slots(q)
        ram,_=capacity(q,'ram');storage_min,_=capacity(q,'storage')
        if ram is not None:data['min_ram_gb']=ram
        if storage_min is not None:data['min_storage_gb']=storage_min
        if low is not None:data['min_price']=low
        data['max_price']=min([v for v in (ceiling,high,data['max_price']) if v is not None])
        if ceiling < 0 or data['min_price'] is not None and data['min_price'] > data['max_price']:
            return clarify('NO_PRICE_RANGE','Không có khoảng giá rẻ hơn mốc đã gợi ý phù hợp với mức tối thiểu hiện tại.')
        event('conversation.reference_resolution',{'kind':kind,'status':'RESOLVED','baseline':'historical_display_price',
               'baseline_price':baseline,'max_price':data['max_price'],'captured_at':snapshot.captured_at})
        return QueryPlan(intent=Intent.PRODUCT_DISCOVERY,requested_fields=[RequestedField.PRICE],
                         references={'baseline':'historical_display_price'},product_search=ProductSearchPlan.model_validate(data))
    index=1 if kind=='SECOND' else 0
    if index >= len(snapshot.cards):return clarify('NO_REFERENCE','Lần gợi ý trước không có sản phẩm thứ hai. Bạn vui lòng nêu tên hoặc SKU cần hỏi.')
    if kind in {'LAST','STORAGE'} and len(snapshot.cards)!=1:
        return clarify('AMBIGUOUS','Có nhiều sản phẩm vừa được gợi ý. Bạn muốn hỏi máy nào? Hãy nêu tên hoặc SKU.')
    card=snapshot.cards[index]
    if storage:
        selector=ProductSelector(product_id=card.product_id,storage_gb=int(storage[1])*(1024 if storage[2]=='tb' else 1))
        search=ProductSearchPlan(targets=[selector], min_ram_gb=snapshot.search.min_ram_gb if snapshot.search else None)
    else:
        selector=ProductSelector(variant_id=card.variant_id) if card.variant_id else ProductSelector(product_id=card.product_id)
        search=ProductSearchPlan(targets=[selector])
    fields=[RequestedField.STOCK] if 'con hang' in q else [RequestedField.PRICE] if 'gia' in q else [RequestedField.SPECIFICATIONS]
    event('conversation.reference_resolution',{'kind':kind,'status':'RESOLVED','product_id':card.product_id,
            'variant_id':card.variant_id,'captured_at':snapshot.captured_at,'refresh':'live_backend'})
    return QueryPlan(intent=Intent.PRODUCT_DETAIL,requested_fields=fields,product_search=search,
                     references={'source':'owned_session_snapshot'})
