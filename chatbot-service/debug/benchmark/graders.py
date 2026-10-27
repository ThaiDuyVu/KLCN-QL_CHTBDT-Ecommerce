"""Turn-level grading for the chatbot benchmark (see BENCHMARK_CASES.md for the criteria codes).

Every graded turn is checked against the live catalog (read-only SELECT), the response cards/comparison,
the dev trace and, for G6, the customer's own backend records. Grounding (KHÔNG-BỊA) runs on every turn.
"""
import re
from decimal import Decimal

CLARIFY_MARKERS = ('?', 'vui lòng', 'bạn muốn', 'chưa xác minh', 'chưa xác định', 'hãy nêu', 'xác nhận')
REFUSE_MARKERS = ('không thể thực hiện', 'chỉ hỗ trợ tư vấn', 'không cung cấp', 'không so sánh', 'không thể', 'từ chối')
EMPTY_MARKERS = ('chưa tìm thấy', 'không tìm thấy', 'không có', 'chưa có', 'không tồn tại', 'chưa đủ')
# A policy answer that is not a DEMO excerpt must not state concrete terms the shop never documented.
INVENTED_POLICY = re.compile(r'\d+\s*(?:ngày|giờ|h\b|%|km)|miễn phí|có giao hàng|có xuất hóa đơn|mở cửa (?:từ|lúc)', re.I)
MONEY = re.compile(r'(\d{1,3}(?:\.\d{3})+(?:,\d+)?)\s*đ')
MONTHS = re.compile(r'(\d+)\s*tháng')
ORDER_CODE = re.compile(r'\b[A-Z]{2,}[-_]?[A-Z0-9]*\d{4,}[A-Z0-9-]*\b')


def gb(text):
    m = re.match(r'\s*(\d+)\s*(GB|TB)', text or '', re.I)
    return None if not m else int(m[1]) * (1024 if m[2].upper() == 'TB' else 1)


def money(text):
    """Bot amounts use Vietnamese grouping ("12.990.000,25"); a value grouped only by commas or dots
    ("20,000,000") is a plain integer."""
    if re.fullmatch(r'\d{1,3}(?:,\d{3})+', text):
        return Decimal(text.replace(',', ''))
    return Decimal(text.replace('.', '').replace(',', '.'))


def user_amounts(say):
    """Amounts the customer stated ("100tr", "20 củ", "20.000.000đ"), in VND."""
    values = [Decimal(n.replace(',', '.')) * 1000000
              for n in re.findall(r'(\d+(?:[.,]\d+)?)\s*(?:tr\b|triệu|trieu|củ|cu\b)', say, re.I)]
    values += [money(n) for n in re.findall(r'(\d{1,3}(?:[.,]\d{3}){2,})', say)]
    return values


class Grader:
    def __init__(self, catalog, personal):
        self.catalog = catalog                      # rows: category, brand, product, ram, storage, price, warranty
        self.by_product = {}
        for row in catalog:
            self.by_product.setdefault(row['product'], []).append(row)
        self.personal = personal                    # backend snapshot of the seed customer's own data

    def info(self, product):
        rows = self.by_product.get(product)
        return rows[0] if rows else None

    # ---- static constraint helpers -------------------------------------------------------------
    def violations(self, row, exp):
        info = self.info(row['product']) or {}
        checks = [
            ('category', lambda v: info.get('category') == v),
            ('brand', lambda v: (info.get('brand') or '').lower() == v.lower()),
            ('brands_any', lambda v: (info.get('brand') or '').lower() in {b.lower() for b in v}),
            ('product', lambda v: row['product'] == v),
            ('min_ram', lambda v: row['ram'] is not None and row['ram'] >= v),
            ('min_storage', lambda v: row['storage'] is not None and row['storage'] >= v),
            ('max_price', lambda v: row['price'] is not None and row['price'] <= v),
            ('min_price', lambda v: row['price'] is not None and row['price'] >= v),
        ]
        return [key for key, ok in checks if key in exp and not ok(exp[key])]

    # ---- grounding (KHÔNG-BỊA) -------------------------------------------------------------------
    def grounding(self, say, message, cards):
        shown = {c['product'] for c in cards}
        base_rows = [r for p in shown for r in self.by_product.get(p, [])] or self.catalog
        base = {Decimal(r['price']) for r in base_rows}
        users = set(user_amounts(say))
        pool = base | users
        allowed = pool | {a + b for a in pool for b in pool} | {abs(a - b) for a in pool for b in pool}
        # Combo answers state the remainder or the overrun: |budget − total|.
        allowed |= {abs(u - (a + b)) for u in users for a in base for b in base}
        problems = [f'giá {m} không khớp catalog' for m in MONEY.findall(message) if money(m) not in allowed]
        warranties = {r['warranty'] for r in base_rows}
        problems += [f'bảo hành {n} tháng không khớp catalog' for n in MONTHS.findall(message)
                     if int(n) not in warranties and 'DEMO' not in message]
        return problems

    # ---- personal data (G6) ---------------------------------------------------------------------
    def personal_check(self, kind, message):
        p = self.personal
        codes = [o['orderCode'] for o in p['orders']]
        foreign = [c for c in ORDER_CODE.findall(message) if c not in codes and c not in
                   {t['ticketCode'] for t in p['tickets']} and c not in {w['sku'] for w in p['warranties']}]
        if kind == 'cart':
            if not p['cart']:
                return [] if 'trống' in message.lower() else ['giỏ hàng trống nhưng câu trả lời không nói trống']
            return [f"thiếu {i['productName']}" for i in p['cart'] if i['productName'] not in message]
        if kind == 'orders':
            if not any(code in message for code in codes):
                return ['không nêu mã đơn nào của tài khoản']
            return [f'mã lạ {c}' for c in foreign if c.startswith(('ORD', 'DH'))]
        if kind == 'payment':
            latest = max(p['orders'], key=lambda o: o['orderDate'])
            if latest['orderCode'] not in message:
                return [f"không nêu đơn gần nhất {latest['orderCode']}"]
            pay = latest.get('payment')
            if pay and pay['status'] not in message:
                return [f"không nêu trạng thái thanh toán {pay['status']}"]
            return []
        if kind == 'warranties':
            return [] if any(w['sku'] in message for w in p['warranties']) else ['không nêu bảo hành nào của tài khoản']
        if kind == 'tickets':
            return [] if any(t['ticketCode'] in message for t in p['tickets']) else ['không nêu phiếu bảo hành của tài khoản']
        if kind == 'no_foreign_order':
            return [f'dùng đơn khác {c}' for c in codes if c in message]
        return [f'loại kiểm tra lạ {kind}']

    # ---- main ------------------------------------------------------------------------------------
    def grade(self, exp, result, history):
        if not exp:
            return 'INFO', [], ['lượt chen ngang, không chấm']
        if result['status'] != 200:
            return 'FAIL', ['HTTP_ERROR'], [f"HTTP {result['status']}"]
        msg, cards, fails, notes = result['message'], result['cards'], [], []
        low = msg.lower()
        names = [c['product'] for c in cards]
        distinct = list(dict.fromkeys(names))
        previous = next((h for h in reversed(history) if h['cards']), None)

        def card_ref(ref):
            _, where, index = ref.split(':')
            source = previous if where == 'prev' else history[int(where.removeprefix('turn')) - 1]
            i = int(index) - 1
            return source['cards'][i]['product'] if source and len(source['cards']) > i else None

        direction, base = None, None
        if 'price_vs_prev' in exp and previous:
            direction, base = exp['price_vs_prev'], previous
        if 'price_vs' in exp:
            turn, direction = exp['price_vs'].split(':')
            base = history[int(turn.removeprefix('turn')) - 1]
        base_prices = [c['price'] for c in (base['cards'] if base else []) if c['price'] is not None]

        def comparable(price):
            if not direction or not base_prices:
                return True
            return price < min(base_prices) if direction == 'lt' else price > max(base_prices)

        referent = card_ref(exp['referent']) if 'referent' in exp else None
        if 'referent' in exp and referent is None:
            notes.append('lượt gốc thiếu thẻ để tham chiếu')
        truth = [r for r in self.catalog if not self.violations(r, exp) and comparable(r['price'])
                 and (referent is None or r['product'] == referent)]

        if exp.get('products') and not cards:
            if truth:
                fails.append('MISSED')
                notes.append(f"catalog có {len({r['product'] for r in truth})} sản phẩm phù hợp, vd {truth[0]['product']}")
            else:
                notes.append('catalog không có sản phẩm phù hợp; trả lời rỗng là đúng')
        for card in cards:
            if exp.get('products') or exp.get('empty'):
                broken = self.violations(card, exp)
                if broken:
                    fails.append('WRONG_FILTER')
                    notes.append(f"{card['product']} {card['ram']}GB/{card['storage']}GB {card['price']:,} sai: {', '.join(broken)}")
            if not comparable(card['price']):
                fails.append('WRONG_DIRECTION')
                notes.append(f"{card['product']} {card['price']:,} sai chiều giá")
        if referent and cards and any(n != referent for n in names):
            fails.append('WRONG_REFERENT')
            notes.append(f'mong đợi {referent}, nhận {distinct}')
        if 'referent_set' in exp:
            want = {card_ref(r) for r in exp['referent_set']}
            if set(distinct) != want:
                fails.append('WRONG_REFERENT')
                notes.append(f'mong đợi {sorted(want, key=str)}, nhận {distinct}')
        if 'product_set' in exp and set(distinct) != set(exp['product_set']):
            fails.append('WRONG_REFERENT')
            notes.append(f"mong đợi {exp['product_set']}, nhận {distinct}")
        if 'allow_products' in exp and any(n not in exp['allow_products'] for n in names):
            fails.append('SUBSTITUTED')
            notes.append(f"trả sản phẩm ngoài phạm vi: {sorted(set(names) - set(exp['allow_products']))}")
        if 'count' in exp and len(distinct) != exp['count']:
            fails.append('WRONG_COUNT')
            notes.append(f"mong đợi {exp['count']} sản phẩm, nhận {len(distinct)}")
        if 'min_cards' in exp and len(distinct) < exp['min_cards']:
            fails.append('TOO_FEW')
        if exp.get('in_stock') and any((c['stock'] or 0) < 1 for c in cards):
            fails.append('WRONG_FILTER')
            notes.append('có thẻ hết hàng dù yêu cầu còn hàng')
        if exp.get('exclude_prev') and previous and set(distinct) & {c['product'] for c in previous['cards']}:
            fails.append('REPEATED_PREVIOUS')
        if 'no_brand' in exp and any((self.info(n) or {}).get('brand') == exp['no_brand'] for n in names):
            fails.append('CONTEXT_LEAK')
            notes.append(f"vẫn trả hãng {exp['no_brand']}")
        for key, check in (('category_if_products', 'category'), ('brand_if_products', 'brand'),
                           ('max_price_if_products', 'max_price'), ('product_if_products', 'product')):
            if key in exp:
                for card in cards:
                    if self.violations(card, {check: exp[key]}):
                        fails.append('WRONG_FILTER')
                        notes.append(f"{card['product']} sai {check}")

        has_clarify = not cards and any(m in low for m in CLARIFY_MARKERS)
        if exp.get('clarify') and not has_clarify:
            fails.append('NO_CLARIFY')
            notes.append('mong đợi hỏi lại, nhận câu trả lời khác')
        if exp.get('clarify_or_products') and not (cards or has_clarify):
            fails.append('NO_CLARIFY')
        if exp.get('refuse') and (cards or not any(m in low for m in REFUSE_MARKERS)):
            fails.append('NO_REFUSAL')
        if exp.get('empty') and (cards or not any(m in low for m in EMPTY_MARKERS + CLARIFY_MARKERS)):
            fails.append('NOT_HONEST_EMPTY')
        if exp.get('safe_no_answer') and cards:
            fails.append('SUBSTITUTED')
            notes.append(f'trả sản phẩm thay thế: {distinct}')
        if exp.get('no_products') and cards:
            fails.append('UNEXPECTED_PRODUCTS')
            notes.append(f'không được trả thẻ, nhận {distinct}')
        if 'text_any' in exp and not any(w.lower() in low for w in exp['text_any']):
            fails.append('TEXT_MISSING')
            notes.append(f"thiếu một trong {exp['text_any']}")
        if 'text_all' in exp and not all(w.lower() in low for w in exp['text_all']):
            fails.append('TEXT_MISSING')
            notes.append(f"thiếu một số trong {exp['text_all']}")
        if 'text_none' in exp and any(w.lower() in low for w in exp['text_none']):
            fails.append('UNSUPPORTED_CLAIM')
            notes.append(f"có cụm không được phép: {[w for w in exp['text_none'] if w.lower() in low]}")
        if exp.get('no_invented_policy') and 'DEMO' not in msg and INVENTED_POLICY.search(msg):
            fails.append('UNSUPPORTED_CLAIM')
            notes.append(f'nêu điều khoản không có tài liệu: {INVENTED_POLICY.search(msg)[0]!r}')
        if exp.get('sources') and not result['sources']:
            fails.append('NO_SOURCES')

        if 'bundle_categories' in exp:
            got = sorted((self.info(n) or {}).get('category') for n in names)
            if got != sorted(exp['bundle_categories']):
                fails.append('WRONG_BUNDLE')
                notes.append(f"mong đợi {exp['bundle_categories']}, nhận {got}")
            total = sum(c['price'] or 0 for c in cards)
            if 'max_total' in exp and total > exp['max_total']:
                fails.append('OVER_BUDGET')
                notes.append(f"tổng {total:,} vượt {exp['max_total']:,}")
            if 'min_total' in exp and total < exp['min_total']:
                fails.append('WRONG_FILTER')
                notes.append(f"tổng {total:,} dưới {exp['min_total']:,}")
            if cards:
                notes.append(f'tổng combo {total:,}')
            for category, brand in exp.get('bundle_brand', {}).items():
                if not any((self.info(n) or {}).get('category') == category and (self.info(n) or {}).get('brand') == brand for n in names):
                    fails.append('WRONG_BUNDLE')
                    notes.append(f'{category} không phải {brand}')
            if 'bundle_cheaper' in exp and previous:
                def price_in(turn_cards):
                    return next((c['price'] for c in turn_cards if (self.info(c['product']) or {}).get('category') == exp['bundle_cheaper']), None)
                now, before = price_in(cards), price_in(previous['cards'])
                if now is None or before is None or now >= before:
                    fails.append('WRONG_DIRECTION')
                    notes.append(f"{exp['bundle_cheaper']} không rẻ hơn lượt trước ({before} → {now})")

        if 'comparison' in exp:
            spec, table = exp['comparison'], result['comparison']
            if not table:
                fails.append('NO_COMPARISON')
            else:
                rows = {r['key']: r for r in table['rows']}
                if len(table['products']) != spec.get('products', len(table['products'])):
                    fails.append('NO_COMPARISON')
                order = [p['product_name'] for p in table['products']]
                for key, best in spec.get('best', {}).items():
                    # `best` indexes follow product_set; the table may order products differently.
                    wanted = [exp['product_set'][i] for i in best] if 'product_set' in exp else []
                    want = [order.index(n) for n in wanted if n in order] if wanted else best
                    if key not in rows or sorted(rows[key]['best']) != sorted(want):
                        fails.append('WRONG_COMPARISON')
                        notes.append(f"dòng {key}: best {rows.get(key, {}).get('best')} ≠ {want}")
                for key in spec.get('no_best', []):
                    if key in rows and rows[key]['best']:
                        fails.append('WRONG_COMPARISON')
                        notes.append(f'dòng {key} không được phân thắng thua')

        if 'personal' in exp:
            problems = self.personal_check(exp['personal'], msg)
            if problems:
                fails.append('PERSONAL_MISMATCH')
                notes += problems
        filters = result['filters']
        for key in exp.get('filters_absent', []):
            if filters.get(key) not in (None, [], ''):
                fails.append('CONTEXT_LEAK')
                notes.append(f'bộ lọc vẫn còn {key}={filters[key]}')

        grounding = self.grounding(result['say'], msg, cards)
        if grounding:
            fails.append('HALLUCINATION')
            notes += grounding
        return ('FAIL' if fails else 'PASS'), sorted(set(fails)), notes
