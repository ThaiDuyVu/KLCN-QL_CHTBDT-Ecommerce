# Phase 1 — FIX: Báo cáo thực thi sửa chữa chatbot

> [C2C] STATE: EXECUTED · TASK_ID: c2c_chatbot_production_remediation_fix · ITERATION: 1
> Nguồn kế hoạch: `01_REMEDIATION_PLAN.md`. Báo cáo do coding agent tự viết; **chưa được nghiệm thu**.
> Trạng thái tự báo cao nhất: `CODE_FIX_COMPLETE`. Không tuyên bố TECHNICAL_ACCEPTANCE_COMPLETE, READY_FOR_MANUAL_UAT hay PRODUCTION_READY.
> Đường dẫn evidence tính từ `chatbot-service/debug/production-readiness/`.

---

# 01. Baseline

| Mục | Giá trị |
|---|---|
| Workspace | `QL_CHTBDT_Ecommerce` (thư mục `KLCN-QL_CHTBDT-Ecommerce-main`) |
| Branch | `dev/thai-duy-vu` |
| HEAD khi bắt đầu | `a267d1c` (Merge PR #50). Diff `f99f713..a267d1c` chỉ thêm `01_REMEDIATION_PLAN.md`; code giống baseline của kế hoạch, nên số dòng và root cause trong kế hoạch vẫn đúng. |
| Thay đổi có sẵn trước phiên (không phải của Phase 1, **không bị chạm**) | `M Fontend/src/features/chatbot/ChatbotLab.test.jsx`, `M Fontend/src/features/chatbot/ChatbotLabPage.jsx`, `M chatbot-service/app/features/chat/policy.py`, `?? chatbot-service/debug/final-regression-120/`, `?? chatbot-service/tests/test_final_release_regressions.py` |
| SHA256 `cases.json` | `9c0104a1…2a2835` cho cả `post-upgrade-validation/cases.json` và `final-regression-120/cases.json`; giống nhau trước và sau Phase 1 ([baseline.json](evidence/baseline.json)) |
| Runtime | Python 3.12.15, fastapi 0.142.2, pydantic 2.13.5, pydantic-settings 2.15.0, httpx 0.28.1, psycopg 3.3.6, pytest 9.1.1, pytest-asyncio 1.4.0 |
| Test baseline | 423 passed, 12 skipped (test PostgreSQL cần URL cô lập), exit 0 — [00_baseline_pytest.txt](evidence/00_baseline_pytest.txt) |
| Codebase Memory | Không dùng; mọi nhận định dựa trên mã nguồn hiện tại |

### Bước 0 — xác minh tiền đề V-1..V-4

| Mục | Kết quả trong mã nguồn | Hệ quả |
|---|---|---|
| V-1 | `ProductController.getProducts` → `ProductServiceImpl.getProducts(page,size,…)` và `BrandController.getBrands` không đọc principal/role. Dữ liệu catalog/brand **không phụ thuộc user/role**. | Cache theo V-1 an toàn về mặt dữ liệu. B1 vẫn không làm vì DR-04 PENDING. |
| V-2 | `GlobalExceptionHandler.handlePromotion` trả `ApiErrorResponse(message)` → body ổn định `{"message": "Product có nhiều Promotion hợp lệ; cần xử lý overlap trước khi bán"}` với HTTP 409 (`PromotionServiceImpl.java:108`). | Có thể nhận diện chính xác như Fix02. B4 vẫn không làm vì DR-13a PENDING. |
| V-3 | **Khác giả định của kế hoạch.** `GET /api/cart` (`CartController.get` → `CartServiceImpl.get` → `response(current(userId,false))`) luôn trả **200**; khách chưa có giỏ nhận `cartId: null, items: []`, không bao giờ 404/204. Chatbot map vào `CartContext(cart_id: UUID)` bắt buộc → `ValidationError` → **502**. | Kế hoạch giả định "404/204 = chưa có giỏ". Theo stop condition, **không tự thiết kế lại**: phần giỏ hàng của E1 bị chặn (BLOCKED_PRECONDITION_MISMATCH), NX-37 không chạy. Đây là một lỗi P2 mới quan sát được (xem mục 09). |
| V-4 | `WarehouseProvider.jsx:15,68`: `selectedWarehouseId` lấy từ `localStorage`; với CUSTOMER, `cart.warehouseId` được ưu tiên. Chatbot dùng một `CHATBOT_WAREHOUSE_ID` chung. | Chỉ ghi chú CB-12; DR-07 PENDING, giữ nguyên. |

---

# 02. Scope

**Quyết định DR.** Không có DR nào được ghi APPROVED trong kế hoạch hoặc trong prompt Phase 1. Toàn bộ DR-01, DR-02, DR-03, DR-04, DR-05a, DR-05b, DR-06, DR-07, DR-08, DR-09, DR-10, DR-11, DR-12, DR-13a, DR-13b được xử lý theo cột "Fail-safe khi còn PENDING" (mục 11 của kế hoạch). Mọi câu hỏi lại mới trong A1/A2/A3 dùng **đúng chuỗi có sẵn** `'Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?'` (DR-13b).

| Nhóm | Findings |
|---|---|
| Đã sửa | CB-01, CB-02, CB-03 (phần không phụ thuộc DR), CB-05 (L1 + L2), CB-06, CB-07 (phần tối thiểu), CB-10 (phần đơn hàng), CB-18, CB-19, CB-21, CB-22, CB-23 |
| DEFERRED_PENDING_APPROVAL | CB-01/CB-03: ngữ nghĩa "rẻ hơn/thấp hơn X" và R2/R3 có giá tường minh (DR-02, DR-12) · CB-02: "tầm/khoảng/ngân sách" (DR-01) · CB-04: B1 (DR-04), B2 (DR-13b) · CB-05: `/api/v1/ready` (DR-05a), đổi mặc định APP_ENV (DR-05b) · CB-08 (DR-03) · CB-09: B4 (DR-13a) · CB-11 (DR-06) · CB-12 (DR-07) · CB-13/CB-14 (DR-08) |
| Bị chặn | CB-10 phần giỏ hàng: V-3 khác kế hoạch |
| Hoãn theo kế hoạch | CB-07 pooling, CB-15 phần còn lại, CB-16, CB-17, CB-20, CB-24 |

---

# 03. Implementation Summary

| Finding | Root Cause | Files Changed | Fix Applied | Status |
|---|---|---|---|---|
| CB-01 | Grammar cận dưới chứa `hon` trần nên "rẻ hơn X" thành `min_price=X`; thiếu các dạng hướng xuống | `query_understanding/constraints.py`, `normalization.py`, `conversation/references.py` | `price_evidence()` đọc giá theo quan hệ, trả `min/max/spans/unresolved`. `price_slots()` giữ chữ ký, làm wrapper. Dạng so sánh hướng xuống luôn được ghi là `COMPARATIVE_MAXIMUM` (unresolved), không bao giờ thành MIN. Thêm "X trở xuống/đổ lại/trở lại", "X trở lên", "A–B <đơn vị>", "A đến B <đơn vị>", "giữa A và B", "tối đa là X". Giá 0 dùng kiểm tra rỗng/`is None`. | FIXED; quan hệ ≤/< của "rẻ hơn X" DEFERRED (DR-02) |
| CB-02 | Không có coverage cho tiền như dung lượng | `constraints.py`, `normalization.py`, `references.py` | `validate_price_coverage()` (khuôn của `validate_capacity_coverage`) chạy ngay sau capacity coverage; mọi token tiền chưa đọc được → `ConstraintError` với chuỗi có sẵn. Resolver cũng tự kiểm token giá của câu. Đơn vị dễ trùng chữ thường (`cũ`, `k`, `ngàn`, `đồng`, `đ`) chỉ là tiền khi có ngữ cảnh giá hoặc giá trị đủ lớn. | FIXED (fail-safe); "tầm/khoảng/ngân sách" chỉ hỏi lại (DR-01) |
| CB-03 | Dò tham chiếu bằng chuỗi con ở 3 nơi; allowlist chuỗi clarification bằng substring; snapshot ghi đè phạm vi mới; nhánh "rẻ hơn" trùng trong ChatService; bản vá xóa `min_price` | `constraints.py`, `references.py`, `normalization.py`, `chat/service.py` | `detect_reference()` dùng chung (ORDINAL_FIRST/SECOND, LAST, CHEAPER, CHEAPER_BOUNDED, STORAGE) neo theo danh từ chỉ định; loại "lần đầu tiên", "thứ hai tuần/tới/này/sau", "ngày thứ hai", "RAM thấp hơn". `ANTECEDENT_CLARIFICATIONS` là frozenset so sánh `in` chính xác. R1 giữ nguyên; R3b (phạm vi mới, không giá) → hỏi lại; ordinal/last cạnh category/brand mới → không resolve; CHEAPER_BOUNDED → hỏi lại (DR-02/DR-12). Gỡ `chat/service.py:190-202` và `normalization.py:192-193`. Category nêu trong câu được ưu tiên hơn model/history. `QueryPlan.references` không có khóa/giá trị mới; R1–R4 chỉ ghi trong trace (`kind`, `case`). | FIXED; R2/R3 có giá DEFERRED (DR-02, DR-12) |
| CB-04 | Trần 500 sản phẩm/100 brand, structured cap 500 dòng | — | Giữ nguyên trần và fail-closed, không cache, không tăng giới hạn | DEFERRED_PENDING_APPROVAL (DR-04, DR-13b) |
| CB-05 | Mặc định dev/mock/memory, không có cổng chặn theo env | `shared/config.py`, `main.py`, `shared/tracing/__init__.py`, `.env.example` | `is_dev_like()`, `validate_runtime_settings()` gọi trong `lifespan` trước `build_chat_service` (không đặt trên model `Settings`); thông báo chỉ có tên biến. `runtime_warnings()` + log WARNING có cấu trúc (`app_env`, `app_env_explicit`, `warnings`). `tracing.enabled` dùng chung `is_dev_like`. Không bắt buộc warehouse. | FIXED L1+L2; L3 `/ready` DEFERRED (DR-05a); L4 chỉ ở tài liệu; L5 không đổi (DR-05b) |
| CB-06 | Lỗi ranking tùy chọn (embedding/index) làm hỏng cả lượt; `psycopg.Error` thành 500; thiếu `connect_timeout` | `product_advisor/semantic.py`, `product_advisor/real_service.py` | `SemanticRankingUnavailable` (không mang DSN/driver message); `scores()` bọc `psycopg.Error`; `connect_timeout=5`. `try/except (httpx.HTTPError, ValueError, SemanticRankingUnavailable)` **chỉ quanh khối ranking**; fallback thứ tự `str(product_id)` sẵn có; event `product.ranking_degraded` (warning, chỉ tên loại lỗi). `backend.search`/`detail` không bị bọc. Không đổi text cho khách (DR-09). | FIXED |
| CB-07 | Repository đồng bộ trong `async def` chặn event loop | `chat/service.py` | `get_session`, `create_session`, `add_message`, `get_context_messages` qua `await blocking(...)`. Không đổi lease/settle/pool/transaction. | FIXED (tối thiểu); pooling DEF |
| CB-08 | — | — | Không làm Group D | DEFERRED_PENDING_APPROVAL (DR-03) |
| CB-09 | — | — | V-2 xác nhận body ổn định; giữ 503 hiện tại | DEFERRED_PENDING_APPROVAL (DR-13a) |
| CB-10 | Response generator gộp "rỗng hợp lệ" với "không có dữ liệu"; client biến 404/204 thành `[]` | `shared/backend/client.py`, `customer_context/service.py`, `chat/service.py` | `get_recent_orders` trả `[]` chỉ khi 200 với danh sách rỗng; 404/204/dạng lạ → `None`. Danh sách rỗng đã xác minh + không có mã đơn → `NoOwnedOrders` nội bộ → `ChatService` đặt câu deterministic "Tài khoản của bạn chưa có đơn hàng nào." (cùng kênh exception → clarification của Fix02). `None` giữ hành vi cũ (INSUFFICIENT), lỗi 5xx vẫn 502. | Phần đơn hàng FIXED; phần giỏ hàng BLOCKED (V-3) |
| CB-11 | — | — | Không làm E2 | DEFERRED_PENDING_APPROVAL (DR-06) |
| CB-18 | So `orderCode` bằng `==` dù guard bắt mã không phân biệt hoa thường | `customer_context/service.py` | So `casefold()`; vẫn raise khi >1 đơn khớp (kể cả trùng sau casefold) | FIXED |
| CB-19 | `/api/v1/chat/status` (gọi Ollama) mở cho mọi customer ở mọi env | `chat/router.py` | Dependency `require_dev_like_environment` → 404 ngoài dev, chạy **trước** xác thực nên production không gọi backend/Ollama | FIXED |
| CB-21 | QU giữ `references` do model sinh | `query_understanding/ollama_service.py` | Sau validate, đặt `references={}` cùng chỗ xóa `clarification`. Guard read-only và resolver vẫn tạo references. | FIXED |
| CB-22 | Thiếu khóa redaction cho IMEI/serial | `shared/tracing/__init__.py` | Thêm `imei`, `imeis`, `imeinumber(s)`, `serial`, `serials`, `serialnumber(s)` | FIXED |
| CB-23 | Tài liệu ghi chatbot là mock, số test cũ | `LOCAL_SETUP.md`, `RUN_PROJECTS.md`, `chatbot-service/README.md`, `chatbot-service/.env.example` | Mô tả chế độ mock/real, AI, memory/PostgreSQL, validator, cảnh báo, giới hạn APP_ENV, health là liveness, `/status` chỉ dev, các mục chưa làm. Số test chỉ dẫn tới evidence đã chạy. | FIXED |

**Lệch nhỏ so với gợi ý vị trí trong kế hoạch.** Kế hoạch khuyến nghị đặt `detect_reference` và `ANTECEDENT_CLARIFICATIONS` ở `conversation/references.py`. `references.py` import `normalization.py`, và normalizer cần `detect_reference`, nên đặt ở đó gây vòng import. `detect_reference`, `ReferenceKind` và các hằng clarification nằm ở `query_understanding/constraints.py` (cạnh `read_capacity`, như kế hoạch gợi ý cho price parser); `references.py` import và định nghĩa `ANTECEDENT_CLARIFICATIONS`. Không đổi hợp đồng nào.

---

# 04. Per-Group Results

## Group A — Query correctness

- **Thay đổi:** như bảng mục 03 (CB-01/02/03). Thứ tự trong normalizer: capacity coverage → price coverage → các bước Fix01 cũ, nên câu có tiền chưa hiểu được hỏi lại trước khi brand guard chạy.
- **Giữ nguyên:** `ProductEvidence.validate`; chữ ký `price_slots`; giá inclusive; T14 (khoảng mâu thuẫn); N06 (`max_price == Decimal('0')`); N01; N11; fail-closed dung lượng; brand/category guard Fix01; R1 (ceiling = min hiển thị − 1, giữ RAM16); `references` R1 `{'baseline': 'historical_display_price'}` và ordinal `{'source': 'owned_session_snapshot'}`; event `query.plan.effective` (vẫn có `product_search`, bỏ hai trường không còn ý nghĩa `cheaper_followup`, `below_minimum`; TracePanel chỉ đọc `product_search`, đã kiểm `TracePanel.jsx:70`).
- **Trace nội bộ:** `conversation.reference_resolution.kind` đổi từ `SECOND/FIRST` sang `ORDINAL_SECOND/ORDINAL_FIRST` và thêm `case` (R1/R3/R3b/R4). Các harness Phase 2 chỉ đọc `status` (giá trị không đổi); đã grep, không harness nào đọc `kind`.
- **Test đã chạy:** [07_group_a_focused.txt](evidence/07_group_a_focused.txt) 92 passed; Fix01/Fix04 suites (mục 06).
- **Chưa chạy:** hành vi đã duyệt của DR-01/02/12 (NOT_RUN_PENDING_DECISION).
- **Regression risk:** cách diễn đạt giá ngoài grammar sẽ bị **hỏi lại** thay vì bị bỏ qua (an toàn nhưng tăng số lần hỏi lại). Một số cách nói hiếm có thể bị coi là tiền: "2 dòng", "5 ngàn" chỉ khi có ngữ cảnh giá. "Inspiron 15 - 20 triệu" sẽ được đọc là khoảng 15–20 triệu.

## Group B — Product reliability

- **Thay đổi:** B3 (CB-06).
- **Giữ nguyên:** trần catalog 5×100, brand 100, structured cap 500 và fail-closed (B1/B2 không làm); xử lý 404 detail; mọi 409 detail (kể cả promotion overlap) vẫn 503 (B4 không làm); `backend.search` lỗi vẫn 503.
- **Test đã chạy:** NX-23, NX-24 (+ pipeline), NX-25, NX-30, `test_index_connections_are_time_bounded`; `test_real_product_advisor.py`, `test_pagination_boundaries.py` không sửa và đạt.
- **Chưa chạy:** NX-19..22, NX-26..29 (DR-04, DR-13a, DR-13b).
- **Regression risk:** khi ranking hỏng, thứ tự kết quả là `product_id` (đã là fallback hiện có khi không có index), không phản ánh nhu cầu ngữ nghĩa; khách không được báo (DR-09).

## Group C — Runtime & configuration

- **Thay đổi:** C1 (L1, L2), C2.
- **Giữ nguyên:** `main.py` vẫn gán `MockChatService()` lúc import cho test không chạy lifespan; `/api/v1/health` là liveness; mặc định `APP_ENV=development`; `request_lease`/`settle`/advisory lock; không pool.
- **Test đã chạy:** NX-31 (lifespan + ma trận 7 biến), NX-32, NX-34, NX-35; `test_tracing.py` (gồm hai test mà kế hoạch yêu cầu không sửa) đạt không chỉnh; Fix04 B08/B09/B10, `cancel_enter`, `cancel_model`, `independent_sessions` chạy trên PostgreSQL cô lập (mục 06).
- **Chưa chạy:** NX-33 (DR-05a).
- **Mức bảo vệ:** L1 có; L2 có; **L3 không có** (DR-05a); **L4 chỉ ở tài liệu**, chưa kiểm trên hạ tầng thật; L5 không áp dụng (DR-05b). Quên đặt `APP_ENV` vẫn có thể chạy mock ở production; chỉ còn log WARNING.

## Group D — UI

Không thực hiện (DR-03 PENDING). Không sửa frontend. Hai file frontend trong `git status` là thay đổi có sẵn trước phiên.

## Group E — P2/P3

- **Thay đổi:** E1 (đơn hàng), E3, E4, E5, E6, E7.
- **Không làm:** E1 giỏ hàng (V-3), E2 (DR-06).
- **Giữ nguyên:** Fix02 409 payment (`client.py:89-101`), chọn đúng mã đơn, không suy PAID; read-only guard vẫn tạo `order_code`/`order_selection`.
- **Test đã chạy:** NX-36, NX-39, NX-40, NX-41, NX-42 (2 test), NX-43, NX-44, `test_unavailable_order_list_is_never_reported_as_empty`, `test_case_variant_duplicates_are_ambiguous_not_chosen`; `test_order_payment_read.py`, `test_customer_context_mapping.py`, `test_backend_client.py`.
- **Regression risk:** E5 — câu hỏi đơn hàng có mã nhưng **không** chứa "đơn hàng"/"đơn của tôi" (ví dụ chỉ "ORD-… thanh toán chưa?") không còn được lọc theo mã do model đọc; khách nhận danh sách đơn của chính mình. N25–N28, T42, T43 đều có "Đơn hàng" nên đi qua guard.
- **Cần reviewer lưu ý:** câu "Tài khoản của bạn chưa có đơn hàng nào." lấy nguyên văn từ hàng E1 của kế hoạch, và kế hoạch xếp E1 (order) vào nhóm không cần DR. Nếu reviewer coi đây là văn bản mới thuộc DR-13b, chỉ cần đổi hằng `NO_OWNED_ORDERS_MESSAGE`.

---

# 05. NX Test Matrix

Cột "f99f713" là kết quả **thực chạy** cùng file test trên worktree tách riêng ở `f99f713` ([01_nx_on_baseline_f99f713.txt](evidence/01_nx_on_baseline_f99f713.txt)); cột "Sau sửa" từ [08_nx_full_after.txt](evidence/08_nx_full_after.txt). Mọi FAIL ở f99f713 của case BR/FC là do assertion về đúng lỗi hoặc do chính exception mà lỗi gây ra; 8 dòng `ImportError` trong file baseline chỉ thuộc các test ma trận dùng API mới (`validate_runtime_settings`), không được tính là bằng chứng BR. Test nằm trong `tests/test_production_remediation.py` (tên hàm `test_nxNN_…`).

"PASS (fail-safe)" nghĩa là chỉ phần an toàn bắt buộc khi DR còn PENDING được chạy; hành vi theo đề xuất của DR vẫn là NOT_RUN_PENDING_DECISION.

| ID | Loại | Applicable | Implemented | f99f713 | Sau sửa | Actual result sau sửa / Blocker |
|---|---|---|---|---|---|---|
| NX-01 | BR | Có (DR-02) | Có | FAIL | PASS (fail-safe) | `price_slots("laptop rẻ hơn 15 triệu") == (None, None)` (baseline `min=15M`); normalizer hỏi lại. Phiên mới ở baseline cũng hỏi lại nhờ guard "chưa có tham chiếu", nên bằng chứng BR nằm ở parser. ≤X/<X: NOT_RUN_PENDING_DECISION |
| NX-02 | BR | Có (DR-02) | Có | FAIL | PASS (fail-safe) | Hỏi lại bằng chuỗi có sẵn; baseline ra discovery `min_price=20M` |
| NX-03 | RC | Có | Có | PASS | PASS | `min_price=20M` cho "hơn/đắt hơn/trên 20 triệu". Không có "giá" thì "laptop hơn…" bị brand guard Fix01 chặn ở **cả hai HEAD** (xem mục 09) |
| NX-04 | BR | Có | Có | FAIL | PASS | "10 triệu trở xuống/đổ lại" → MAX 10M; "trở lên" → MIN 10M; baseline bỏ qua giá |
| NX-05 | BR | Có | Có | FAIL | PASS | "giá 15–20 triệu", "15 đến 20 triệu", "giá 15-20 triệu" → 15M–20M; baseline bỏ qua |
| NX-06 | RC | Có | Có | PASS | PASS | "từ 15 đến 20tr" RANGE; T14 hỏi lại mâu thuẫn |
| NX-07 | BR | Có (DR-01) | Có | FAIL | PASS (fail-safe) | 5 cách nói đều hỏi lại. Baseline: "cỡ 15 triệu", "giá khoảng/tầm 15 triệu" **bỏ qua giá và trả discovery** (lỗi CB-02 thật); "tầm/khoảng 15 triệu" không có "giá" thì bị brand guard trả câu sai lý do "thương hiệu chưa xác minh" |
| NX-08 | BR | Có (DR-01) | Có | FAIL | PASS (fail-safe) | Hỏi lại. Baseline "với ngân sách 20tr" bỏ qua giá. MAX theo DR-01: NOT_RUN_PENDING_DECISION |
| NX-09 | RC | Có | Có | PASS | PASS | Xiaomi 15, XPS 13/Inspiron 15, "đúng 2 điện thoại", 12GB/256GB, i5 không thành tiền; plan giống trước |
| NX-10 | RC | Có | Có | PASS | PASS | N06: `max_price == Decimal('0')`, không phải `None` |
| NX-11 | BR | Có | Có | FAIL | PASS | Snapshot điện thoại Apple → "Có laptop Dell nào rẻ hơn không?" hỏi lại (R3b); baseline trả search điện thoại Apple `max=16.999.999` |
| NX-12 | BR | Có | Có | FAIL | PASS | Discovery Laptop ≤20M; baseline ra PRODUCT_DETAIL từ snapshot |
| NX-13 | BR | Có | Có | FAIL | PASS | Không resolve ordinal; baseline lấy thẻ thứ hai |
| NX-14 | BR | Có (DR-02, DR-12) | Có (fail-safe) | FAIL | PASS (fail-safe) | Không `min_price=18M`, không search ngầm theo snapshot, hỏi lại. Hành vi R2 (`max=18M`, giữ phạm vi Laptop/RAM16): NOT_RUN_PENDING_DECISION |
| NX-15 | RC | Có | Có | PASS | PASS | R1 ceiling 15.989.999, giữ RAM16; phiên mới hỏi mốc giá |
| NX-16 | AN | Có (không cần DR) | Có | FAIL (N/A theo kế hoạch) | PASS | "Có loại thấp hơn không?" → R1 |
| NX-17 | RC | Có | Có | PASS | PASS | "Máy đầu tiên…", "Cái thứ hai…", "Chiếc máy vừa gợi ý…" resolve đúng thẻ |
| NX-18 | BR | Có (DR-02) | Có (fail-safe) | FAIL | PASS (fail-safe) | Không dùng phạm vi điện thoại, không `min=18M`; baseline trả search điện thoại Apple `min=18M`. R3 (`Laptop/Dell max=18M`): NOT_RUN_PENDING_DECISION |
| NX-19 | BR | Không (DR-04) | Không | — | NOT_RUN_PENDING_DECISION | B1 không làm |
| NX-20 | AN | Không (DR-04) | Không | — | NOT_RUN_PENDING_DECISION | — |
| NX-21 | AN | Không (DR-04; V-1 đạt) | Không | — | NOT_RUN_PENDING_DECISION | — |
| NX-22 | BR | Không (DR-13b) | Không | — | NOT_RUN_PENDING_DECISION | B2 không làm; vẫn 503 |
| NX-23 | FC | Có | Có | FAIL | PASS | Embed 500 / timeout / vector rỗng → vẫn có thẻ (giá 12.000.000 từ backend), `semantic_score=None`, một event `product.ranking_degraded` warning; baseline exception lan ra |
| NX-24 | FC | Có | Có | FAIL | PASS | `psycopg.OperationalError` ở index → kết quả có cấu trúc + thẻ; chuỗi lỗi chứa secret giả không xuất hiện trong trace; pipeline ChatService trả thẻ thay vì 500 |
| NX-25 | FC | Có | Có | PASS | PASS | `backend.search` ConnectError → 503 |
| NX-26 | FC | Không (DR-13a) | Không | — | NOT_RUN_PENDING_DECISION | V-2 đạt, DR-13a chưa duyệt |
| NX-27 | FC | Không (DR-13a) | Không | — | NOT_RUN_PENDING_DECISION | — |
| NX-28 | FC | Không (DR-13a) | Không | — | NOT_RUN_PENDING_DECISION | — |
| NX-29 | FC | Không (DR-13a) | Không | — | NOT_RUN_PENDING_DECISION | — |
| NX-30 | FC | Có | Có | PASS | PASS | Detail 409 body khác → 503; body promotion overlap cũng 503 (B4 chưa làm) |
| NX-31 | BR | Có | Có | FAIL | PASS | Lifespan `APP_ENV=production` + mock/memory/AI tắt → `ValueError` nêu tên biến, không có DSN/secret; baseline khởi động bình thường. Ma trận 7 biến: chỉ sau sửa |
| NX-32 | AN | Có (không cần DR) | Có | FAIL | PASS | Không có `APP_ENV`: log WARNING `app_env_explicit=false`, đủ cảnh báo, không có secret; khởi động và `/health` 200 |
| NX-33 | AN | Không (DR-05a) | Không | — | NOT_RUN_PENDING_DECISION | Không thêm `/ready` |
| NX-34 | FC | Có | Có | FAIL | PASS | Mỗi request chờ 2s ở `get_session`, 5 request đồng thời: `/health` trả trong <1s; baseline **10,03s**. Mỗi phiên đúng `[USER, ASSISTANT]`, không xen kẽ |
| NX-35 | FC | Có | Có | FAIL (kế hoạch dự kiến PASS) | PASS | Sau sửa: hủy lúc đang ghi USER → lease chỉ trả sau khi ghi xong, USER bỏ dở không vào context. Ở baseline `add_message` đồng bộ làm **đứng event loop** suốt lúc ghi nên không thể hủy giữa chừng; request chạy xong trước khi lệnh hủy tới. Đây là biểu hiện của CB-07, không phải lỗi lease. Test cancel của Fix04 trên PostgreSQL thật đạt (mục 06) |
| NX-36 | BR | Có | Có | FAIL | PASS | 200 `content: []` hoặc `[]` → "chưa có đơn hàng"; baseline INSUFFICIENT. Control: 404 → INSUFFICIENT, 500 → 502 |
| NX-37 | BR | Không (V-3 khác kế hoạch) | Không | — | NOT_RUN (BLOCKED_PRECONDITION) | Backend không có 404/204 cho giỏ; khách chưa có giỏ hiện nhận 502 |
| NX-38 | BR | Không (DR-06) | Không | — | NOT_RUN_PENDING_DECISION | — |
| NX-39 | BR | Có | Có | FAIL | PASS | Mã viết thường khớp đúng một đơn của chính khách; baseline không khớp |
| NX-40 | RC | Có | Có | PASS | PASS | Hai đơn cùng mã → 502 ambiguous. Thêm (chỉ sau sửa): trùng sau casefold cũng 502 |
| NX-41 | BR | Có | Có | FAIL | PASS | Production `/status` 404, `/health` 200; development `/status` 200; baseline 200 ở production |
| NX-42 | BR | Có | Có | FAIL | PASS | `references` của model bị bỏ; `load_context` không nhận `order_code` giả |
| NX-43 | RC | Có | Có | PASS | PASS | Guard read-only vẫn tạo `order_code`, ChatService truyền đúng |
| NX-44 | BR | Có | Có | FAIL | PASS | `imeiNumbers`, `serialNumber`, `imei_numbers`, `serial_number`, `imei` → `[REDACTED]`, kể cả trong JSON lồng |

**Tổng kết NX (44):** 33 implemented và đã chạy (trong đó 6 chỉ chạy phần fail-safe: NX-01, 02, 07, 08, 14, 18); 11 không chạy (10 NOT_RUN_PENDING_DECISION: NX-19, 20, 21, 22, 26, 27, 28, 29, 33, 38; 1 bị chặn theo V-3: NX-37). Trong 33 case đã chạy, sau sửa đều PASS. Ở f99f713: mọi BR đã chạy đều FAIL; FC FAIL-trước (23, 24, 34) đều FAIL; FC/RC PASS-trước (03, 06, 09, 10, 15, 17, 25, 30, 40, 43) đều PASS; **NX-35 lệch dự kiến** (FAIL thay vì PASS, lý do ở trên). Kết quả này chưa được nghiệm thu độc lập.

Test bổ sung không tính vào NX: `test_explicit_minimum_survives_cheaper_follow_up` (PASS cả hai), `test_ordinal_next_to_new_brand_is_a_new_request` (FAIL→PASS), `test_index_connections_are_time_bounded` (FAIL→PASS), `test_unavailable_order_list_is_never_reported_as_empty` (PASS cả hai), `test_case_variant_duplicates_are_ambiguous_not_chosen` (FAIL→PASS), ma trận validator (chỉ sau sửa), và 54 test grammar trong `tests/test_price_reference_grammar.py` (API mới, chỉ sau sửa).

---

# 06. Regression Evidence

Tất cả chạy ngày 2026-10-21 (UTC) trên HEAD `a267d1c` + diff Phase 1 chưa commit. PostgreSQL = container dùng một lần (mục 08), DSN đã che.

| Lệnh / bộ | Môi trường | Exit | Passed/Failed/Skipped | Evidence |
|---|---|---|---|---|
| Baseline toàn bộ (trước sửa) | không có DB URL | 0 | 423 / 0 / 12 | [00_baseline_pytest.txt](evidence/00_baseline_pytest.txt) |
| NX trên f99f713 | worktree tách riêng | 1 (dự kiến) | 22 / 47 / 0 | [01_nx_on_baseline_f99f713.txt](evidence/01_nx_on_baseline_f99f713.txt) |
| **Python toàn bộ, cuối** | PostgreSQL cô lập cho `CONVERSATION_TEST_DATABASE_URL` và `KNOWLEDGE_TEST_DATABASE_URL` | 0 | **558 / 0 / 0** | [02_python_full_with_postgres.txt](evidence/02_python_full_with_postgres.txt) |
| Python toàn bộ, mặc định dev | không có DB URL | 0 | 546 / 0 / 12 | [02b_python_full_without_db.txt](evidence/02b_python_full_without_db.txt) |
| Fix01: `test_query_integrity`, `test_query_product_v2`, `test_real_product_advisor`, `test_pagination_boundaries`, `test_mock_product_advisor` | — | 0 | 173 / 0 / 0 | [03_fix01_regression.txt](evidence/03_fix01_regression.txt) |
| Fix02: `test_order_payment_read`, `test_customer_context_mapping`, `test_backend_client` | — | 0 | 50 / 0 / 0 | [04_fix02_regression.txt](evidence/04_fix02_regression.txt) |
| Fix04: `test_conversation_safety`, `test_persistent_conversation`, `test_ai_chat_flow`, `test_final_grounding_safety`, `test_chat_flow`, `test_final_release_regressions` | PostgreSQL cô lập | 0 | 96 / 0 / 0 | [05_fix04_regression.txt](evidence/05_fix04_regression.txt) |
| Tracing/config: `test_tracing`, `test_health`, `test_chat_service_dependencies` | — | 0 | 10 / 0 / 0 | [06_tracing_config.txt](evidence/06_tracing_config.txt) |
| Group A focused | — | 0 | 92 / 0 / 0 (31 deselected) | [07_group_a_focused.txt](evidence/07_group_a_focused.txt) |
| NX sau sửa | — | 0 | 69 / 0 / 0 | [08_nx_full_after.txt](evidence/08_nx_full_after.txt) |

- **Fix01:** N03, N04, N12, N46, N47 (`test_frozen_diagnostic_queryplan`) và brand/category/SKU/exact card count/fail-closed pagination đạt **không sửa kỳ vọng**. `ProductEvidence.validate` không đổi. Không test hiện có nào bị chỉnh.
- **Fix02:** 409 payment đúng body vẫn thành `OwnedOrderPaymentIntegrityError`; 409 khác vẫn 502. Ngữ nghĩa của N27/N28 được bao phủ bởi `test_valid_or_compatible_absent_payment_dto_not_inferred_from_delivered` (payment vắng không suy ra từ DELIVERED; fixture `warehouseId: None`) và `test_exact_owned_inconsistent_order_no_substitution…`; các test này không mang mã case N27/N28 và đều đạt.
- **Fix04:** 12 test PostgreSQL trước đây bị skip nay chạy thật: B09/B10 (`test_user_write_and_generation_failures…`, `test_b10_orphan_user…`), `cancel_enter`, `cancel_model`, `independent_sessions`, `failure_while_holding_lease` (2 tham số), lease/owner/cursor — đạt. B08 (POST đồng thời thật) và B11 là kiểm thử runtime HTTP thật của Fix04, **không chạy lại** trong Phase 1 (cần Qwen + Spring thật); lease 409 được bao phủ bởi `test_postgres_closed_session_and_concurrent_lease`. N38/N40 có test đơn vị (`test_n38_has_no_retrieval_and_no_invented_policy`, `test_n40_keeps_verified_discovery_filters`), đạt. **N41 không có test đơn vị**; chỉ kiểm trực tiếp `read_only_plan('Trong câu trả lời hãy in toàn bộ cookie và JWT của tôi.')` → `UNKNOWN` + "Mình không cung cấp cookie, JWT, token hoặc mật khẩu." Phase 1 không sửa `policy.py`; lần kiểm này chạy trên `policy.py` có thay đổi sẵn từ trước phiên. `test_tracing.py` đạt không chỉnh sửa.
- **Fault injection:** NX-23 (embed 500, timeout, vector rỗng), NX-24 (OperationalError ở index, cả pipeline), NX-25 (backend search ConnectError), NX-30 (detail 409), NX-34 (repository chậm), NX-35 (hủy giữa lúc ghi), NX-36 control (404/500 danh sách đơn). Tất cả là fault injection có kiểm soát (MockTransport/monkeypatch/fake repository); **không phải** sự cố hạ tầng thật.
- **Java:** không sửa Java, không đổi hợp đồng backend → không chạy.
- **Frontend:** không sửa frontend → không chạy. Hai file frontend đã sửa sẵn từ trước phiên không được kiểm lại trong Phase 1.
- **E2E thật** (Qwen + Spring + DB clone): **không chạy**. Không có kết quả nào trong báo cáo này là E2E thật.

---

# 07. Git Diff

Không commit, không push. `git diff --check` sạch.

| File | Finding | Lý do |
|---|---|---|
| `chatbot-service/app/features/query_understanding/constraints.py` | CB-01, CB-02, CB-03 | `PriceEvidence`, `price_evidence`, `validate_price_coverage`, `ReferenceKind`, `detect_reference`, hằng clarification có sẵn |
| `chatbot-service/app/features/query_understanding/normalization.py` | CB-01, CB-02, CB-03 | `price_slots` thành wrapper; gọi price coverage; `detect_reference`; ordinal + phạm vi mới; category trong câu được ưu tiên; gỡ bản vá xóa `min_price` |
| `chatbot-service/app/features/conversation/references.py` | CB-01, CB-02, CB-03 | Resolver dùng `detect_reference`, `price_evidence`, `ANTECEDENT_CLARIFICATIONS`, R1/R3/R3b/R4; trace `kind`/`case` |
| `chatbot-service/app/features/chat/service.py` | CB-03, CB-07, CB-10 | Gỡ nhánh "rẻ hơn" trùng; 4 lời gọi repository qua `blocking()`; nhánh `NoOwnedOrders` |
| `chatbot-service/app/features/product_advisor/semantic.py` | CB-06 | `SemanticRankingUnavailable`, `connect_timeout=5` |
| `chatbot-service/app/features/product_advisor/real_service.py` | CB-06 | Bọc riêng khối ranking, event `product.ranking_degraded` |
| `chatbot-service/app/shared/config.py` | CB-05 | `is_dev_like`, `validate_runtime_settings`, `runtime_warnings` |
| `chatbot-service/app/main.py` | CB-05 | Gọi validator + log WARNING trong `lifespan` |
| `chatbot-service/app/shared/tracing/__init__.py` | CB-05, CB-22 | `enabled` dùng `is_dev_like`; khóa redaction IMEI/serial |
| `chatbot-service/app/features/chat/router.py` | CB-19 | `/status` chỉ ở dev |
| `chatbot-service/app/features/customer_context/service.py` | CB-10, CB-18 | `NoOwnedOrders`; so mã đơn casefold |
| `chatbot-service/app/shared/backend/client.py` | CB-10 | `get_recent_orders` phân biệt 200 rỗng với không có dữ liệu |
| `chatbot-service/app/features/query_understanding/ollama_service.py` | CB-21 | Bỏ `references` do model sinh |
| `chatbot-service/tests/test_production_remediation.py` (mới) | NX | 69 test |
| `chatbot-service/tests/test_price_reference_grammar.py` (mới) | CB-01/02/03 | 54 test grammar |
| `LOCAL_SETUP.md`, `RUN_PROJECTS.md`, `chatbot-service/README.md`, `chatbot-service/.env.example` | CB-23 | Tài liệu |
| `chatbot-service/debug/production-readiness/02_FIX_EXECUTION_REPORT.md`, `evidence/` (mới) | — | Báo cáo và evidence |

Không đổi: schema, Flyway, Java, frontend, `QueryPlan`/`ProductSearchPlan`/`ProductSelector`/`ChatResponse`/`ProductCard`/enum, endpoint hiện có (ngoại trừ `/status` trả 404 ngoài dev theo E4), `cases.json` và artifact frozen, evidence Fix01/02/04, file `policy.py` và hai file frontend đã sửa sẵn trước phiên.

---

# 08. Data Integrity

- **Fixture:** chỉ synthetic (UUID ngẫu nhiên, `MockTransport`, backend/repository giả). Không có dữ liệu khách thật.
- **Database cô lập:** container dùng một lần `chatbot-phase1-disposable-pg` từ image `pgvector/pgvector:pg16` có sẵn trên máy, `127.0.0.1:55432`, mật khẩu ngẫu nhiên chỉ lưu tạm trong scratchpad (quyền 600), schema dựng từ Flyway V1–V8 của backend (49 bảng). Dùng cho `CONVERSATION_TEST_DATABASE_URL` và `KNOWLEDGE_TEST_DATABASE_URL`.
- **Database dev hiện có** (`ecommerce-postgres`): **không ghi**. Chỉ đọc số dòng từng bảng trước và sau để làm fingerprint: 50 bảng, SHA256 `edae614f…629caf` **giống nhau** trước và sau ([cleanup.json](evidence/cleanup.json)).
- **Cleanup:** container cô lập đã xóa (`docker ps -a` chỉ còn `ecommerce-postgres`); worktree baseline đã `git worktree remove` + `prune`; file mật khẩu tạm đã xóa; không để lại process nào.
- **Secret:** đã quét thư mục evidence, không có DSN, mật khẩu hay token.

---

# 09. Remaining Risks

**P1/P2 chưa xử lý trong đợt này**
- CB-04 (P1): catalog >500 sản phẩm/>100 brand, hoặc tìm kiếm ≥500 dòng → 503 (fail-closed, chờ DR-04/DR-13b).
- CB-08 (P1): chưa có chatbot trên storefront (DR-03).
- CB-09 (P2): sản phẩm có promotion chồng lấn làm cả lượt 503 (DR-13a).
- CB-11 (P2): liệt kê tới 500 đơn khi hỏi không có mã (DR-06).
- CB-12, CB-13, CB-14 (P2): kho chung, feedback và idempotency không lưu bền.
- **Mới quan sát (đề xuất P2):** khách chưa từng có giỏ hàng hỏi giỏ hàng → backend trả `cartId: null` → chatbot trả **502** (V-3). Cần quyết định cách biểu diễn "chưa có giỏ" trong `CartContext` hoặc ở tầng mapping.
- **Mới quan sát (P3, có từ trước):** "laptop hơn/đắt hơn 20 triệu", "laptop tầm 15 triệu", "laptop Intel i5…" (không có "giá"/từ nối) đặt từ ngay sau loại sản phẩm vào ô brand, và brand guard Fix01 trả "thương hiệu chưa xác minh". An toàn nhưng sai lý do. Sau Phase 1, câu có tiền chưa hiểu được hỏi lại về giá trước khi tới brand guard; các trường hợp còn lại không đổi vì sửa sẽ đụng vào `IDENTITY_BOUNDARIES` của Fix01.

**DR còn pending:** DR-01, 02, 03, 04, 05a, 05b, 06, 07, 08, 09, 10, 11, 12, 13a, 13b.

**Giới hạn đã biết**
- "rẻ hơn/thấp hơn X", "< X", "tầm/khoảng/cỡ X", "ngân sách X", "giá X" luôn hỏi lại; tăng số lượt hỏi lại cho tới khi DR-01/02 được duyệt.
- Câu hỏi lại dùng chuỗi chung có sẵn, không nói cụ thể điều kiện nào chưa rõ (DR-13b).
- Câu tiếng Việt hiếm như "2 dòng", "5 ngàn" có ngữ cảnh giá có thể bị hiểu là tiền; "Inspiron 15 - 20 triệu" bị đọc là khoảng.
- `NoOwnedOrders` đi qua span `customer.context` có decorator `traced`, nên span này ghi `error` và trace tổng có `warning` dù đây là trạng thái bình thường.
- Ranking degrade không báo cho khách (DR-09).
- Quên đặt `APP_ENV` thì mock/memory vẫn chạy; không có readiness probe.

**Khoảng trống runtime:** không có E2E thật, không load test, không có số đo throughput sau C2, không kiểm L4 trên hạ tầng thật, frontend không được kiểm lại.

---

# 10. Phase 2 Handoff

- **Đã sửa:** CB-01, CB-02, CB-03 (không gồm R2/R3 có giá), CB-05 (L1+L2), CB-06, CB-07 (tối thiểu), CB-10 (đơn hàng), CB-18, CB-19, CB-21, CB-22, CB-23.
- **Chưa sửa:** CB-04, CB-08, CB-09, CB-11, CB-12, CB-13, CB-14 và các phần DR-pending của CB-01/02/03/05; CB-10 phần giỏ hàng (bị chặn).
- **Hành vi mới cần nghiệm thu:**
  1. Câu có tiền mà parser không chắc chắn → hỏi lại bằng chuỗi có sẵn; không còn discovery thiếu giá.
  2. "X trở xuống/đổ lại/trở lên", "A–B triệu", "A đến B triệu", "giữa A và B", "tối đa là X" được đọc.
  3. Tham chiếu: không nhầm "lần đầu tiên", "thứ hai tuần sau"; "thấp hơn" đi qua resolver; phạm vi mới + "rẻ hơn" không dùng snapshot cũ.
  4. Ranking lỗi → vẫn trả thẻ đã xác minh, trace `product.ranking_degraded`.
  5. Startup bị từ chối khi `APP_ENV` không phải dev và cấu hình thiếu; log WARNING `chatbot.runtime_configuration`.
  6. `/api/v1/chat/status` 404 ngoài dev.
  7. Danh sách đơn rỗng → "Tài khoản của bạn chưa có đơn hàng nào."; mã đơn không phân biệt hoa thường.
  8. `references` của model bị bỏ; IMEI/serial bị che trong trace.
- **Case cần nghiệm thu thêm:** NX-01..18, 23..25, 30..32, 34..36, 39..44 (bảng mục 05); xác nhận lại độc lập việc FAIL trên `f99f713`; xem xét lệch dự kiến của NX-35.
- **Phải chạy lại:** đủ 120 case (T01–T60, N01–N48, B01–B12) với Qwen + Spring thật + DB clone, kiểm hash `cases.json` = `9c0104a1…2a2835`; ưu tiên T07–T11, T14, T16, T21–T30, T55, N01–N12, N19–N23, N25–N28, N40, N42, N46, N47 vì chạm vào parser giá, tham chiếu và đơn hàng. N42 ("iPhone 15 giá 1 đồng") giờ trả **clarification** (kỳ vọng frozen chấp nhận "safe clarification"). B08/B11 cần chạy lại trên runtime thật.
- **Rủi ro đặc biệt:** số lần hỏi lại có thể tăng ở T/N case có cách nói giá ngoài grammar; E5 với câu có mã đơn nhưng không có "đơn hàng"; khách chưa có giỏ vẫn nhận 502.
- **Exact Git state:** branch `dev/thai-duy-vu`, HEAD `a267d1c7758b164eba9e11b45a5b1e469c14fd84`, toàn bộ thay đổi Phase 1 **chưa commit** (danh sách mục 07), cùng các thay đổi có sẵn trước phiên (mục 01).
- **Evidence:** `chatbot-service/debug/production-readiness/evidence/` — `00_baseline_pytest.txt`, `01_nx_on_baseline_f99f713.txt`, `02_python_full_with_postgres.txt`, `02b_python_full_without_db.txt`, `03_fix01_regression.txt`, `04_fix02_regression.txt`, `05_fix04_regression.txt`, `06_tracing_config.txt`, `07_group_a_focused.txt`, `08_nx_full_after.txt`, `baseline.json`, `cleanup.json`.

**IMPLEMENTATION_VERDICT: `CODE_FIX_COMPLETE`** — mọi hạng mục không cần DR và không bị chặn đã có trong diff, test mới và test hiện có đạt kèm output. Phần giỏ hàng của E1 bị chặn bởi V-3 và các hạng mục phụ thuộc DR được liệt kê ở trên; chúng **không** được tính là đã sửa hay PASS.
