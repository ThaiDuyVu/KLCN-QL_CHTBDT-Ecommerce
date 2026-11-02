# Kế hoạch sửa chữa chatbot sau Static Production Audit

> [C2C] STATE: PLANNED · TASK_ID: c2c_chatbot_remediation_planning · ITERATION: 1
> Tài liệu lập kế hoạch tĩnh. Không có source code, test, schema hay migration nào bị thay đổi hoặc thực thi khi lập tài liệu này.
> Đường dẫn file tính từ gốc repo `QL_CHTBDT_Ecommerce/`. Số dòng ứng với HEAD `f99f713`.
>
> **Iteration 1 (sau PLAN_REVIEW):**
> - Sửa pseudocode A1 để giá 0 được giữ nguyên; thêm control N06.
> - A3 bổ sung quan hệ giữa giá tường minh và tham chiếu.
> - B4 bổ sung chính sách theo loại yêu cầu.
> - C1 bổ sung các mức bảo vệ và deployment gate.
> - Phân loại lại bộ NX (44 case).
> - Tách UI_LAB_READY / UI_STOREFRONT_READY.
> - DR-01..DR-13b ở trạng thái PENDING_APPROVAL.
> - Thêm acceptance gate riêng cho từng group (mục 10.3).

---

# 01. Thông tin baseline

| Mục | Giá trị |
|---|---|
| Workspace | `QL_CHTBDT_Ecommerce` |
| Branch | `dev/thai-duy-vu` |
| HEAD | `f99f713` (Merge PR #48, gồm `74f4e19` fix(chatbot): isolate incomplete turns and scope read-only safety) |
| Git status khi bắt đầu lập kế hoạch | sạch (không có thay đổi chưa commit) |
| Nguồn audit | `../CHATBOT_STATIC_PRODUCTION_AUDIT_2026-10-21.md` (nằm ở thư mục `KLTN/`, ngoài repo). Đã đọc toàn văn; đó là báo cáo do cùng agent viết trong phiên trước. |
| Commit sau audit | Không có. HEAD lúc audit và lúc lập kế hoạch giống nhau, nên không finding nào có khả năng ALREADY_RESOLVED nhờ commit mới. Từng finding vẫn được đối chiếu lại với code. |
| Bản vá đã nghiệm thu được đọc | `chatbot-service/debug/post-upgrade-fixes/01_QUERYPLAN_INTEGRITY_FIX.md`, `02_ORDER_PAYMENT_READ_FIX.md`, `04_CONVERSATION_SAFETY_FIX.md`, `fix01-follow-up/REVIEW_FOLLOW_UP.md` |
| Bộ 120 case | `chatbot-service/debug/post-upgrade-validation/cases.json` (T=60, N=48, B=12, đã đếm), `results.json`, `04_FINAL_ACCEPTANCE_REPORT.md` |
| Codebase Memory | Không khả dụng trong môi trường này. Toàn bộ nhận định dựa trên đọc trực tiếp source. |

**Phạm vi đã đọc:**
- `chatbot-service/app/**` (toàn bộ).
- Các test liên quan: `test_query_integrity.py`, `test_persistent_conversation.py`, `test_ai_chat_flow.py`, `test_pagination_boundaries.py`, `test_query_product_v2.py`.
- `Fontend/src/App.jsx`, `components/layout/AppLayout.jsx`, `features/chatbot/*`, `features/cart/components/AddToCart.jsx`, `vite.config.js`.
- Backend: `SecurityConfig`, `OrderController`, `OrderServiceImpl`, `ProductAdvancedSearchService`, `ProductDetailServiceImpl`, `PromotionServiceImpl`, `ProductServiceImpl`, migration V1/V7.

**Giới hạn của static planning:**
- Không chạy test, không có số đo hiệu năng.
- Kết quả 120 case sau sửa chưa tồn tại. Kết quả có trong repo (99 PASS / 8 WARN / 9 FAIL / 4 DEFERRED) là **baseline chẩn đoán trước Fix01/02/04**.
- Một số chi tiết backend cần được xác minh ở **bước 0 của Phase 1** (mục 09), vì kế hoạch phụ thuộc vào chúng:
  - V-1: `ProductServiceImpl.getProducts` có lọc theo role không.
  - V-2: dạng body lỗi 409 của `PromotionException`.
  - V-3: ngữ nghĩa 404/204 của `/api/cart`.
  - V-4: hook `useWarehouse`.

---

# 02. Executive Summary

**Đánh giá tổng thể.** Kiến trúc hiện tại (deterministic trước, LLM chỉ dùng để phân loại, dữ kiện lấy từ backend) **được giữ nguyên**. Lỗi tập trung ở bốn lớp hẹp:
1. Bộ đọc giá và nhận diện tham chiếu bằng regex/chuỗi con.
2. Các ngưỡng coverage tĩnh khiến hệ thống trả 503 thay vì kết quả đúng.
3. Bước tùy chọn (ranking) trở thành điểm hỏng duy nhất.
4. Cấu hình/vận hành: mock mặc định, I/O DB đồng bộ trên event loop.

Không cần thiết kế lại hệ thống, đổi schema, hay thêm hạ tầng (Redis/queue/microservice).

**Thực sự cần sửa trong đợt này** (15 finding):
- CB-01 → CB-07 (P1).
- CB-09, CB-10, CB-11 (P2).
- CB-18, CB-19, CB-21, CB-22, CB-23 (P3, thay đổi nhỏ, rủi ro thấp).

**Hoãn:** CB-13, CB-14, CB-15 (phần còn lại sau khi CB-04 giải quyết phần lớn), CB-16, CB-17, CB-20. **Giới hạn được chấp nhận:** CB-24, và phần "thẻ đại diện một variant" của CB-12 (Fix01 đã chấp nhận).

**Cần người dùng quyết định** (DR-01..DR-13b, tất cả **PENDING_APPROVAL**; xem mục 11):
- CB-08: phạm vi UI (DR-03).
- Ngữ nghĩa "tầm/khoảng/ngân sách" (DR-01).
- "thấp hơn X" là cận bao gồm hay loại trừ (DR-02).
- Ceiling khi có giá tường minh (DR-12).
- Ngưỡng catalog và TTL cache (DR-04).
- Endpoint `/api/v1/ready` (DR-05a).
- Số đơn tối đa (DR-06).
- Phạm vi kho (DR-07).
- Chính sách promotion (DR-13a) và văn bản mới hiển thị cho khách (DR-13b).

Khi một DR chưa được duyệt, Phase 1 chỉ dùng hành vi fail-safe, không áp giá trị đề xuất.

**Thứ tự khuyến nghị:** Bước 0 (xác minh) → Group A (Query correctness) → Group B (Product reliability) → Group C (Runtime/config) → Group E (P2/P3 đã chọn) → Group D (UI, chỉ sau khi chốt phạm vi) → Phase 2.

Group A làm trước vì CB-03 cần hàm phát hiện tham chiếu mới. Hàm này loại bỏ nhánh "rẻ hơn" trùng lặp ở `chat/service.py:190-202`, và đó cũng là vị trí lời gọi DB đồng bộ ở dòng 193 thuộc CB-07.

---

# 03. Finding Verification Matrix

Ký hiệu trạng thái: VFR = VERIFIED_FIX_REQUIRED, VI = VERIFIED_IMPROVEMENT, AL = ACCEPTED_LIMITATION, DEF = DEFERRED, DR = DECISION_REQUIRED.

| ID | Orig. | Verified Status | Current Evidence (HEAD f99f713) | Recommended Action | Target | Dependency |
|---|---|---|---|---|---|---|
| CB-01 | P1 | **VFR** (P1 giữ nguyên) | `normalization.py:46` grammar cận dưới chứa `hon` trần; `references.py:38-39` gán `min_price=low`; `normalization.py:47` cận trên thiếu `re/thap/nho hon` | Parser giá có quan hệ (Group A1) | Phase 1 / A | DR-02 (bao gồm hay loại trừ) |
| CB-02 | P1 | **VFR** (P1) | `price_slots` `normalization.py:29-48` không có coverage guard; đối chiếu `constraints.py:81-83, 94-99` (dung lượng có guard) | Price coverage validation (A2) | Phase 1 / A | A1; DR-01 |
| CB-03 | P1 | **VFR** (P1) | `references.py:17-19` dò chuỗi con; `:20-21` allowlist chuỗi clarification; `:33` ưu tiên `snapshot.search`; `normalization.py:167-173`; logic trùng `chat/service.py:190-202` | `detect_reference` dùng chung + thứ tự ưu tiên + hằng clarification (A3) | Phase 1 / A | A1 |
| CB-04 | P1 | **VFR** (P1) | `backend.py:68-85` (catalog 5×100, brand >100 → `ValueError`); gọi mỗi lượt `normalization.py:74-78`; **bổ sung khi xác minh:** structured search cũng có trần 500 dòng (product,variant) `real_service.py:142-149`, nên danh mục lớn cũng trả 503 | Catalog/brand paginate đủ + kiểm completeness + cache TTL; search quá rộng → clarification (B1, B2) | Phase 1 / B | V-1; DR-04 |
| CB-05 | P1 | **VFR** (P1) | `config.py:10,24,28,34` mặc định dev/mock/memory; `dependencies.py:31-41`; `main.py:29,33-38` health luôn ok | Validator production + readiness (C1) | Phase 1 / C | DR-05 |
| CB-06 | P1 | **VFR** (P1) | `real_service.py:153-156` không bắt lỗi; `semantic.py:57-60` thiếu `connect_timeout`; psycopg.Error không nằm trong `chat/service.py:220` nên thành 500 | Ranking degrade an toàn (B3) | Phase 1 / B | — |
| CB-07 | P1 | **VFR (phần tối thiểu)**; pooling → DEF | `chat/service.py:116-136, 193` gọi repository đồng bộ trong `async def`; `postgres_repository.py:26-32` mở connection mỗi lần | Bọc các lời gọi bằng `blocking()` (C2); pool hoãn tới khi có load test | Phase 1 / C | A3 (bỏ dòng 193) |
| CB-08 | P1 | **DR** | `App.jsx:42-52` chỉ có lab; `AppLayout.jsx` không có widget; `vite.config.js:12-20` proxy dev-only | Option A hoặc B (Group D) | Phase 1 / D sau khi quyết định | DR-03 |
| CB-09 | P2 | **VI** (giữ P2, đưa vào đợt này) | `PromotionServiceImpl.java:104-111` 409; SQL `ProductAdvancedSearchService.java:49-56` lấy LIMIT 1; `real_service.py:161-165, 205-210` chỉ bỏ qua 404 | Bỏ qua đúng một dạng 409 overlap promotion trong discovery, có kiểm soát (B4) | Phase 1 / B | V-2 |
| CB-10 | P2 | **VFR** (P2) | `response_generation/ollama_service.py:56-60, 92-97`: 0 đơn trả `INSUFFICIENT`; bảo hành rỗng có câu riêng (`:74-75`) nên không nhất quán | Nhánh deterministic cho "rỗng hợp lệ" (E1) | Phase 1 / E | V-3 |
| CB-11 | P2 | **VI** (P2) | `customer_context/service.py:61-66` + `response_generation/ollama_service.py:60-67` liệt kê toàn bộ (tối đa 500) | Giới hạn N đơn mới nhất, kèm "N/total" (E2) | Phase 1 / E | DR-06 |
| CB-12 | P2 | **DR** (kho) + **AL** (thẻ một variant: Fix01 §11 "ProductCard vẫn một representative eligible variant") | `dependencies.py:36` kho toàn cục; storefront có `selectedWarehouseId` (`AddToCart.jsx:9`) mà chatbot không nhận | Không sửa trong đợt này; ghi rõ là giới hạn | DEF / Decision | DR-07 |
| CB-13 | P2 | **DR → DEF** | `postgres_repository.py:22, 164-170`; bảng `message_feedback` thang 1–5 không khớp -1/0/1 | Hoãn tới khi quyết định ánh xạ/schema | DEF | DR-08 |
| CB-14 | P2 | **DEF** | `chat/models.py:4-7` không có idempotency key; Fix04 đã ghi nhận là rủi ro còn lại | Hoãn: cần thay đổi public API, có thể cả schema | DEF | DR-08 |
| CB-15 | P2 | **VI**: phần catalog được CB-04 (cache) giải quyết; phần N+1 detail → **DEF** | `normalization.py:74-82`, `real_service.py:100-103, 159-197, 204-206` | B1 giảm lời gọi catalog; detail N+1 hoãn tới khi có số đo | B (một phần) / DEF | B1 |
| CB-16 | P2 | **VI → DEF** | `real_service.py:19,137,201,247` ContextVar; eligibility lặp ở `real_service.py:41-66` và SQL | Hoãn; Phase 2 thêm test bảo vệ | DEF | — |
| CB-17 | P2 | **VI → DEF** | `postgres_repository.py:148-159` quét tối đa 100 envelope | Hoãn tới khi có số đo | DEF | C2 |
| CB-18 | P3 | **VFR** (P3) | `policy.py:48` regex `re.I` nhưng `customer_context/service.py:62` so `==`; mã thật dạng `ORD-<UUID HOA>` (`OrderServiceImpl.java:110`) | So khớp không phân biệt hoa thường, vẫn bắt buộc đúng một đơn (E3) | Phase 1 / E | — |
| CB-19 | P3 | **VI** (P3) | `router.py:101-134` mọi customer gọi được, kèm outbound Ollama | Chỉ bật ở env dev-like (E4) | Phase 1 / E | C1 (dùng chung helper env) |
| CB-20 | P3 | **DEF** | `dependencies.py:47-55` không truyền `after_response` | Hoãn cùng Knowledge RAG chính thức | DEF | — |
| CB-21 | P3 | **VI** (giữ P3, không nâng) | `query_understanding/ollama_service.py:56-60` giữ `references` của model; `normalization.py:70-73` return sớm; `:207` giữ references khi followup; được dùng ở `chat/service.py:227-230`. Rủi ro giới hạn vì `/mine` gắn với owner và chọn đúng mã đơn (Fix02) | Xóa `references` do model sinh (E5) | Phase 1 / E | A3 |
| CB-22 | P3 | **VFR** (P3) | `tracing/__init__.py:24-26` thiếu `imei`, `serial`; `WarrantyContext.imei_numbers/serial_number` (`contracts/context.py:102-103`) | Bổ sung khóa redaction (E6) | Phase 1 / E | — |
| CB-23 | P3 | **VI** (P3) | `LOCAL_SETUP.md:49,80`, `RUN_PROJECTS.md:39` ghi chatbot là mock, 58 test | Cập nhật tài liệu sau cùng (E7) | Phase 1 / E (cuối) | Tất cả |
| CB-24 | P3 | **AL** | `conversation/repository.py:58-62, 126-128, 145-148`; chỉ dùng ở dev. C1 chặn memory ở production | Không sửa; ghi chú trong tài liệu | — | C1 |

**Tổng:** 24/24 đã xác minh. Không finding nào NOT_CONFIRMED, NOT_APPLICABLE hay ALREADY_RESOLVED. Không thay đổi mức ưu tiên gốc, ngoại trừ CB-07 được tách thành phần tối thiểu (P1) và phần pooling (DEF), vì tác động throughput chưa được chứng minh.

---

# 04. Phân tích nguyên nhân gốc

| Nhóm | Findings | Loại | Giải thích |
|---|---|---|---|
| R1. Ngữ nghĩa bằng regex/chuỗi con, không có mô hình quan hệ | CB-01, CB-02, CB-03 | **Systemic** | Giá được đọc thành hai số (min, max) mà không giữ quan hệ hay span. Tham chiếu được dò bằng `in q`. Fix01 đã giải quyết bài toán này cho dung lượng (`CapacityConstraint` có `Relation` + `spans` + coverage), nhưng chưa áp dụng cho tiền và tham chiếu. |
| R2. Logic trùng ở nhiều lớp | CB-03, CB-16, CB-09 | **Systemic** | "rẻ hơn" ở 3 nơi; eligibility ở SQL và Python; giá promotion ở SQL và Java. |
| R3. Hợp đồng ngầm qua văn bản người dùng thấy | CB-03 | Local, có hệ quả systemic | Resolver đọc chuỗi clarification tiếng Việt để quyết định. |
| R4. Fail-closed đúng ý nhưng sai loại kết quả | CB-04, CB-06, CB-09 | **Systemic** | Thiếu coverage hoặc lỗi bước tùy chọn thành 503 cho toàn lượt, thay vì clarification (tìm quá rộng), degrade (ranking) hoặc loại riêng sản phẩm lỗi. |
| R5. Dev và production khác nhau, không có cổng chặn | CB-05, CB-19, CB-24, CB-13 | Systemic (cấu hình) | Mặc định thân thiện cho dev, không có validator theo env. |
| R6. Ranh giới async/sync | CB-07, CB-17 | Local | Repository đồng bộ được gọi trực tiếp trong coroutine. |
| R7. Gộp rỗng hợp lệ và không có dữ liệu | CB-10 | Local | Response generator chỉ kiểm tra "có dữ liệu hay không". |
| R8. Lỗi cục bộ đơn lẻ | CB-11, CB-18, CB-21, CB-22, CB-23 | Local | Sửa tại chỗ. |

---

# 05. Kiến trúc mục tiêu sau sửa

Giữ nguyên pipeline, contracts công khai (`QueryPlan`, `ProductSearchPlan`, `ProductSelector`, `ChatResponse`, `ProductCard`, các enum) và API hiện có. Chỉ thay đổi nội bộ:

1. **Lớp đọc giá có quan hệ** (`query_understanding/constraints.py` hoặc module mới `query_understanding/price.py`):
   - Trả về `PriceEvidence(min, max, spans, unresolved_tokens)` dùng nội bộ.
   - `price_slots()` giữ chữ ký `(min, max)` làm wrapper cho các caller hiện có.
2. **Một bộ phát hiện tham chiếu duy nhất** `detect_reference(folded_text)` trong `conversation/references.py`. Normalizer và resolver cùng dùng nó; nhánh "rẻ hơn" trong `ChatService` bị loại bỏ.
3. **Hằng clarification dùng chung**: resolver so sánh bằng `==`/`in` trên tập hằng, không so chuỗi con.
4. **Catalog/taxonomy provider** có paginate đầy đủ, kiểm completeness và cache TTL trong process (nằm trong `BackendProductClient`, không phải service mới).
5. **Kết quả "tìm quá rộng"** là `ProductResolutionError('TOO_BROAD', ...)`, thành clarification HTTP 200, không trả kết quả một phần.
6. **Ranking degrade**: lỗi embedding hoặc index thì `scores={}`, ghi event warning, vẫn dùng kết quả có cấu trúc đã xác minh.
7. **Settings validator theo env** và (nếu được duyệt) `/api/v1/ready`.
8. **Mọi I/O repository trong pipeline async đi qua `blocking()`.**

---

# 06. Chi tiết từng Fix Group

## GROUP A — Query Correctness (CB-01, CB-02, CB-03)

### A1. Parser giá có quan hệ (CB-01)

**Mục tiêu.** Mọi cách diễn đạt giá được hỗ trợ đều ánh xạ đúng cận. Không có từ so sánh nào bị đảo nghĩa.

**Root cause.** `normalization.py:46` coi `hon` đứng riêng là cận dưới bất kể từ đứng trước. `normalization.py:47` không có các dạng so sánh hướng xuống. `references.py:38-39` tái sử dụng kết quả sai đó.

**Phương án ưu tiên.** Viết lại phần nhận diện bên trong `price_slots` thành bảng grammar có quan hệ. Kết quả trả về kèm span (để A2 dùng).

| Mẫu (sau `folded`) | Quan hệ | Ghi chú |
|---|---|---|
| `duoi`, `khong qua`, `khong vuot (qua)`, `toi da`, `under`, `<=`, `<` + amount | MAX | hành vi hiện có |
| `(re|thap|nho|it) hon` + amount; `gia (re|thap) hon` + amount | MAX | **mới**; quan hệ bao gồm hay loại trừ theo DR-02 |
| amount + `tro xuong`, `do lai`, `tro lai` | MAX | **mới**, không mơ hồ |
| `tren`, `toi thieu`, `gia tu`, `(cao|dat|lon) hon`, `hon` (đứng trực tiếp trước amount, không có tính từ hướng xuống) + amount | MIN | `hon 20 trieu` = "hơn 20 triệu", giữ là MIN |
| amount + `tro len` | MIN | **mới** |
| `tu A den/toi/- B`, `A - B <unit>`, `A den B <unit>`, `giua A va B <unit>` | RANGE | hai dạng sau **bắt buộc có đơn vị tiền** để không đọc nhầm "Xiaomi 15-…" |
| `tam`, `khoang`, `co`, `chung`, `xap xi`, `quanh`, `~` + amount | **UNRESOLVED** | theo DR-01, mặc định hỏi lại |
| `ngan sach`, `budget` + amount | **UNRESOLVED** hoặc MAX | theo DR-01, mặc định khuyến nghị MAX nếu được duyệt |

Đơn vị tiền (sau folded): `trieu|tr|cu|million|k|nghin|ngan|vnd|dong|d`. Định dạng `20.000.000` và `15,5 triệu` giữ như `money()` hiện tại.

**Pseudocode:**
```
def price_evidence(q) -> PriceEvidence:
    spans, lows, highs, unresolved = [], [], [], []
    for each grammar rule in ORDERED_RULES (dài/cụ thể trước, ví dụ "re hon" trước "hon"):
        for match in finditer(rule, q) not overlapping existing spans:
            record span; append value to lows/highs, or record unresolved
    low  = max(lows)  if lows  else None     # KHÔNG dùng `x or None`: Decimal(0) là falsy
    high = min(highs) if highs else None     # "giá tối đa 0 đồng" phải ra high == Decimal('0')
    contradiction: low is not None and high is not None and low > high → caller trả clarification (giữ T14)
    return PriceEvidence(low, high, spans, unresolved)

def price_slots(q): e = price_evidence(q); return e.min, e.max   # wrapper giữ chữ ký
```

**Quy tắc giá trị 0.** Mọi nhánh kiểm tra giá trong code mới phải dùng `is None` / `is not None`, không dùng truthiness. Quy tắc này áp dụng cho `price_evidence`, `validate_price_coverage`, nhánh CHEAPER trong `references.py` (dòng 40 hiện dùng `if v is not None`, phải giữ) và `ProductEvidence`. Hành vi hiện tại của N06 ("giá tối đa 0 đồng"): `price_slots` ở `normalization.py:48` dùng `min(high) if high else None` nên trả `Decimal('0')`, và `ProductSearchPlan(max_price=0)` hợp lệ (`ge=0`). Đây là **regression control** NX-10, phải giữ nguyên.

**Thay đổi trong `references.py:35-41` (nhánh CHEAPER):** dùng `price_evidence`. Cách kết hợp giá tường minh với phạm vi snapshot được quy định ở **A3.1** (R1/R2). **Không bao giờ** lấy giá trị từ dạng so sánh hướng xuống gán vào `min_price`. Cận MIN chỉ giữ nếu người dùng nói rõ ("trên X").

**Phương án đã loại:**
- Chỉ xóa `hon` khỏi grammar cận dưới: làm hỏng "hơn 20 triệu" (đúng nghĩa là MIN).
- Để LLM quyết định giá: trái với nguyên tắc Fix01 (plan được dựng lại từ câu nói).

**File:** `chatbot-service/app/features/query_understanding/normalization.py` (`price_slots`); `chatbot-service/app/features/conversation/references.py` (`resolve_reference`, nhánh CHEAPER). Nếu tách module thì thêm mới `query_understanding/price.py`; khuyến nghị đặt trong `constraints.py` cạnh `read_capacity` để thống nhất.

**Contract giữ nguyên:** `ProductSearchPlan.min_price/max_price` bao gồm cận (Fix01 §11 "Price bounds tiếp tục inclusive"); `ProductEvidence.validate`; T14 (khoảng mâu thuẫn hỏi lại); N06 ("tối đa 0 đồng"); N01 ("20.000.000 đồng"); N11 ("trên 16 triệu và dưới 40 triệu").

### A2. Price coverage validation (CB-02)

**Mục tiêu.** Không bao giờ bỏ qua một điều kiện tiền mà vẫn trả kết quả như thể đã hiểu đủ.

**Phương án ưu tiên.** `validate_price_coverage(text, evidence)` làm theo khuôn của `validate_capacity_coverage` (`constraints.py:94-99`):
- Token tiền = `\d+(?:[.,]\d+)*\s*(?:<đơn vị tiền>)\b`, **hoặc** số đứng trong vòng 8 ký tự sau `gia|ngan sach|budget`.
- Token nằm ngoài mọi span đã parse, hoặc thuộc `unresolved` → `ConstraintError` với thông báo cụ thể:
  - "tầm/khoảng" → "Bạn muốn giá tối đa bao nhiêu? (ví dụ: dưới 15 triệu hoặc từ 13 đến 17 triệu)".
  - Token không đọc được → "Mình chưa hiểu chắc điều kiện giá…".
- Gọi trong `DomainNormalizer._normalize` ngay sau `validate_capacity_coverage(coverage_text)` (`normalization.py:93`). Gọi lại trên `scoped` khi có nhiều target (`normalization.py:143-144` đã chặn giá theo từng target).
- Resolver CHEAPER (`references.py`) cũng gọi: nếu còn token chưa đọc được thì `clarify('UNSUPPORTED_PRICE', …)`.

**Không** tự đặt biên độ ±X% cho "tầm/khoảng" khi chưa có DR-01.

**Phải không bị bắt nhầm:** số model ("Xiaomi 15", "iPhone 15", "XPS 13", "Inspiron 15"), dung lượng ("12GB/256GB"), số lượng ("2 điện thoại"), CPU ("i5"). Các token này không có đơn vị tiền và không đứng sau "giá".

### A3. Reference resolution an toàn (CB-03)

**Mục tiêu.** Phân biệt follow-up thật với yêu cầu mới; snapshot không ghi đè category/brand/target mới.

**Phương án ưu tiên:**
1. `detect_reference(q) -> kind | None` (đặt trong `references.py`), dùng mẫu neo theo cụm danh từ chỉ định:
   - ORDINAL_FIRST/SECOND: `\b(?:cai|may|chiec|san pham|mau|con|ban)\s+(?:dau tien|thu (?:nhat|hai|2))\b`, hoặc toàn câu bắt đầu bằng `(cai )?thu hai|dau tien`. **Loại trừ:** `lan dau tien`, `thu hai (tuan|toi|nay)`, `ngay thu hai`.
   - LAST: `(?:may|chiec|san pham|cai)\s+vua (?:goi y|nhac|roi)`, giữ `vua goi y`.
   - CHEAPER: `(re|thap) hon|gia thap hon|cheaper` **không** có amount theo sau.
   - CHEAPER_BOUNDED: cùng mẫu nhưng **có** amount. Giá thuộc A1; phạm vi xử lý theo A3.1 (R2/R3/R4).
   - STORAGE: giữ `storage_reference` (`constraints.py:89-91`).
2. Normalizer `normalization.py:167` và resolver `references.py:17-18` cùng gọi `detect_reference`. Bỏ danh sách chuỗi con.
3. **Thứ tự ưu tiên nguồn:** target tường minh trong câu hiện tại > category/brand/giá/dung lượng trong câu hiện tại > `snapshot.search` > plan của model.
   - CHEAPER / CHEAPER_BOUNDED: áp dụng bảng A3.1. Tóm tắt:
     - Câu nêu phạm vi mới và có giá → discovery theo câu hiện tại (R3).
     - Câu nêu phạm vi mới và không có giá → `clarify('SCOPE_CHANGED', 'Bạn muốn tìm <loại mới> rẻ hơn mức giá nào?')` (R3b).
     - Không nêu phạm vi → dùng `snapshot.search` cùng ceiling (R1) hoặc cùng giá tường minh (R2).
   - ORDINAL/LAST: nếu câu hiện tại có category, brand hoặc target mới → không resolve, trả plan hiện tại.
4. **Hằng clarification:** khai báo `ANTECEDENT_CLARIFICATIONS` (frozenset các chuỗi) tại một module dùng chung (khuyến nghị `conversation/references.py`). Normalizer import để trả về chuỗi đó; resolver kiểm tra `plan.clarification in ANTECEDENT_CLARIFICATIONS`, thay cho so chuỗi con ở `references.py:20-21`.
5. **Xóa nhánh trùng** `chat/service.py:190-202` (biến `cheaper`, `below_minimum`, lời gọi `last_product_cards`). Resolver đã xử lý ceiling. Kiểm tra `test_ai_chat_flow.py:185-209`: test này vẫn đạt nhờ resolver dùng `plan.product_search.max_price` khi snapshot chỉ có cards (`InMemoryConversationRepository.get_snapshot` dựng `EvidenceSnapshot(cards=...)`).
6. **Gỡ bản vá bù trừ** ở `normalization.py:192-193` (`if 're hon' in q and context.recent_messages and not targets: minimum = None`). Đoạn này chỉ tồn tại để xóa `min_price` sai do grammar cũ sinh ra. Sau A1, việc gỡ nó là bắt buộc; nếu giữ, cận MIN hợp lệ ("rẻ hơn mà vẫn trên 10 triệu") sẽ bị xóa âm thầm.

#### A3.1 Quan hệ giữa giá tường minh và tham chiếu

Mỗi câu có từ so sánh giá được phân thành một trong bốn trường hợp. Phân loại này **tách độc lập** hai việc: giá do A1 đọc, phạm vi do A3 quyết định.

| Trường hợp | Ví dụ | Phạm vi (category/brand/RAM/storage/CPU) | Giá | Target |
|---|---|---|---|---|
| R1. So sánh trần, có snapshot hợp lệ, câu **không** nêu phạm vi mới | "Có loại rẻ hơn không?" (T25/T30) | `snapshot.search` | `max_price = min(ceiling, snapshot.search.max_price)`, với `ceiling = min(giá hiển thị) − 1` | `[]` |
| R2. Giá tường minh, có snapshot hợp lệ, câu **không** nêu phạm vi mới | "Có máy nào rẻ hơn 18 triệu không?" | `snapshot.search` (giữ category/brand/RAM/storage/CPU của lượt trước) | `max_price = min(18M, snapshot.search.max_price)`; `min_price` lấy từ snapshot **chỉ khi** ≤ max mới, ngược lại hỏi lại; **không** áp `ceiling` từ giá hiển thị (DR-12) | `[]` |
| R3. Câu nêu phạm vi mới (category/brand/target), có hoặc không có giá | "Có laptop Dell nào rẻ hơn 18 triệu không?" | **Hoàn toàn từ câu hiện tại** (normalizer) | Từ A1 (`max=18M`) | Từ câu hiện tại |
| R3b. Như R3 nhưng **không** có giá ("Có laptop Dell nào rẻ hơn không?") và phạm vi khác snapshot | — | — | — | Hỏi lại `SCOPE_CHANGED` (không có mốc giá cho phạm vi mới) |
| R4. Không có snapshot / phiên mới | "Có máy nào rẻ hơn 18 triệu không?" | Từ câu hiện tại; không có category thì hỏi lại theo `normalization.py:189-190` | Từ A1 | — |

**Quy tắc bắt buộc:**
- Giá tường minh **luôn** được giữ (A1). Tham chiếu chỉ bổ sung **phạm vi** khi câu không tự nêu phạm vi.
- Snapshot **không bao giờ** ghi đè category, brand hay target mà câu hiện tại nêu rõ (R3).
- Target tường minh trong câu (SKU/tên/ID) → không xử lý như tham chiếu CHEAPER/ORDINAL (`references.py:19` giữ điều kiện `plan.product_search.targets`).
- Có token giá chưa đọc được (A2) → hỏi lại, kể cả khi có snapshot.
- Nếu snapshot đến từ một lượt detail, `snapshot.search` vẫn là yêu cầu discovery gốc (`chat/service.py:282-284`, hành vi Fix04 giữ nguyên).

**Thay đổi trong `resolve_reference`:**
- Nhận thêm thông tin phạm vi đã chuẩn hóa từ plan hiện tại (đã có trong `plan.product_search`) và `PriceEvidence` của câu.
- Quyết định R1–R4 theo bảng trên.
- **`QueryPlan.references` không có khóa hay giá trị mới.**
  - R1 giữ đúng giá trị đang có ở `references.py:46-47` (`{'baseline': 'historical_display_price'}`).
  - R2, R3, R4 trả `references={}`.
  - Việc phân biệt R1–R4 **chỉ** nằm trong trace event `conversation.reference_resolution` (đã có ở `references.py:25, 43-44, 59-60`), qua trường `kind` (`CHEAPER`, `CHEAPER_BOUNDED`, `ORDINAL_*`, `LAST`, `STORAGE`) và `case` (`R1`–`R4`).
  - Không thêm giá trị như `explicit_price_with_session_scope` vào public payload.

**Phương án đã loại:**
- Đưa reason code, hay **bất kỳ khóa/giá trị mới nào**, vào `QueryPlan.references`, kể cả để đánh dấu R2. Lý do: (a) làm lộ dữ liệu nội bộ vào public payload, trace và prompt (`GroundedContext` được `model_dump` cho LLM ở `response_generation/ollama_service.py:110`); (b) biến `references` thành kênh điều khiển giữa các lớp, đúng loại hợp đồng ngầm mà CB-03 đang loại bỏ. Các giá trị `references` mà ứng dụng **đã sinh sẵn** (`baseline=historical_display_price`, `source=owned_session_snapshot`, `order_code`, `order_selection`) giữ nguyên, không mở rộng.
- Thêm field vào `QueryPlan`: đổi public contract (`extra=forbid`).

**File/function:** `references.py` (`resolve_reference`, mới `detect_reference`, `ANTECEDENT_CLARIFICATIONS`); `normalization.py` (`_normalize` dòng 167-193); `chat/service.py` (`_send_message_async` dòng 190-204 và event `query.plan.effective`, giữ event nhưng bỏ trường `cheaper_followup`/`below_minimum` hoặc đặt cố định).

**Contract giữ nguyên:** T23–T30, N19–N23 (ordinal/last hợp lệ); T25 (ceiling = min − 1, giữ RAM16 qua `capacity(q,'ram')`); T26 (không bịa sản phẩm thứ hai); T29 (bản 512GB, cùng product); T30 (phiên mới → hỏi mốc giá); `test_persistent_conversation.py:145` (rẻ hơn + "RAM dưới 16GB" → giữ plan lỗi).

**Group A — Dependencies, Risks, Tests, Exit**
- **Dependencies:** A1 → A2 → A3 (A3 dùng `price_evidence` để phân biệt "rẻ hơn" có hay không có amount).
- **Regression risk:**
  - T25/T30/N19–N23 (luồng tham chiếu Fix04).
  - Câu chứa số model bị A2 bắt nhầm.
  - Event trace `query.plan.effective` (TracePanel đọc `product_search`, không đọc `cheaper_followup`).
- **Test hiện có phải đạt:** `test_query_integrity.py`, `test_query_product_v2.py`, `test_persistent_conversation.py`, `test_ai_chat_flow.py`, `test_conversation_safety.py`, `test_final_grounding_safety.py`.
- **Test mới:** NX-01 → NX-18 (mục 08).
- **Exit criteria:** xem gate A ở mục 10.3.
- **Tuyệt đối không:** đổi `QueryPlan/ProductSearchPlan`; nới `ProductEvidence.validate`; bỏ fail-closed của dung lượng; tự đặt biên độ "tầm/khoảng"; sửa `cases.json`; dùng truthiness cho giá.
- **Cần phê duyệt:** DR-01, DR-02, DR-12 (đều đang PENDING_APPROVAL).

---

## GROUP B — Product Reliability & Scalability (CB-04, CB-06, CB-09, một phần CB-15)

### B1. Catalog/brand coverage (CB-04)

**Root cause.** `catalog_names()` và `taxonomy()` (`backend.py:68-85`) có trần cứng 500 sản phẩm và 100 brand, và bị gọi lại ở **mỗi** lượt có từ khóa sản phẩm (`normalization.py:74-78`), cộng thêm lần nữa trong `resolve()` (`real_service.py:100-103`).

**So sánh phương án:**

| Phương án | Chính xác | Mở rộng | Số backend call/lượt | Phức tạp | Tác động Fix01 | Đổi backend | Rủi ro thiếu dữ liệu |
|---|---|---|---|---|---|---|---|
| A. Tra theo keyword API | Tốt cho `resolve()` khi đã biết tên; **không giải được** bài toán "tên nào trong catalog xuất hiện trong câu" của normalizer | Tốt | Thấp | Trung bình | Cao (đổi cách tìm `named`) | Không | Trung bình |
| B. Paginate đủ + completeness | Tốt | Tuyến tính theo P | ⌈P/100⌉ + ⌈B/100⌉ mỗi lượt | Thấp | Thấp | Không | Thấp (fail-closed khi không nhất quán) |
| C. Cache TTL | Phụ thuộc TTL | Tốt | ~0 khi cache còn hạn | Thấp | Thấp | Không | Sản phẩm/brand mới chưa nhận diện trong ≤TTL |
| D. Chỉ tăng limit | Tốt tới trần mới | Kém | Như B | Rất thấp | Thấp | Không | Có trần mới |

**Phương án ưu tiên: B + C, kèm trần cấu hình được.**
- `BackendProductClient.catalog_names()`:
  - Lấy trang 0 để biết `totalElements/totalPages`.
  - Duyệt hết các trang.
  - Kiểm: `totalElements` không đổi giữa các trang, số `productId` duy nhất == `totalElements`; nếu sai thì `ValueError('Catalog changed during resolution')` (giữ fail-closed).
  - Trần `catalog_max_products` (Settings, khuyến nghị mặc định 5000, theo DR-04); vượt trần → `ValueError` như hiện tại.
- `BackendProductClient.taxonomy()`: paginate brand tương tự, thay cho việc báo lỗi khi `totalElements > 100` (`backend.py:72-74`).
- **Cache trong process:** `(categories, brands, catalog_rows)` với TTL `catalog_cache_ttl_seconds` (khuyến nghị 60s, theo DR-04), khóa bằng `threading.Lock`. Chỉ cache kết quả đã đủ completeness.
  - **Điều kiện tiên quyết V-1:** xác minh `GET /api/v1/products` và `/api/v1/brands` trả cùng dữ liệu cho mọi user đã xác thực. Nếu dữ liệu phụ thuộc role thì khóa cache theo `roleName`; nếu phụ thuộc user thì **không cache** và dừng xin quyết định.
- `RealProductAdvisorService.resolve()` (`real_service.py:100-103`) dùng cùng nguồn đã cache.
- **Không** lọc INACTIVE ra khỏi `named` (Fix01: sản phẩm ngừng bán được nhận diện để trả VARIANT_NOT_FOUND, không bị mở rộng sang discovery).

### B2. Tìm kiếm quá rộng (CB-04, phần bổ sung)

**Root cause.** `real_service.py:142-149` trả `ValueError` khi ≥500 dòng (product, variant). Kết quả là 503 cho truy vấn hợp lệ nhưng rộng ("tìm laptop" khi catalog lớn).

**Phương án ưu tiên.** Giữ việc không trả kết quả một phần, nhưng đổi loại kết quả: `raise ProductResolutionError('TOO_BROAD', 'Có quá nhiều sản phẩm phù hợp để xếp hạng đầy đủ. Bạn thêm hãng, ngân sách hoặc cấu hình giúp mình nhé.')`. `chat/service.py:217-219` đã chuyển lỗi này thành clarification HTTP 200. Trần trang có thể cấu hình (`structured_search_max_rows`, mặc định giữ 500, theo DR-04).

**Phương án đã loại:** trả 100 dòng đầu, vì backend sắp `ORDER BY product_id` nên tập con là tùy ý và sai về mặt nghiệp vụ.

### B3. Ranking degrade an toàn (CB-06)

**Phân loại lỗi:**

| Lỗi | Hành vi |
|---|---|
| `embedder.embed` ném `httpx.HTTPError` hoặc `ValueError` (vector rỗng) | **fallback**: `scores={}` |
| `index.scores` ném `psycopg.Error` (connect/timeout/query) | **fallback**: `scores={}` |
| `backend.search` (bộ lọc có cấu trúc) lỗi | **unavailable 503**, giữ nguyên |
| `backend.detail` lỗi khác 404 (trừ B4) | **unavailable 503**, giữ nguyên |
| `taxonomy/catalog` thiếu completeness | **unavailable 503**, giữ nguyên |

**Thay đổi:**
- `semantic.py`:
  - Thêm `class SemanticRankingUnavailable(RuntimeError)`.
  - `ProductSemanticIndex._connect` thêm `connect_timeout=5`.
  - `scores()` bọc `psycopg.Error` thành `SemanticRankingUnavailable`.
  - `OllamaEmbeddingClient.embed` giữ nguyên ngoại lệ.
- `real_service.py:153-156`: `try … except (httpx.HTTPError, ValueError, SemanticRankingUnavailable): scores = {}; event('product.ranking_degraded', {'reason': type(exc).__name__}, status='warning')`. **Chỉ** bọc khối ranking, không bọc `backend.search`.
- Thứ tự fallback giữ như chế độ không có index hiện nay (`str(product_id)`), để không phát minh tiêu chí xếp hạng mới. Việc có thông báo cho khách biết "kết quả chưa xếp hạng theo nhu cầu" hay không là DR-09; mặc định không đổi text.

### B4. Promotion overlap 409 (CB-09)

**Ràng buộc.** Không nuốt mọi 409; không đổi invariant `PromotionServiceImpl.resolve`.

**Nhận diện:**
- Bước 0, V-2: đọc `GlobalExceptionHandler` để biết body của `PromotionException(409, "Product có nhiều Promotion hợp lệ; cần xử lý overlap trước khi bán")`.
- `BackendProductClient`: thêm `is_promotion_overlap(response)`, so khớp đúng `status==409` + body đúng message (cùng cách Fix02 làm với `PAYMENT_CARDINALITY_MESSAGE`, `client.py:89-101`).
- 409 có body khác → giữ hành vi cũ (raise, thành 503). Nếu V-2 không cho ra body ổn định → **bỏ B4**, giữ hành vi 503 hiện tại, ghi giới hạn.

**Chính sách theo loại yêu cầu.** Một sản phẩm overlap là `PRICE_UNVERIFIABLE`. Nó **không bao giờ** được báo là `NOT_FOUND`, `VARIANT_NOT_FOUND` hay "không có sản phẩm phù hợp", và không bao giờ được ẩn đi trong khi câu trả lời vẫn trông như đầy đủ.

| Loại yêu cầu | Nhận diện trong code | Hành vi khi gặp overlap |
|---|---|---|
| **Discovery không yêu cầu số lượng chính xác** | `PRODUCT_DISCOVERY`, `requested_count(...) is None` | Bỏ qua sản phẩm overlap, tiếp tục ứng viên kế tiếp đến `top_k`. Nếu `skipped_integrity > 0`, **bắt buộc** thêm câu công bố deterministic: "Có N sản phẩm phù hợp chưa xác minh được giá khuyến mãi nên chưa hiển thị." Nếu sau khi bỏ qua không còn kết quả → `ProductResolutionError('PRICE_UNVERIFIABLE', …)`, không ra câu "chưa tìm thấy sản phẩm phù hợp". |
| **Discovery có số lượng chính xác** ("đúng 2 điện thoại", N47) | `requested_count(...) = N` | Nếu bỏ qua làm số sản phẩm < N → `PRICE_UNVERIFIABLE` cho cả lượt. **Không** trả danh sách thiếu kèm câu "Chỉ tìm thấy x/N" (`response_generation/ollama_service.py:30-33`), vì câu đó ngầm nói rằng phần còn lại không tồn tại. Nếu vẫn đủ N sản phẩm đã xác minh thì như discovery thường, kèm câu công bố. |
| **Exact product / SKU / ID** (`PRODUCT_DETAIL`, 1 target) | `resolve()`, `real_service.py:111-115` | `ProductResolutionError('PRICE_UNVERIFIABLE', 'Mình chưa xác minh được giá khuyến mãi hiện tại của <tên/SKU>; bạn vui lòng thử lại sau.')`. Không thay bằng variant hay sản phẩm khác. |
| **Comparison** (`PRODUCT_COMPARE`, 2 target) | `resolve()` duyệt từng target | Bất kỳ target nào overlap → `PRICE_UNVERIFIABLE` cho **cả** lượt, nêu tên target không xác minh được. Không so sánh một nửa. |
| **Refresh ở `build_cards`** (`real_service.py:205-210`) | Overlap xuất hiện lần đầu ở lần đọc detail thứ hai | Áp dụng cùng quy tắc với loại yêu cầu tương ứng ở trên (dùng intent và count của lượt). |

**Cơ chế truyền thông tin "đã bỏ qua"** (không đổi public contract):
- `RealProductAdvisorService` ghi số bỏ qua vào ContextVar nội bộ `_integrity_skipped`, cùng phạm vi với `_current_plan`. Hàm `integrity_skipped()` được đọc ngay trong closure `search_and_build` (`chat/service.py:206-212`, cùng thread, cùng context).
- `ChatService` thêm câu công bố vào `assistant_message` **sau** khi sinh câu trả lời, trước `save_assistant`.
- Việc dùng ContextVar thêm một chỗ coupling cùng loại CB-16. Chấp nhận có ý thức; phải có test NX-26 bảo vệ.
- Phương án đổi `ProductAdvisorService.search` trả về một đối tượng kết quả bị loại vì sẽ đổi Protocol và mock advisor (phạm vi CB-16, đang DEF).

**Văn bản mới hiển thị cho khách** (câu công bố, các thông báo `PRICE_UNVERIFIABLE`) cần duyệt theo **DR-13a**. Khi chưa duyệt: B4 không được triển khai và hành vi 503 hiện tại được giữ.

**Group B — Risks, Tests, Exit**
- **Dependencies:** B1 trước B2 (cùng file `backend.py`); B3 và B4 độc lập.
- **Regression risk:** Fix01 (`named`, `validate_brands`, `test_query_integrity.py:213-222` "incomplete brand taxonomy → unavailable" phải được **cập nhật có kiểm soát**: brand >100 nhưng đủ completeness thì đạt; thiếu completeness thì vẫn unavailable); `test_pagination_boundaries.py:9-21, 24-31` cần đổi theo hợp đồng mới (6 trang vẫn có thể hợp lệ; vượt trần cấu hình hoặc thiếu completeness mới lỗi). **Việc sửa test phải giữ ý nghĩa "không trả kết quả một phần" và được nêu rõ trong báo cáo Phase 1.**
- **Exit criteria:** xem gate B ở mục 10.3.
- **Tuyệt đối không:** trả kết quả một phần như thể đầy đủ; cache khi chưa đủ completeness; cache theo user mà không xác minh V-1; đổi Java promotion hay SQL search; báo `PRICE_UNVERIFIABLE` thành NOT_FOUND.
- **Cần phê duyệt:** DR-04 (B1), DR-09, DR-13a (B4), DR-13b (văn bản B2) (PENDING_APPROVAL).

---

## GROUP C — Runtime & Configuration Hardening (CB-05, CB-07)

### C1. Production config validation và readiness (CB-05)

**Thay đổi `chatbot-service/app/shared/config.py`:**
- Thêm helper `is_dev_like(app_env) -> bool` (`{'development','local','test'}`). Đặt ở `config.py` và để `tracing.enabled` (`tracing/__init__.py:162-163`) dùng lại.
- **Vị trí validator: hàm `validate_runtime_settings(settings)` được gọi trong `lifespan` (`main.py:13-14`, trước `build_chat_service`), KHÔNG đặt `@model_validator` trên `Settings`.** Lý do: `tests/test_tracing.py:71` dựng `Settings(app_env='production', chatbot_trace_enabled=True)` và `tests/test_tracing.py:121` đặt `APP_ENV=production` cùng cấu hình mock từ `conftest.py`. Nếu đặt validator trên model, hai test này hỏng và một hợp đồng đang được kiểm thử (trace bị tắt ở production) cũng mất đi. Gọi ở lifespan thì chặn được việc khởi động thật mà vẫn giữ nguyên việc tạo `Settings` trong test.
- `validate_runtime_settings`: khi **không** dev-like thì bắt buộc
  - `chatbot_ai_enabled=True`
  - `product_advisor_mode='real'`
  - `conversation_storage='postgres'` và `conversation_database_url`
  - `product_index_database_url` (đã bắt buộc gián tiếp ở `dependencies.py:37`)
  - `chatbot_trace_enabled=False`, `chatbot_trace_file_enabled=False`

  Sai thì `ValueError` liệt kê **tên** biến sai, không in giá trị.
- **Không** bắt buộc `chatbot_warehouse_id`: code hỗ trợ hợp lệ trường hợp không có kho (`real_service.py:132-133` chỉ yêu cầu khi `in_stock_only`; text "chưa xác định tồn kho vì chưa chọn kho"). Readiness chỉ báo `warehouse_configured` như thông tin.
- Mặc định `app_env='development'` giữ nguyên (DR-05b). Kèm hướng dẫn deploy bắt buộc đặt `APP_ENV`.

**Readiness (DR-05a, thêm public API):**
- `GET /api/v1/ready`, không cần xác thực.
- Trả `{ready: bool, checks: {conversation_db, backend, ollama_chat_model, embedding_model, product_index_db, knowledge_db?}}` chỉ dạng boolean/trạng thái. Không lộ URL, model path hay secret.
- Mỗi check timeout ≤3s; `ready=false` → HTTP 503.
- `/api/v1/health` giữ nguyên (liveness).
- Nếu DR-05a bị từ chối: chỉ làm validator; ghi giới hạn "không có readiness".

`main.py:29` (`MockChatService()` lúc import) giữ nguyên vì test dùng nó khi không có lifespan; lifespan thay thế ở production. Ghi chú trong tài liệu.

#### C1.1 Các mức bảo vệ và trường hợp APP_ENV bị bỏ quên

Validator chỉ có tác dụng khi `APP_ENV` được đặt sang một giá trị không phải dev. Nếu người deploy **quên** đặt `APP_ENV`, giá trị mặc định là `development` (`config.py:10`), validator bị bỏ qua và mock/memory vẫn chạy. Code không thể tự biết "đây là production". Vì vậy cần phân tầng rõ:

| Mức | Cơ chế | Do code bảo đảm? | Chặn được trường hợp quên APP_ENV? |
|---|---|---|---|
| L1 | `validate_runtime_settings` khi env không dev-like | Có | **Không** |
| L2 | **Cảnh báo khởi động**: lifespan luôn ghi log WARNING có cấu trúc (`logging`, không in secret) nếu đang chạy mock, memory, AI tắt hoặc thiếu warehouse, kèm giá trị `app_env` hiệu lực và việc biến `APP_ENV` có được đặt tường minh không (`'app_env' in settings.model_fields_set`) | Có | Chỉ phát hiện, không chặn; cần có người hoặc hệ thống đọc log |
| L3 | **Readiness theo năng lực** (nếu DR-05a duyệt): `/api/v1/ready` trả 503 **bất kể APP_ENV** khi đang mock, memory hoặc AI tắt (`checks.production_capable=false`) | Có | **Có**, nếu hạ tầng deploy dùng `/ready` làm readiness probe |
| L4 | **Deployment gate** (ngoài repo): manifest/compose production phải đặt tường minh `APP_ENV=production`, `PRODUCT_ADVISOR_MODE=real`, `CONVERSATION_STORAGE=postgres`, `CHATBOT_AI_ENABLED=true`; readiness probe trỏ vào `/api/v1/ready`, không phải `/api/v1/health`; secrets lấy từ secret store | **Không**. Repo không có cấu hình deploy production (`docker-compose.yml` chỉ có postgres) | Có, nếu được tuân thủ |
| L5 | (DR-05b) đổi mặc định `app_env` sang `production` (fail-safe) | Có | Có, nhưng mọi môi trường dev phải đặt `APP_ENV=development` (`.env.example` đã có; `conftest.py` cần đặt thêm) |

**Khuyến nghị (PROPOSED):**
- L1 + L2 trong đợt này (không cần duyệt API).
- L3 nếu DR-05a được duyệt.
- L4 dưới dạng checklist trong tài liệu triển khai (E7).
- L5 chỉ khi DR-05b chọn fail-safe.

**Giới hạn phải công bố:** khi thiếu L3 hoặc L4, việc quên `APP_ENV` **vẫn** có thể làm production chạy mock. Phase 2 chỉ kiểm được L1–L3 bằng mô phỏng env, và chỉ kiểm được L4 ở mức tài liệu, không phải hạ tầng thật.

### C2. Bỏ I/O đồng bộ khỏi event loop (CB-07, phần tối thiểu)

**Thay đổi `chat/service.py:_send_message_async`:** bọc các lời gọi sau bằng `await blocking(...)`:
- `conversation_service.get_session` (dòng 116)
- `create_session` (119)
- `add_message` (126)
- `get_context_messages` (132)

Dòng 193 đã bị xóa ở A3. `get_snapshot` (177) và `save_assistant` (289) đã dùng `blocking`.

**Lưu ý Fix04:** `blocking()` shield và settle task nên một USER insert đang chạy sẽ hoàn tất rồi mới ném `CancelledError`. Kết quả tương đương trường hợp B09/B10 đã được nghiệm thu (USER bỏ dở không vào context). Lease vẫn chỉ được giải phóng trong `request_lease.finally` sau khi mọi `blocking` đã settle.

**Hoãn (DEF, cần số đo):** `psycopg_pool`, vì:
- Cần thêm dependency.
- Advisory lock session-level phải được unlock trước khi trả connection về pool. Rủi ro rò lock là thực.
- Throughput chưa được chứng minh là vấn đề.

Executor riêng cho Ollama cũng hoãn.

**Group C — Risks, Tests, Exit**
- **Regression risk:**
  - C1: test hoặc script chạy với `APP_ENV` khác dev → kiểm `conftest.py` và `.env.example` (đang `development`).
  - C2: thứ tự event trace; các test cancellation của Fix04 (`cancel_enter`, `cancel_model`).
- **Exit criteria:** xem gate C ở mục 10.3.
- **Tuyệt đối không:** giữ transaction trong suốt thời gian model I/O; đổi `request_lease`/`settle`; đổi cách lấy advisory lock; tự thêm pool.
- **Cần phê duyệt:** DR-05a (endpoint), DR-05b (mặc định env).

---

## GROUP D — UI/Integration Readiness (CB-08) — **DECISION_REQUIRED**

Không bắt đầu Group D cho tới khi người dùng chọn phương án (DR-03, PENDING_APPROVAL).

**Hai trạng thái UI độc lập** (định nghĩa đầy đủ ở mục 14):
- **UI_LAB_READY** — Chatbot Lab (`/chatbot-lab`) đủ dùng cho UAT nội bộ có kiểm soát. Option A chỉ hướng tới trạng thái này.
- **UI_STOREFRONT_READY** — khách hàng thật dùng chatbot trên storefront bản build production. Chỉ Option B, cùng hạ tầng L4 (mục C1.1), mới có thể đạt trạng thái này.

Đạt UI_LAB_READY **không** có nghĩa là đã có sản phẩm chatbot cho khách hàng. Không báo cáo, verdict hay tài liệu nào được suy diễn UI_STOREFRONT_READY từ kết quả của Lab.

**OPTION A — Hoàn thiện Chatbot Lab cho UAT** (khuyến nghị nếu mục tiêu trước mắt là UAT)
- `Fontend/src/App.jsx:51-53`: bọc `chatbot-lab` bằng `RequireRole roles={[ROLES.CUSTOMER]}` để tránh tình huống staff vào được rồi nhận 403.
- `ChatbotLabPage.jsx:107`: không khớp ASSISTANT bằng text. Giữ nguyên cách hiện tại nhưng ghi chú; muốn sửa đúng cần response trả message id, tức là đổi public `ChatResponse` (DR).
- Tài liệu triển khai lab cho UAT: biến `VITE_CHATBOT_LAB_ENABLED`, proxy `/api/v1/chat`.
- Phạm vi: khoảng 2–3 file frontend + tài liệu. Test: `ChatbotLab.test.jsx`.

**OPTION B — Chatbot cho khách trên storefront**
- Mới: `Fontend/src/features/chatbot/ChatWidget.jsx` (+ css, test), dùng lại `chatbotApi.js`, không có TracePanel hay status inspector.
- `AppLayout.jsx`: mount widget khi `user?.roleName === ROLES.CUSTOMER`.
- Điều hướng ProductCard dùng `/products/:productId` (đã có).
- Session: một session cho mỗi user, lưu trong `sessionStorage` (như lab).
- **Production routing:** cần reverse proxy cùng origin cho `/api/v1/chat` → chatbot-service. Repo không có cấu hình deploy production, nên cần quyết định hạ tầng (DR-03b). CSRF hiện dựa vào `SameSite=Lax` (`application.properties:14`); nếu khác origin hoặc `SameSite=None` phải có thêm kiểm tra CSRF ở FastAPI (thay đổi bảo mật, cần duyệt).
- Phạm vi: khoảng 4–6 file frontend + cấu hình proxy + browser test. Đây là **tính năng mới**, không phải bug fix.

---

## GROUP E — P2/P3 được chọn (CB-10, CB-11, CB-18, CB-19, CB-21, CB-22, CB-23)

| Mã | Finding | Thay đổi cụ thể | File / function | Rủi ro | Test |
|---|---|---|---|---|---|
| E1 | CB-10 | **V-3** trước. ORDER/PAYMENT: backend trả danh sách rỗng hợp lệ (`get_recent_orders` → `[]` từ 200) thì trả "Tài khoản của bạn chưa có đơn hàng nào." CART: theo V-3, nếu 404/204 nghĩa là chưa có giỏ thì "Giỏ hàng của bạn hiện đang trống." Không suy ra "rỗng" từ lỗi. Cần phân biệt "200 rỗng" với `None` do 404, nên `BackendClient` phải báo rõ nguồn (ví dụ trả `[]` chỉ khi 200; 404 → `None` để caller quyết định). | `response_generation/ollama_service.py:56-97`; `customer_context/service.py:57-77`; `shared/backend/client.py:122-155` | Thấp. Không ảnh hưởng N27/N28 (có đơn) | NX-36, NX-37 |
| E2 | CB-11 | ORDER_STATUS không có code và không "mới nhất": hiển thị N đơn mới nhất (DR-06, mặc định 5) + dòng "Đang hiển thị N/total đơn gần nhất; bạn có thể hỏi theo mã đơn." Cắt ở response generator, giữ context đủ để tra mã. | `response_generation/ollama_service.py:60-67` | Thấp | NX-38 |
| E3 | CB-18 | So `r['orderCode'].casefold() == order_code.casefold()`; vẫn raise khi khớp >1 | `customer_context/service.py:61-64` | Rất thấp | NX-39, NX-40 |
| E4 | CB-19 | `/status` trả 404 khi `not is_dev_like(app_env)` | `chat/router.py:101-134` | Thấp (Lab chạy dev) | NX-41 |
| E5 | CB-21 | Sau validate, đặt `plan.references={}` cùng chỗ xóa `clarification` (`query_understanding/ollama_service.py:58`). References chỉ được tạo bởi `policy.read_only_plan` và `references.resolve_reference`. Kiểm `normalization.py:207` (`references=plan.references if followup`): sau E5 giá trị này luôn `{}`, cần xác nhận không test nào phụ thuộc. | `query_understanding/ollama_service.py:56-60`; `normalization.py:207` | Thấp–trung bình | NX-42, NX-43 + chạy lại test QU |
| E6 | CB-22 | Thêm `imei`, `imeinumbers`, `serial`, `serialnumber` vào `SENSITIVE` | `shared/tracing/__init__.py:24-26` | Rất thấp | NX-44 trong `test_tracing.py` |
| E7 | CB-23 | Cập nhật `LOCAL_SETUP.md` (dòng 49, 80), `RUN_PROJECTS.md:39`, `chatbot-service/README.md`: các chế độ mock/real, biến bắt buộc ở production (C1), readiness, giới hạn còn lại. Không ghi số test nếu chưa chạy. | Tài liệu | Không | Review |

**Không đưa vào đợt này:** CB-12 (DR-07), CB-13/CB-14 (DR-08, cần API/schema), CB-15 phần còn lại, CB-16, CB-17, CB-20, CB-24.

**Không nâng ưu tiên CB-21/CB-22/CB-09:** source chứng minh rủi ro bị giới hạn. CB-21 có `/mine` gắn với owner và chọn đúng mã đơn. CB-22 chỉ xảy ra ở dev với tracing đã tắt mặc định. CB-09 phụ thuộc dữ liệu promotion bất thường, mà backend đã chặn đúng cách.

---

# 07. Regression Impact Matrix

| Proposed Change | Related Fix | Affected Modules | Regression Risk | Required Controls |
|---|---|---|---|---|
| A1 parser giá | Fix01 (hard price filters, `ProductEvidence`) | `normalization.price_slots`, `references` | Trung bình: T07–T11, T14, T23, T55, N01, N02, N04, N06, N11, N40, N46 | Bảng unit test; chạy lại đủ 120 case |
| A2 price coverage | Fix01 (fail-closed) | `DomainNormalizer._normalize` | Trung bình: bắt nhầm số model (T16, T22, N09, N12) | Test âm tính số model, dung lượng, số lượng |
| A3 reference detection + xóa nhánh trùng | Fix04 (snapshot integrity, completed-turn), Fix01 (`storage_reference`) | `references`, `normalization`, `chat/service` | Trung bình–cao: T23–T30, N19–N23 | Toàn bộ `test_persistent_conversation.py`, `test_ai_chat_flow.py:185`; NX-11 → NX-18 |
| B1 catalog/brand pagination + cache | Fix01 (brand/category validation, identity) | `BackendProductClient`, `DomainNormalizer`, `resolve` | Trung bình: dữ liệu cũ trong TTL | V-1; test completeness; TTL cấu hình được |
| B2 TOO_BROAD | Fix04 (structured cap) | `RealProductAdvisorService.search` | Thấp | `test_pagination_boundaries` cập nhật có kiểm soát |
| B3 ranking degrade | — | `semantic`, `real_service` | Thấp | Fault injection; structured search lỗi vẫn 503 |
| B4 promotion 409 | Fix02 (cách so khớp body lỗi) | `BackendProductClient`, `real_service` | Thấp | Chỉ khớp đúng một dạng body; 409 khác không đổi |
| C1 Settings validator | — | `config`, `tracing.enabled` | Thấp | Ma trận cấu hình |
| C1 `/ready` | — | `main.py` | Thấp | Không lộ secret |
| C2 `blocking()` | **Fix04** (cancellation, lease, persistence) | `chat/service` | Trung bình | B08/B09/B10, `cancel_enter`, `cancel_model` |
| E1 rỗng hay không có | Fix02 (no fabricated payment) | Response generation, BackendClient | Thấp | N27/N28 không đổi |
| E3 case-insensitive | Fix02 (đúng mã đơn, không thay bằng đơn khác) | `customer_context` | Rất thấp | Đơn trùng → vẫn lỗi |
| E5 xóa references của model | Fix02 (order_code), Fix04 (safety intent) | QU | Thấp–trung bình | Read-only guard vẫn tạo references |
| Tất cả | Fix04 (refusal giao dịch, frontend race/retry) | `policy`, `actions`, frontend | Thấp | N38/N40/N41 + test frontend |

---

# 08. Kế hoạch kiểm thử (dành cho Phase 2; không chạy trong nhiệm vụ này)

## 8.1 Bộ test mới (NX), thống kê riêng, **không** cộng vào 120

**Bốn loại test** (cột "Loại"):
- **BR — Bug-reproduction:** tái hiện đúng một lỗi đã xác minh. **Bắt buộc FAIL trên `f99f713` và PASS sau khi sửa.**
- **RC — Regression control:** hành vi hiện tại là đúng và phải giữ. **PASS ở cả hai HEAD.**
- **AN — Approved-new-behavior:** hành vi mới mà trên HEAD cũ không có hoặc có dạng khác (thường là 503) mà không sai về nghiệp vụ. **Chỉ yêu cầu PASS sau khi sửa**; không áp tiêu chí FAIL-trước. Chỉ được viết khi DR liên quan đã được duyệt.
- **FC — Fault/concurrency:** fault injection hoặc đồng thời/hủy. Cột "Trên f99f713" ghi rõ kỳ vọng trước sửa: FAIL (tái hiện lỗi), PASS (control) hoặc N/A (hành vi mới).

Cột "Trên f99f713": FAIL / PASS / N/A. "Điều kiện" nêu DR hoặc bước xác minh tiền đề mà test phụ thuộc.

| ID | Loại | Trên f99f713 | Đầu vào / điều kiện | Kỳ vọng sau sửa | Điều kiện |
|---|---|---|---|---|---|
| NX-01 | BR | FAIL | "laptop rẻ hơn 15 triệu" (phiên mới) | `max_price` = 15.000.000 (≤X, hoặc theo DR-02); `min_price is None`; nếu DR-02 chưa duyệt thì clarification, nhưng tuyệt đối không có `min_price=15M` | DR-02 |
| NX-02 | BR | FAIL | "laptop giá thấp hơn 20 triệu" | Như NX-01 với 20M | DR-02 |
| NX-03 | RC | PASS | "laptop hơn 20 triệu", "laptop đắt hơn 20 triệu", "laptop trên 20 triệu" | `min_price=20M` | — |
| NX-04 | BR | FAIL | "điện thoại 10 triệu trở xuống" / "điện thoại 10 triệu trở lên" | Không bị bỏ qua âm thầm: MAX/MIN 10M khi có quy tắc A1, ngược lại clarification | — |
| NX-05 | BR | FAIL | "laptop giá 15–20 triệu", "laptop 15 đến 20 triệu" | RANGE 15M–20M (hoặc clarification); không bỏ qua âm thầm | — |
| NX-06 | RC | PASS | "laptop giá từ 15 đến 20tr"; "laptop giá từ 25 triệu đến 20 triệu" (T14) | RANGE; T14 → clarification khoảng mâu thuẫn | — |
| NX-07 | BR | FAIL | "laptop tầm 15 triệu", "laptop khoảng 15 triệu" | Không trả discovery thiếu điều kiện giá; clarification (hoặc quy tắc theo DR-01) | DR-01 |
| NX-08 | BR | FAIL | "laptop ngân sách 20tr" | Không bỏ qua âm thầm; MAX 20M nếu DR-01 duyệt, ngược lại clarification | DR-01 |
| NX-09 | RC | PASS | "Xiaomi 15 giá bao nhiêu", "So sánh Dell XPS 13 và Dell Inspiron 15", "Chỉ gợi ý đúng 2 điện thoại Xiaomi", "Xiaomi 15 bản 12GB/256GB" | Không có lỗi coverage giá; plan giống trước khi sửa | — |
| NX-10 | RC | PASS | **N06** "Tìm điện thoại giá tối đa 0 đồng"; unit `price_evidence(folded(...))` | `max_price == Decimal('0')` (không phải `None`); `ProductSearchPlan` hợp lệ; plan giống trước khi sửa | — |
| NX-11 | BR | FAIL | Snapshot điện thoại Apple → "Có laptop Dell nào rẻ hơn không?" | `SCOPE_CHANGED` clarification (R3b); không trả điện thoại | — |
| NX-12 | BR | FAIL | Có lịch sử + snapshot → "Đây là lần đầu tiên tôi mua laptop, tư vấn laptop dưới 20 triệu" | Discovery Laptop ≤20M; không phải PRODUCT_DETAIL từ snapshot | — |
| NX-13 | BR | FAIL | Snapshot ≥2 thẻ → "Thứ hai tuần sau có hàng laptop Dell không?" | Không resolve ordinal | — |
| NX-14 | BR | FAIL | Snapshot laptop (search: Laptop, RAM≥16, ≤20M) → "Có máy nào rẻ hơn 18 triệu không?" | R2: category=Laptop, `min_ram_gb=16` giữ nguyên; `max_price=18M`; `min_price is None`; không áp ceiling (DR-12) | DR-02, DR-12 |
| NX-15 | RC | PASS | **T25/T30-like**: snapshot → "Có loại rẻ hơn không?", "Có chiếc nào rẻ hơn mà RAM vẫn 16GB không?"; phiên mới → "Có loại rẻ hơn không?" | R1: ceiling = min − 1, giữ RAM16; phiên mới → hỏi mốc giá | — |
| NX-16 | AN | N/A | Snapshot → "Có loại thấp hơn không?" | Như R1 (sau khi gom logic, "thấp hơn" đi qua resolver) | — |
| NX-17 | RC | PASS | **N19/N20/N23-like**: "Máy đầu tiên còn hàng không?", "Cái thứ hai có giá bao nhiêu?", "Chiếc máy vừa gợi ý giá bao nhiêu?" | Resolve đúng thẻ như trước | — |
| NX-18 | BR | FAIL | Snapshot điện thoại → "Có laptop Dell nào rẻ hơn 18 triệu không?" | R3: discovery Laptop/Dell, `max_price=18M`, không dùng phạm vi snapshot | DR-02 |
| NX-19 | BR | FAIL | Catalog tổng hợp 1.000 sản phẩm + 150 brand (MockTransport) | Normalizer chạy; không 503 | DR-04 |
| NX-20 | AN | N/A | `totalElements` đổi giữa các trang / `productId` trùng | Unavailable (fail-closed) | DR-04 |
| NX-21 | AN | N/A | Gọi normalizer 2 lần trong TTL | Lần đầu ⌈P/100⌉ + ⌈B/100⌉ lời gọi; lần sau 0 | DR-04, V-1 |
| NX-22 | BR | FAIL | Structured search trả ≥ trần dòng | Clarification TOO_BROAD (HTTP 200); không gọi `detail` | DR-13b |
| NX-23 | FC | FAIL | Embed trả 500 / timeout; structured search hợp lệ | Có thẻ sản phẩm; event `product.ranking_degraded` | — |
| NX-24 | FC | FAIL | `psycopg` connect timeout / OperationalError ở index | Như NX-23; không có 500 | — |
| NX-25 | FC | PASS | `backend.search` lỗi (5xx/timeout) | 503 như hiện tại | — |
| NX-26 | FC | FAIL | Discovery không có count; detail 409 overlap ở 1/3 sản phẩm | 2 sản phẩm còn lại + câu công bố "Có 1 sản phẩm… chưa xác minh được giá"; ContextVar `_integrity_skipped` được reset giữa các lượt | V-2, DR-13a |
| NX-27 | FC | N/A | Discovery "đúng 2 điện thoại…", 1 trong 2 overlap | `PRICE_UNVERIFIABLE`; không có câu "Chỉ tìm thấy 1/2" | V-2, DR-13a |
| NX-28 | FC | N/A | Detail theo SKU/tên có overlap | `PRICE_UNVERIFIABLE`; không có NOT_FOUND/VARIANT_NOT_FOUND | V-2, DR-13a |
| NX-29 | FC | N/A | Compare 2 target, 1 target overlap; discovery mà mọi ứng viên đều overlap | `PRICE_UNVERIFIABLE` cho cả lượt; không so sánh một nửa; không có câu "chưa tìm thấy sản phẩm phù hợp" | V-2, DR-13a |
| NX-30 | FC | PASS | Detail 409 có body khác | 503 như hiện tại | — |
| NX-31 | BR | FAIL | `validate_runtime_settings` với `APP_ENV=production` × {mock, memory, AI tắt, trace bật, thiếu DB URL} | Lifespan từ chối khởi động; thông báo chỉ có tên biến | — |
| NX-32 | AN | N/A | Không đặt `APP_ENV` + mock/memory | Log WARNING L2 có `app_env_explicit=false`, không in secret; khởi động vẫn thành công | — |
| NX-33 | AN | N/A | `/api/v1/ready` khi DB, Ollama, model lỗi, hoặc chạy mock/memory | 503 + checks boolean, `production_capable=false`; không có URL/secret | DR-05a |
| NX-34 | FC | FAIL | Repository giả chậm 2s, 5 request đồng thời ở 5 session khác nhau + gọi `/health` | `/health` trả trong khi các request đang chạy; không có lượt xen kẽ | — |
| NX-35 | FC | PASS | Hủy request trong lúc `add_message` / model | Lease được giải phóng; USER bỏ dở không vào context (tương đương Fix04 `cancel_*`) | — |
| NX-36 | BR | FAIL | Khách có 0 đơn (200, `content=[]`) hỏi "đơn hàng của tôi" | "chưa có đơn hàng"; không phải `INSUFFICIENT` | — |
| NX-37 | BR | FAIL | Giỏ hàng theo V-3 (404/204 = chưa có giỏ) | Câu đúng ngữ nghĩa; lỗi backend vẫn là lỗi | V-3 |
| NX-38 | BR | FAIL | 30 đơn, hỏi không có mã | Hiển thị N đơn + "N/30" | DR-06 |
| NX-39 | BR | FAIL | Mã đơn viết thường "ord-…" | Khớp đúng một đơn của chính khách | — |
| NX-40 | RC | PASS | 2 đơn trùng mã (sau casefold) | Vẫn lỗi ambiguous (502), không chọn đơn nào | — |
| NX-41 | BR | FAIL | `APP_ENV=production` gọi `/api/v1/chat/status` | 404 | — |
| NX-42 | BR | FAIL | Model trả `ORDER_STATUS` + `references.order_code` cho câu không có mã | `references == {}`; load context không lọc theo mã đó | — |
| NX-43 | RC | PASS | "Đơn hàng ORD-… của tôi đã thanh toán chưa?" (read-only guard) | `references.order_code` vẫn được tạo bởi `policy.read_only_plan` (N27/N28 không đổi) | — |
| NX-44 | BR | FAIL | Trace có `imeiNumbers`, `serialNumber` | `[REDACTED]` | — |

**Thống kê NX (44 case):**

| Loại | Số case | ID |
|---|---:|---|
| BR | 21 | 01, 02, 04, 05, 07, 08, 11, 12, 13, 14, 18, 19, 22, 31, 36, 37, 38, 39, 41, 42, 44 |
| RC | 8 | 03, 06, 09, 10, 15, 17, 40, 43 |
| AN | 5 | 16, 20, 21, 32, 33 |
| FC | 10 | 23, 24, 25, 26, 27, 28, 29, 30, 34, 35 (trong đó FAIL-trước: 23, 24, 26, 34; PASS-trước: 25, 30, 35; N/A: 27, 28, 29) |
| **Tổng** | **44** | Không cộng vào 120 frozen case |

Case nào có DR hoặc V-x chưa đạt điều kiện thì được ghi **NOT_RUN_PENDING_DECISION**, không tính PASS/FAIL. Thay đổi trên không thay đổi số lượng hay nội dung của 120 case.

## 8.2 Test hiện có phải chạy lại
- **Python:** toàn bộ `chatbot-service/tests` với DB thật (để các test Postgres không bị skip; báo cáo phải ghi số skip = 0 hoặc nêu lý do).
- **Java:** chỉ khi Group B/E chạm contract backend (không dự kiến). Nếu không đổi Java thì không tuyên bố đã chạy lại.
- **Frontend:** `ChatbotLab.test.jsx`, `TracePanel.test.jsx`, `apiClient.test.js`; thêm test widget nếu chọn Option B; build + lint.

## 8.3 Fault injection và integration thật
- Embed lỗi, index DB lỗi, backend detail 409/500, conversation DB chậm hoặc mất.
- Chạy thật: Qwen thật + Spring thật + DB clone cô lập, đúng cách làm của Fix01/02/04, có fingerprint dữ liệu nghiệp vụ trước và sau.

## 8.4 Regression cuối 120 case
- Chạy lại **đủ 120 case** (T01–T60, N01–N48, B01–B12) với `cases.json` **không đổi** (kiểm hash so với bản hiện tại).
- So sánh với baseline 99/8/9/4. Mọi case chuyển từ PASS sang trạng thái khác đều phải giải trình.
- Nếu code đổi sau khi đã chạy regression, phải chạy lại các case bị ảnh hưởng **và** đủ 120 case trước khi tuyên bố nghiệm thu cuối.

## 8.5 Kiểm thử trên trình duyệt
- Lab: gửi, retry, đổi phiên, đóng phiên, render ProductCard và nguồn, trạng thái lỗi 401/403/409/503.
- Option B (nếu chọn): widget trên desktop và viewport mobile, điều hướng sản phẩm, đăng xuất/đăng nhập lại.

---

# 09. Kế hoạch triển khai Phase 1 — FIX

**Phạm vi được phép:** các file liệt kê ở mục 06; test mới hoặc cập nhật có giải trình; tài liệu ở E7. Mọi thứ khác ngoài phạm vi.

| Bước | Nội dung | Điều kiện dừng xin quyết định |
|---|---|---|
| 0 | **Xác minh tiền đề** (chỉ đọc): V-1 `ProductServiceImpl.getProducts` và brand list có phụ thuộc role/user không; V-2 body 409 của `PromotionException` qua `GlobalExceptionHandler`; V-3 ngữ nghĩa 404/204 của `/api/cart`; V-4 `useWarehouse` (chỉ để ghi chú CB-12). Ghi kết quả vào báo cáo Phase 1. | V-1 phụ thuộc user → dừng (B1 không cache). V-2 không có body ổn định → bỏ B4, ghi giới hạn. |
| 1 | Chụp baseline: HEAD, hash `cases.json`, danh sách test hiện có. | — |
| 2 | A1 parser giá + NX-01 → NX-06, NX-10 | DR-02 PENDING → dạng "thấp hơn/rẻ hơn X" trả **clarification** (fail-safe), không tự chọn ≤X hay <X; yêu cầu tối thiểu "không bao giờ đặt `min_price`" vẫn bắt buộc |
| 3 | A2 coverage + NX-07 → NX-09 | DR-01 PENDING → "tầm/khoảng/ngân sách" đều hỏi lại |
| 4 | A3 `detect_reference`, A3.1, hằng clarification, xóa `chat/service.py:190-202` và `normalization.py:192-193`, NX-11 → NX-18 | DR-12 PENDING → R2 dùng giá tường minh, không áp ceiling (phương án không thêm điều kiện ẩn), ghi là tạm thời. Test Fix04 hiện có fail mà không thể sửa nếu không đổi kỳ vọng → **dừng** |
| 5 | B1 + B2, NX-19 → NX-22, cập nhật có kiểm soát `test_pagination_boundaries.py` và `test_query_integrity.py:213-222` | DR-04 PENDING → không làm B1; giữ trần 500/100, không cache. B2 chỉ làm khi DR-13b APPROVED (văn bản `TOO_BROAD`), nếu không thì giữ 503. Cần đổi Java hoặc API → dừng |
| 6 | B3 + NX-23 → NX-25 | — |
| 7 | B4 + NX-26 → NX-30 | Chỉ khi V-2 đạt **và** DR-13a đã duyệt; nếu không thì bỏ B4, giữ 503 |
| 8 | C1 validator L1 + log L2 (NX-31, NX-32); `/ready` L3 (NX-33) chỉ khi DR-05a duyệt | — |
| 9 | C2 `blocking()`, NX-34, NX-35 | Cancellation test của Fix04 fail → dừng |
| 10 | E1 → E6 + NX-36 → NX-44 | E1 cart phụ thuộc V-3; E2 chỉ khi DR-06 duyệt |
| 11 | Group D, **chỉ khi** DR-03 đã có | Không có quyết định → bỏ qua, ghi DEFERRED |
| 12 | E7 tài liệu | — |
| 13 | Chạy test suite đầy đủ (Python có DB, frontend nếu đổi); lưu output thật vào `chatbot-service/debug/production-readiness/evidence/` | — |
| 14 | Viết `02_FIX_EXECUTION_REPORT.md`: diff theo finding, test đã chạy kèm output, giới hạn còn lại. **Không** tự tuyên bố đã nghiệm thu. | — |

**Điều kiện dừng chung** (dừng và hỏi người dùng):
- Cần đổi public contract, API (ngoài `/ready` đã duyệt), schema hay migration.
- Phải sửa frozen expectation.
- Một bảo vệ của Fix01/02/04 phải nới để test đạt.
- Phát hiện lỗi P0 mới.
- Kết quả xác minh tiền đề trái với giả định.

---

# 10. Kế hoạch Phase 2 — NGHIỆM THU

## 10.1 Kiểm tra độc lập

**Nguyên tắc:** người nghiệm thu không dựa vào lời khẳng định của coding agent. Mọi kết luận phải có bằng chứng: diff, output lệnh, trace, JSON kết quả.

| # | Kiểm tra | Bằng chứng bắt buộc |
|---|---|---|
| 1 | Diff thực tế khớp phạm vi mục 06; không có file ngoài phạm vi | `git diff --stat` + review theo finding |
| 2 | Từng root cause được xử lý tại đúng vị trí (bảng mục 03) | Ánh xạ file:line → finding |
| 3 | Test BR thực sự tái hiện lỗi gốc | **Chỉ áp dụng cho loại BR** và FC có ghi "FAIL" (NX-23, 24, 26, 34): chạy trên `f99f713` (worktree tách riêng) phải **FAIL**, trên HEAD mới phải **PASS**. Một test BR mà PASS trên HEAD cũ nghĩa là không tái hiện lỗi; test đó phải viết lại, không được tính là bằng chứng |
| 4 | Regression controls giữ nguyên | RC và FC có ghi "PASS" (NX-03, 06, 09, 10, 15, 17, 25, 30, 35, 40, 43) phải **PASS ở cả hai HEAD** |
| 4b | Approved-new-behavior | AN và FC có ghi "N/A": chỉ yêu cầu PASS trên HEAD mới, **và** DR liên quan phải có trạng thái APPROVED; nếu không thì NOT_RUN_PENDING_DECISION |
| 5 | Production không âm thầm chạy mock (trong phạm vi L1–L3) | NX-31, NX-32, NX-33 (nếu DR-05a duyệt) + đọc `config.py`/`main.py` |
| 6 | Lỗi dependency không tạo dữ liệu giả | NX-23, NX-24, NX-25, NX-26..NX-30 (nếu B4 triển khai) + trace |
| 7 | Authorization/ownership còn nguyên | Test ownership hiện có + B11 |
| 8 | Dữ liệu nghiệp vụ nguồn không bị sửa | Fingerprint trước/sau như Fix02/04 |
| 9 | Hành vi runtime khớp contract | E2E Qwen thật + Spring thật |
| 10 | HTTP 200 không bị đánh đồng với đúng nghiệp vụ | Chấm verdict theo nội dung |
| 11 | Không ghi nhận test chưa có output | Mỗi dòng trong báo cáo đều có link output |
| 12 | Regression 120 case đầy đủ, hash `cases.json` không đổi | `results.json` mới + bảng so sánh với baseline |
| 13 | UI readiness đánh giá **riêng hai trạng thái** UI_LAB_READY và UI_STOREFRONT_READY (mục 14) | Test component + browser cho Lab; storefront chỉ đánh giá nếu Option B đã triển khai, nếu không thì ghi DEFERRED |
| 14 | Giới hạn còn lại công khai | Mục giới hạn trong báo cáo nghiệm thu |

## 10.2 Verdict

- **PASS** — đạt đủ tiêu chí trong phạm vi.
- **PASS_WITH_CONDITIONS** — đạt trong phạm vi có điều kiện ghi rõ; không còn blocker chưa được chấp thuận.
- **FIX_REQUIRED** — còn lỗi trong phạm vi bắt buộc.
- **BLOCKED_CONTRACT** — thiếu quyết định nghiệp vụ/contract (ví dụ DR-01, DR-03).
- **BLOCKED_ENVIRONMENT** — không kiểm thử được vì môi trường.

Phần DEFERRED **không** được đánh PASS.

## 10.3 Acceptance gate riêng cho từng group

Mỗi group nhận verdict riêng. Verdict tổng thể không được cao hơn verdict thấp nhất của các group bắt buộc (A, B, C, và E trong phạm vi đã duyệt).

### Gate A — Query correctness (CB-01, CB-02, CB-03)
1. BR NX-01, 02, 04, 05, 07, 08, 11, 12, 13, 14, 18: FAIL trên `f99f713`, PASS sau sửa (với các case có DR chưa duyệt: NOT_RUN_PENDING_DECISION).
2. RC NX-03, 06, 09, 10, 15, 17: PASS ở cả hai HEAD.
3. Không câu nào chứa token tiền mà kết quả `PRODUCT_DISCOVERY` lại không phản ánh token đó trong `min_price/max_price` (kiểm bằng property test trên bảng NX-01..10).
4. "rẻ hơn/thấp hơn + amount" không bao giờ sinh `min_price`.
5. Giá 0 được giữ (`Decimal('0')`, không phải `None`) ở mọi đường xử lý (NX-10).
6. **Fix01 gate:** `test_query_integrity.py`, `test_query_product_v2.py` PASS không sửa kỳ vọng, trừ thay đổi có kiểm soát đã ghi. 120-case: T07–T11, T14, T16, T21, T22, T55, N01–N12, N40, N46, N47 không chuyển từ PASS sang trạng thái kém hơn so với kết quả đã nghiệm thu của Fix01. `ProductEvidence.validate` và chữ ký `price_slots` không đổi.
7. **Fix04 gate:** `test_persistent_conversation.py`, `test_ai_chat_flow.py` (gồm `:185`), `test_conversation_safety.py` PASS. 120-case T23–T30, N19–N23 không kém hơn. `query.plan.effective` vẫn được ghi.

### Gate B — Product reliability (CB-04, CB-06, CB-09)
1. BR NX-19, 22 và FC NX-23, 24, 26: FAIL trước / PASS sau. RC/FC NX-25, 30: PASS cả hai. AN/FC NX-20, 21, 27, 28, 29: PASS sau (khi DR-04/DR-13a/DR-13b/V-2 đạt).
2. Không có đường nào trả kết quả một phần như thể đầy đủ: review diff + NX-22, NX-26, NX-27.
3. `PRICE_UNVERIFIABLE` không bao giờ bị biến thành NOT_FOUND, VARIANT_NOT_FOUND hay "chưa tìm thấy sản phẩm phù hợp" (NX-27, 28, 29).
4. Cache chỉ chứa dữ liệu đã đủ completeness; khóa cache phù hợp kết quả V-1.
5. **Fix01 gate:** identity, brand/category validation, VARIANT_NOT_FOUND, đúng số thẻ (N12, N47) không kém hơn; `test_real_product_advisor.py` PASS.
6. **Fix02 gate:** không đổi xử lý 409 payment (`client.py:89-101`); N27/N28 không kém hơn.
7. **Fix04 gate:** structured-cap vẫn fail-closed (chỉ đổi loại kết quả sang TOO_BROAD), `test_pagination_boundaries.py` cập nhật có giải trình từng assertion.

### Gate C — Runtime & configuration (CB-05, CB-07)
1. BR NX-31 FAIL trước / PASS sau; FC NX-34 FAIL trước / PASS sau; FC NX-35 PASS cả hai; AN NX-32 (và NX-33 nếu DR-05a duyệt) PASS sau.
2. `tests/test_tracing.py:71, :121` PASS không sửa (xác nhận validator không nằm trên model `Settings`).
3. Không có lời gọi repository đồng bộ trực tiếp trong `_send_message_async` (review diff).
4. Không log hay response nào chứa URL DB, token hay secret (grep output test + review).
5. Báo cáo nêu rõ mức L1–L5 nào đã có và L4 chưa kiểm được trên hạ tầng thật.
6. **Fix04 gate:** B08 (409 khi đồng thời cùng session), B09, B10, `cancel_enter`, `cancel_model` PASS; không có transaction nào giữ qua model I/O; lease được giải phóng trong mọi đường hủy.

### Gate E — P2/P3 đã chọn (CB-10, 11, 18, 19, 21, 22, 23)
1. BR NX-36, 37, 38, 39, 41, 42, 44: FAIL trước / PASS sau. RC NX-40, 43: PASS cả hai.
2. "Rỗng hợp lệ" chỉ được trả khi backend trả 200 với danh sách rỗng; lỗi hay 404 không được biến thành "rỗng" (trừ cart theo V-3).
3. **Fix02 gate:** chọn đúng mã đơn, không thay bằng đơn khác, không bịa trạng thái payment; `test_order_payment_read.py`, `test_customer_context_mapping.py` PASS; N27/N28 không kém hơn.
4. **Fix04 gate:** read-only guard vẫn tạo `references` (NX-43); N38/N40/N41 không kém hơn.
5. Tài liệu (E7) không ghi số test hay kết quả chưa có output.

### Gate D — UI (CB-08), chỉ khi DR-03 đã duyệt
- Option A → chỉ có thể đạt **UI_LAB_READY**.
- Option B → đánh giá **UI_STOREFRONT_READY** riêng, cần browser test trên bản build production và L4.
- Chưa có DR-03 → Gate D = DEFERRED; không ảnh hưởng verdict của A/B/C/E nhưng chặn UI_STOREFRONT_READY và PRODUCTION_READY.

---

# 11. Các quyết định cần người dùng phê duyệt

**Quy tắc áp dụng cho mọi DR:**
- Mọi DR đang ở trạng thái **PENDING_APPROVAL**. Cột "Đề xuất" chỉ là khuyến nghị của người lập kế hoạch, **không** phải quyền tự động đổi business rule hay public API.
- Coding agent chỉ được triển khai đề xuất của một DR khi DR đó được ghi là **APPROVED**, kèm người duyệt và thời điểm, trong báo cáo Phase 1 hoặc trong prompt Phase 1.
- Khi DR còn PENDING, coding agent chỉ được dùng hành vi ở cột **"Fail-safe khi còn PENDING"**. Hành vi đó không đưa ra quy tắc nghiệp vụ mới: hoặc hỏi lại khách, hoặc giữ nguyên hành vi hiện tại, hoặc không triển khai hạng mục.
- Việc duyệt toàn bộ kế hoạch **không** đồng nghĩa với việc duyệt từng DR.

| Mã | Câu hỏi | Đề xuất (chưa duyệt) | Fail-safe khi còn PENDING | Trạng thái |
|---|---|---|---|---|
| DR-01 | "tầm/khoảng/cỡ X" và "ngân sách X" hiểu thế nào? | "tầm/khoảng" → hỏi lại; "ngân sách X" → MAX X (bao gồm) | Cả hai đều hỏi lại (A2 vẫn chặn việc bỏ qua âm thầm) | PENDING_APPROVAL |
| DR-02 | "thấp hơn/rẻ hơn X" là ≤X hay <X (X−1 VND)? | ≤X, nhất quán với "dưới X" và Fix01 "inclusive" | Hỏi lại ("Bạn muốn giá dưới X đúng không?"); vẫn bắt buộc không sinh `min_price` | PENDING_APPROVAL |
| DR-03 | CB-08: Option A (Lab cho UAT) hay Option B (widget storefront)? Nếu B: hạ tầng reverse proxy production? | Option A trước; Option B là tính năng riêng | Không làm Group D | PENDING_APPROVAL |
| DR-04 | Trần catalog (đề xuất 5000), TTL cache (đề xuất 60s), trần structured search (đề xuất giữ 500) | Như đề xuất, dạng Settings | Giữ trần 500 sản phẩm/100 brand, không cache, không làm B1 (B2 phụ thuộc DR-13b) | PENDING_APPROVAL |
| DR-05a | Thêm public endpoint `GET /api/v1/ready`? | Có | Không thêm endpoint; chỉ L1 + L2 | PENDING_APPROVAL |
| DR-05b | Mặc định `APP_ENV` giữ `development` hay đổi sang `production` (fail-safe)? | Giữ `development` + checklist deploy L4 | Giữ `development` (hành vi hiện tại) | PENDING_APPROVAL |
| DR-06 | Số đơn tối đa hiển thị khi không có mã | 5 | Không làm E2; giữ hành vi hiện tại | PENDING_APPROVAL |
| DR-07 | CB-12: chatbot dùng kho chung hay kho khách đã chọn (`selectedWarehouseId`)? Cách sau cần thêm tham số API | Giữ kho chung trong đợt này; ghi giới hạn | Giữ nguyên | PENDING_APPROVAL |
| DR-08 | CB-13/CB-14: ánh xạ feedback sang `message_feedback` (thang 1–5) hay migration mới; idempotency key (đổi API/schema)? | Hoãn | Hoãn | PENDING_APPROVAL |
| DR-09 | Khi ranking degrade, có thêm câu thông báo cho khách không? | Không đổi text, chỉ ghi event | Không đổi text | PENDING_APPROVAL |
| DR-10 | Những P2/P3 nào đưa vào đợt này | Group E như mục 06 | Chỉ các mục E không phụ thuộc DR khác (E1 order, E3, E4, E5, E6, E7) **khi kế hoạch được duyệt** | PENDING_APPROVAL |
| DR-11 | Mục tiêu đợt này: READY_FOR_MANUAL_UAT hay PRODUCTION_READY? | READY_FOR_MANUAL_UAT, gắn với **UI_LAB_READY**. PRODUCTION_READY cần UI_STOREFRONT_READY, L4 và load test | Nhắm READY_FOR_MANUAL_UAT | PENDING_APPROVAL |
| DR-12 | Với "rẻ hơn X" có snapshot (R2), có áp thêm ceiling từ giá đã hiển thị không? | Không áp ceiling; giá tường minh là điều kiện duy nhất về giá | Không áp ceiling (không thêm điều kiện ẩn); ghi là tạm | PENDING_APPROVAL |
| DR-13a | Chính sách B4 (promotion overlap theo từng loại yêu cầu) cùng văn bản của nó: câu công bố bỏ qua sản phẩm, các thông báo `PRICE_UNVERIFIABLE` | Như bảng chính sách B4 | Không làm B4; giữ 503 hiện tại | PENDING_APPROVAL |
| DR-13b | Văn bản **mới** hiển thị cho khách của A1/A2/A3.1/B2: hỏi lại giá (tầm/khoảng, "thấp hơn X" khi DR-02 chưa duyệt, token giá chưa đọc được), `SCOPE_CHANGED`, `TOO_BROAD` | Văn bản như mục A2, A3.1, B2 (người duyệt có thể sửa câu chữ) | **Không dùng văn bản mới.** A1/A2/A3 dùng lại đúng chuỗi đã có `'Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?'` (`normalization.py:190`). B2 **không triển khai**; giữ 503 hiện tại | PENDING_APPROVAL |

Không có thay đổi schema nào được đề xuất trong đợt này. Thay đổi public API duy nhất được đề xuất là `/api/v1/ready` (DR-05a), và chỉ khi được duyệt.

**Phạm vi phê duyệt.** Duyệt kế hoạch chỉ phê duyệt **cách tiếp cận kỹ thuật** và các hạng mục bên dưới. Kế hoạch không phê duyệt giá trị đề xuất của bất kỳ DR nào, kể cả câu chữ mới hiển thị cho khách (DR-13a/13b).

**Các hạng mục đủ điều kiện chuyển sang Fix ngay khi kế hoạch được duyệt**, không cần DR riêng. Các hạng mục này chỉ sửa lỗi đã xác minh, không thêm quy tắc nghiệp vụ, API hay câu chữ mới:
- A1: phần sửa đảo nghĩa (không bao giờ sinh `min_price` từ dạng so sánh hướng xuống), quy tắc giá 0, các dạng RANGE/"trở xuống"/"trở lên" không mơ hồ. Mọi chỗ cần hỏi lại dùng chuỗi có sẵn ở `normalization.py:190` cho đến khi DR-13b được duyệt.
- A2: coverage guard ở chế độ fail-safe (chuỗi có sẵn).
- A3: trừ lựa chọn ceiling của DR-12 (đã có fail-safe). `SCOPE_CHANGED` dùng chuỗi có sẵn cho đến khi DR-13b được duyệt.
- B3.
- C1 (L1, L2), C2.
- E1 (order), E3, E4, E5, E6, E7.

**Chỉ chuyển sang Fix khi DR tương ứng được duyệt:**
- B1 (DR-04).
- B2 (DR-13b, vì `TOO_BROAD` là văn bản mới).
- B4 (DR-13a + V-2).
- E2 (DR-06).
- `/ready` (DR-05a).
- Group D (DR-03).

---

# 12. Những tính năng tiếp tục hoãn

- **Official Knowledge RAG** (chờ tài liệu chính thức); CB-20 (audit retrieval) đi cùng.
- **Multi-intent orchestration** (T56, N43, N44, N48 vẫn DEFERRED theo thiết kế).
- **Cross-session memory.**
- Connection pool, executor riêng cho Ollama (CB-07 phần mở rộng), tối ưu N+1 detail (CB-15), snapshot query (CB-17), gỡ ContextVar coupling (CB-16): chờ số đo.
- Feedback bền vững (CB-13), idempotency (CB-14), kho theo khách (CB-12).
- Widget storefront (CB-08 Option B) nếu chưa được duyệt.
- Không thay Qwen, FastAPI/Spring, không thêm Redis/queue/microservice, không thêm mutation API.

---

# 13. Risk Register

Xác suất và tác động ghi định tính; không có dữ liệu thống kê.

| Risk | Probability | Impact | Mitigation | Residual Risk | Owner/Decision |
|---|---|---|---|---|---|
| A2 bắt nhầm số model/dung lượng thành tiền, tăng số lần hỏi lại | Trung bình | Trung bình (UX) | Định nghĩa token tiền bắt buộc có đơn vị hoặc đi sau "giá"; NX-09 | Cách viết hiếm gặp | Coding agent / người review |
| A3 làm hỏng luồng tham chiếu Fix04 | Trung bình | Cao | Giữ toàn bộ test Fix04; RC NX-15/17; dừng nếu fail | Cách diễn đạt tham chiếu mới ngoài vocabulary | Người dùng duyệt |
| Cache catalog trả dữ liệu cũ (sản phẩm mới chưa nhận diện) | Trung bình | Thấp–trung bình | TTL ngắn, cấu hình được; chỉ cache dữ liệu đủ completeness | Trễ ≤ TTL | DR-04 |
| Dữ liệu catalog phụ thuộc user nên không thể cache chung | Thấp (chưa xác minh) | Cao nếu bỏ sót | Bước 0 V-1 | — | Bước 0 |
| Validator production chặn môi trường hợp lệ chưa biết | Thấp | Trung bình | Chỉ áp dụng khi env không dev-like; thông báo nêu rõ biến | — | DR-05b |
| `blocking()` thay đổi thời điểm hủy | Thấp | Cao | Test cancellation của Fix04, NX-35 | — | Người review |
| B4 so khớp body 409 theo chuỗi, dễ hỏng khi đổi ngôn ngữ | Trung bình (dài hạn) | Thấp (fallback về 503) | Cùng mẫu Fix02; ghi giới hạn | Cần discriminator ổn định | Theo dõi về sau |
| Không đạt PRODUCTION_READY dù sửa xong Phase 1 (vì CB-08, hạ tầng, load) | Cao | Cao (kỳ vọng) | Tách bốn trạng thái ở mục 14 | — | DR-11 |
| Regression 120 case có case mới chuyển trạng thái | Trung bình | Trung bình | So sánh từng case, giải trình | — | Phase 2 |

---

# 14. Tiêu chí hoàn thành tổng thể

Bốn trạng thái sau **không tương đương**:

| Trạng thái | Định nghĩa | Ai xác nhận |
|---|---|---|
| **CODE_FIX_COMPLETE** | Mọi thay đổi trong phạm vi đã duyệt có trong diff; test mới và test hiện có đạt kèm output; báo cáo Phase 1 hoàn tất | Coding agent (tự báo) |
| **TECHNICAL_ACCEPTANCE_COMPLETE** | Phase 2 verdict PASS hoặc PASS_WITH_CONDITIONS: test mới fail trên HEAD cũ và pass trên HEAD mới; regression đủ 120 case với hash không đổi; fault injection; ownership; fingerprint dữ liệu | Người nghiệm thu độc lập |
| **READY_FOR_MANUAL_UAT** | Như trên, cộng **UI_LAB_READY**: Lab triển khai được ở cấu hình real + postgres + AI, validator không báo lỗi, readiness xanh (nếu có), giới hạn đã công bố cho tester. **Chỉ là UAT nội bộ trên Lab**, không phải chatbot cho khách hàng | Người dùng |
| **PRODUCTION_READY** | Như trên, cộng **UI_STOREFRONT_READY**; hạ tầng L4 (reverse proxy/TLS/cookie/readiness probe/APP_ENV tường minh) đã xác minh trên môi trường thật; load test và quyết định về pooling; các finding DEFERRED liên quan production (CB-13, CB-14, CB-12) đã được quyết định | Người dùng + vận hành |

**Hai trạng thái UI, đánh giá độc lập:**

| Trạng thái | Định nghĩa | Điều kiện | Có thể đạt trong đợt này? |
|---|---|---|---|
| **UI_LAB_READY** | Trang `/chatbot-lab` dùng được cho UAT nội bộ có kiểm soát | Gate A/B/C/E đạt; `ChatbotLab.test.jsx` / `TracePanel.test.jsx` PASS; browser test Lab (gửi, retry, đổi phiên, đóng phiên, ProductCard, nguồn, lỗi 401/403/409/503); nhãn trạng thái mock/real hiển thị đúng; Option A nếu DR-03 chọn A | Có |
| **UI_STOREFRONT_READY** | Khách hàng thật dùng chatbot trên storefront bản build production | DR-03 chọn B; widget đã triển khai; browser test desktop + mobile trên bản build production; reverse proxy cùng origin và CSRF/SameSite đã xác minh (L4); chỉ CUSTOMER thấy widget | **Không**, trừ khi DR-03 chọn B và hạ tầng sẵn sàng |

Đạt UI_LAB_READY hay READY_FOR_MANUAL_UAT **không được** mô tả là "đã có chatbot cho khách hàng" hay "chatbot sẵn sàng production".

Kế hoạch này **không hứa** đạt điểm audit mới hay trạng thái PRODUCTION_READY. Mục tiêu khuyến nghị cho đợt này (DR-11, PENDING) là READY_FOR_MANUAL_UAT, gắn với UI_LAB_READY.

---

# 15. Hai bản tóm tắt cho prompt tiếp theo

## FIX_EXECUTION_BRIEF

- **Baseline:** branch `dev/thai-duy-vu`, HEAD `f99f713`. Ghi lại hash `chatbot-service/debug/post-upgrade-validation/cases.json` trước khi sửa.
- **Bước 0 (chỉ đọc):** V-1 (catalog/brand có phụ thuộc user/role không), V-2 (body 409 promotion), V-3 (`/api/cart` 404/204), V-4 (`useWarehouse`). Kết quả trái giả định thì dừng theo bảng mục 09.
- **Thứ tự sửa:**
  - A1 → A2 → A3
  - B1 → B2 → B3 → B4
  - C1 → C2
  - E1 → E6
  - D (chỉ khi DR-03 đã có)
  - E7 (tài liệu)
- **File được phép:**
  - `chatbot-service/app/features/query_understanding/{normalization.py,constraints.py,ollama_service.py}`
  - `chatbot-service/app/features/conversation/references.py`
  - `chatbot-service/app/features/chat/{service.py,router.py}`
  - `chatbot-service/app/features/product_advisor/{backend.py,real_service.py,semantic.py}`
  - `chatbot-service/app/shared/{config.py,tracing/__init__.py,backend/client.py}`
  - `chatbot-service/app/features/customer_context/service.py`
  - `chatbot-service/app/features/response_generation/ollama_service.py`
  - `chatbot-service/app/main.py` (gọi `validate_runtime_settings` + log L2 trong `lifespan`; `/ready` chỉ khi DR-05a APPROVED)
  - `chatbot-service/tests/**` (test mới; cập nhật có giải trình cho `test_pagination_boundaries.py`, `test_query_integrity.py:213-222`)
  - Tài liệu `LOCAL_SETUP.md`, `RUN_PROJECTS.md`, `chatbot-service/README.md`, `.env.example`
  - Frontend chỉ khi có DR-03.
- **Contract giữ nguyên:** `QueryPlan`, `ProductSearchPlan`, `ProductSelector`, `ChatResponse`, `ProductCard`, các enum; mọi endpoint hiện có; schema/migration; Java; ngữ nghĩa giá inclusive; `ProductEvidence.validate`; fail-closed dung lượng/identity/brand; ownership `/mine`; invariant payment 409 (Fix02); lease/cancellation/completed-turn (Fix04); read-only guard và từ chối giao dịch.
- **Quyết định:** DR-01..DR-13b đều PENDING_APPROVAL. Prompt Phase 1 phải liệt kê DR nào đã APPROVED (kèm giá trị được duyệt). Với DR còn PENDING, **chỉ** dùng cột "Fail-safe khi còn PENDING" ở mục 11:
  - DR-01 → hỏi lại
  - DR-02 → hỏi lại, nhưng không bao giờ sinh `min_price`
  - DR-04 → giữ trần 500/100, không cache, không làm B1
  - DR-05a → không có `/ready`
  - DR-06 → không làm E2
  - DR-09 → không đổi text
  - DR-12 → không áp ceiling
  - DR-13a → không làm B4
  - DR-13b → không dùng văn bản mới; hỏi lại bằng chuỗi có sẵn `normalization.py:190`; không làm B2
  - DR-03 → không làm Group D
- **`QueryPlan.references`:** không thêm khóa hay giá trị mới. R2/R3/R4 trả `{}`. Phân biệt R1–R4 chỉ qua trace event `conversation.reference_resolution`.

  Giá trị đề xuất **không** phải quyền tự động đổi business rule hay public API.
- **Điểm kỹ thuật bắt buộc:**
  - Giá dùng `is None`, không dùng truthiness (giá 0, N06).
  - Validator chạy trong `lifespan`, không đặt trên model `Settings` (giữ `test_tracing.py:71,121`).
  - A3.1 R1–R4 (giá tường minh luôn được giữ; snapshot chỉ bổ sung phạm vi khi câu không nêu phạm vi mới).
  - B4 theo bảng chính sách từng loại yêu cầu; `PRICE_UNVERIFIABLE` không bao giờ thành NOT_FOUND.
- **Stop conditions:** đổi public contract/API/schema; phải sửa frozen expectation; phải nới bảo vệ Fix01/02/04; test Fix04 fail không giải quyết được; phát hiện P0; V-1 phụ thuộc user; cần triển khai đề xuất của một DR còn PENDING.
- **Kết quả mong đợi:** diff theo từng finding (CB-01..07, 09, 10, 11, 18, 19, 21, 22, 23, trong giới hạn DR đã duyệt); 44 test NX-01..NX-44 đã viết, kèm loại BR/RC/AN/FC và trạng thái chạy (case phụ thuộc DR PENDING ghi NOT_RUN_PENDING_DECISION); output test suite lưu tại `chatbot-service/debug/production-readiness/evidence/`; báo cáo `02_FIX_EXECUTION_REPORT.md`; không commit hay push nếu người dùng chưa yêu cầu; trạng thái tối đa tự báo: CODE_FIX_COMPLETE.

## ACCEPTANCE_BRIEF

- **Đầu vào:** diff giữa `f99f713` và HEAD mới; `02_FIX_EXECUTION_REPORT.md`; thư mục evidence.
- **Gate 1 — Phạm vi:** chỉ các file trong FIX_EXECUTION_BRIEF; ánh xạ từng thay đổi về đúng finding; không có thay đổi public contract ngoài `/ready` đã duyệt.
- **Gate 2 — Tính đúng của test (theo loại, 44 case):**
  - **BR (21):** FAIL trên `f99f713` (worktree), PASS trên HEAD mới. BR mà PASS trên HEAD cũ thì không được tính là bằng chứng.
  - **RC (8):** PASS ở cả hai HEAD.
  - **AN (5):** chỉ PASS trên HEAD mới, và chỉ khi DR liên quan đã APPROVED.
  - **FC (10):** theo cột "Trên f99f713" (FAIL: 23, 24, 26, 34; PASS: 25, 30, 35; N/A: 27, 28, 29).
  - Case phụ thuộc DR còn PENDING: NOT_RUN_PENDING_DECISION, không tính PASS.
- **Gate 3 — Suite đầy đủ:** Python có DB thật (0 skip hoặc giải trình); frontend nếu đổi; Java chỉ nếu đổi Java. Mọi tuyên bố phải có output.
- **Gate 4 — Fault/concurrency:** NX-23..30, NX-34, NX-35, B08/B09/B10, `cancel_enter`/`cancel_model` của Fix04.
- **Gate 5 — Bảo mật/dữ liệu:** ownership, B11, fingerprint dữ liệu nghiệp vụ trước/sau, NX-31..33, 41, 42, 44 (không lộ secret; production không âm thầm chạy mock trong phạm vi L1–L3; L4 chỉ kiểm ở mức tài liệu).
- **Gate theo group:** áp dụng mục 10.3 (Gate A/B/C/E kèm Fix01/Fix02/Fix04 gate). Verdict tổng không cao hơn group bắt buộc thấp nhất.
- **Gate 6 — Regression 120 case:** hash `cases.json` không đổi; chạy đủ T01–T60, N01–N48, B01–B12 với Qwen thật + Spring thật + DB clone cô lập; so sánh với baseline 99/8/9/4 từng case; mọi lần từ PASS chuyển sang trạng thái khác phải được giải trình; HTTP 200 không tự động là PASS.
- **Gate 7 — UI, hai verdict riêng:**
  - **UI_LAB_READY:** test component + browser Lab (gửi, retry, đổi phiên, đóng, ProductCard, lỗi).
  - **UI_STOREFRONT_READY:** chỉ đánh giá nếu DR-03 chọn Option B và đã triển khai; nếu không thì ghi DEFERRED, không PASS.
  - Không suy diễn verdict storefront từ verdict Lab.
- **Verdict:** PASS / PASS_WITH_CONDITIONS / FIX_REQUIRED / BLOCKED_CONTRACT / BLOCKED_ENVIRONMENT, cho từng group và tổng thể. Phần DEFERRED không được PASS. Kết luận trạng thái theo mục 14 (tối đa TECHNICAL_ACCEPTANCE_COMPLETE hoặc READY_FOR_MANUAL_UAT gắn với UI_LAB_READY; không tuyên bố PRODUCTION_READY hay "chatbot cho khách hàng" nếu chưa có UI_STOREFRONT_READY và L4).
