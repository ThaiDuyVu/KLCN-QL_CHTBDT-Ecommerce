# Phase 2 — Final Technical Acceptance + Manual UAT Readiness

> [C2C] STATE: EXECUTED · TASK_ID: c2c_chatbot_phase2_final_acceptance · ITERATION: 1
> **Tính độc lập:** môi trường không có reviewer độc lập. Hotfix (PART A) và nghiệm thu (PART B/C) do **cùng một agent** thực hiện. Đây là *acceptance evidence do cùng agent thu thập*, chờ ChatGPT/người dùng review cuối; không phải phê duyệt cuối.
> Không tuyên bố PRODUCTION_READY. Đường dẫn evidence tính từ `chatbot-service/debug/production-readiness/`.

---

## 01. Source Baseline

| Thời điểm | Branch / HEAD | Working tree | Ghi chú |
|---|---|---|---|
| Trước hotfix | `dev/thai-duy-vu` @ `df64381` (Phase 1 commit; cha là `1289b41`, `a267d1c`) | sạch | `e10669d` (mốc prompt nêu) là merge PR #51 trên `origin/main`, **cùng tree** `8a2d214` với `df64381` → source Phase 1 khớp. Branch quan sát trong prompt (`feature/chatbot-production-readiness-plan`) không phải branch hiện tại; không reset/checkout. Không có commit mới sau `df64381` chạm chatbot. |
| Sau hotfix = acceptance candidate | `df64381` + diff chưa commit | `M chat/service.py`, `M customer_context/service.py`, `?? tests/test_cart_hotfix.py` | Đóng băng lúc 2026-10-21T04:4x UTC: [B00_acceptance_source_fingerprint.json](phase2-evidence/B00_acceptance_source_fingerprint.json), aggregate `0b9b454e808e586f…` |
| Lúc chạy nghiệm thu và khi kết thúc | như trên | không đổi | Kiểm lại cuối phiên: 0 file app/test/frontend chatbot khác fingerprint |

- `cases.json` SHA256 `9c0104a14d032bbb774c1a2434ff21d3d66487b4e78655dcd0889c24207a2835` trước, trong (bản sao byte-exact của run) và sau.
- Python 3.12.15 (fastapi 0.142.2, pydantic 2.13.5, httpx 0.28.1, psycopg 3.3.6, pytest 9.1.1, playwright 1.63.0); Java Temurin 21.0.12.1; Node 22.23.3; Docker 29.8.1; Ollama `qwen3:4b-instruct`, `qwen3-embedding:0.6b` (digest trong [environment_sanitized.json](phase2-evidence/final120/environment_sanitized.json)).
- Cấu hình runtime của acceptance stack (đã che secret): `APP_ENV=development` (để bật trace theo harness), `CHATBOT_AI_ENABLED=true`, `PRODUCT_ADVISOR_MODE=real`, `CONVERSATION_STORAGE=postgres`, product index + knowledge + conversation trỏ DB clone, backend `8081`, chatbot `8091/8092/8093` (kho mặc định / kho rỗng / không kho), frontend `5174`.

## 02. Cart Hotfix Review

**Root cause.** `CartServiceImpl.get(userId)` gọi `response(current(userId, false))`; khách chưa từng có giỏ → HTTP 200 với `cartId: null, items: [], subtotal: 0` (Jackson không bật `NON_NULL`, khóa `cartId` có mặt). `CustomerContextService.load_context` dựng `CartContext(cart_id=row['cartId'])` với `cart_id: UUID` bắt buộc → `ValidationError` → HTTP 502.

**Sửa (2 file app, không đổi Java/DTO/schema).**
- `customer_context/service.py`: `never_created_cart(row)` đúng khi và chỉ khi payload có khóa `cartId` bằng `null`, `items == []` và `subtotal == 0`; khi đó raise `VerifiedEmptyCart` (exception nội bộ, cùng kiểu `NoOwnedOrders` của Phase 1). Mọi payload khác không có cart identity vẫn đi đường cũ → 502.
- `chat/service.py`: bắt `VerifiedEmptyCart` → `clarification = "Giỏ hàng của bạn hiện đang trống."` (đúng câu đang có trong response generator) và `CustomerContext()` rỗng.
- Không UUID giả, không đổi `CartContext.cart_id`, không gọi API ghi, không đổi `get_cart`/404/204.
- Diff: [A03_hotfix.diff](phase2-evidence/A03_hotfix.diff). Đã gỡ một thay đổi phụ không cần thiết (dùng chung hằng trong `response_generation`) để giữ phạm vi 2 file.

**Test** (`tests/test_cart_hotfix.py`, 24 test, chỉ dùng entry point có từ trước):

| Case | Trước hotfix (`df64381`) | Sau |
|---|---|---|
| A chưa tạo giỏ (2 dạng payload) + context không bịa cart | **FAIL — HTTP 502 thật** (3 test) | PASS |
| B giỏ có ID, rỗng (giữ `cart_id`) | PASS | PASS |
| C giỏ có item (SKU/số lượng/giá/tạm tính, không suy tồn kho/khuyến mãi) | PASS | PASS |
| D backend 500/502/503 → 502; timeout/connect → 503 | PASS | PASS |
| E `cartId=null` + item / subtotal≠0 / items null / thiếu items / item thiếu variant / giá âm / cartId sai; mảng JSON trần | PASS (502) | PASS (502) |
| F 404/204 không bị diễn giải | PASS | PASS |
| G 401/403 giữ nguyên; không cookie thì không gọi backend; chỉ `GET /api/cart` với cookie của chính khách | PASS | PASS |
| Yêu cầu thêm vào giỏ bị từ chối trước khi đọc giỏ | PASS | PASS |

Evidence: [A01 trước](phase2-evidence/A01_cart_tests_before_hotfix.txt), [A02 sau + Fix02](phase2-evidence/A02_cart_and_fix02_after_hotfix.txt) (80 passed).

**E2E thật** ([phase2_targeted_e2e.json](phase2-evidence/final120/evidence/phase2_targeted_e2e.json), [phase2_uat_browser.json](phase2-evidence/final120/evidence/phase2_uat_browser.json)):
- Spring thật trả cho khách tổng hợp chưa có giỏ: HTTP 200, `cartId=null`, `items=[]`, `subtotal=0` (chứng minh contract runtime).
- Chatbot: A → 200 "Giỏ hàng của bạn hiện đang trống." (backend chỉ `GET /api/cart`); sau đó khách vẫn **0 cart** (không tạo giỏ). B (`seed.customer2`) → cùng câu. C (`seed.customer1`) → "Xiaomi 14T Pro (DEV-X14TP-BLU-512): 1 sản phẩm; giá 18990000.0; Tạm tính 18990000.0", khớp `/api/cart`.
- Trình duyệt thật: CART-A, CART-B, CART-C đều PASS.

**CART_HOTFIX_VERDICT: PASS.** NX-37 được giữ ID, đổi giả định đầu vào thành contract đã xác minh (200 + `cartId=null`) thay vì 404/204 của kế hoạch cũ, vì V-3 cho thấy backend không bao giờ trả 404/204 cho trường hợp này.

## 03. NX Acceptance Matrix

Chạy mới trên 3 cây với cùng file test ([B01 f99f713](phase2-evidence/B01_nx_cart_on_f99f713.txt) 50 fail/43 pass; [B01 df64381](phase2-evidence/B01_nx_cart_on_df64381.txt) 3 fail/90 pass; [B01 candidate](phase2-evidence/B01_nx_cart_on_candidate.txt) 93 pass). Một mã NX có thể ứng với nhiều hàm/tham số test; thống kê bên dưới đếm **mã NX**, không đếm parametrization. Các test ma trận validator dùng API mới (ImportError ở f99f713) không được tính là bằng chứng BR.

| ID | Type | Applicable | Old (f99f713) | New (candidate) | Evidence | Verdict |
|---|---|---|---|---|---|---|
| NX-01 | BR | Có (DR-02 fail-safe) | FAIL | PASS | B01 | PASS (fail-safe; hành vi duyệt NOT_RUN_PENDING_DECISION) |
| NX-02 | BR | Có (DR-02 fail-safe) | FAIL | PASS | B01 | PASS (fail-safe) |
| NX-03 | RC | Có | PASS | PASS | B01 | PASS |
| NX-04 | BR | Có | FAIL | PASS | B01 | PASS |
| NX-05 | BR | Có | FAIL | PASS | B01 | PASS |
| NX-06 | RC | Có | PASS | PASS | B01 | PASS |
| NX-07 | BR | Có (DR-01 fail-safe) | FAIL | PASS | B01 | PASS (fail-safe) |
| NX-08 | BR | Có (DR-01 fail-safe) | FAIL | PASS | B01 | PASS (fail-safe) |
| NX-09 | RC | Có | PASS | PASS | B01 | PASS |
| NX-10 | RC | Có | PASS | PASS | B01 | PASS |
| NX-11 | BR | Có | FAIL | PASS | B01 | PASS |
| NX-12 | BR | Có | FAIL | PASS | B01 | PASS |
| NX-13 | BR | Có | FAIL | PASS | B01 | PASS |
| NX-14 | BR | Có (DR-02/12 fail-safe) | FAIL | PASS | B01 | PASS (fail-safe) |
| NX-15 | RC | Có | PASS | PASS | B01 | PASS |
| NX-16 | AN | Có (không cần DR) | FAIL (N/A) | PASS | B01 | PASS |
| NX-17 | RC | Có | PASS | PASS | B01 | PASS |
| NX-18 | BR | Có (DR-02 fail-safe) | FAIL | PASS | B01 | PASS (fail-safe) |
| NX-19 | BR | Không (DR-04) | — | — | — | NOT_RUN_PENDING_DECISION |
| NX-20 | AN | Không (DR-04) | — | — | — | NOT_RUN_PENDING_DECISION |
| NX-21 | AN | Không (DR-04) | — | — | — | NOT_RUN_PENDING_DECISION |
| NX-22 | BR | Không (DR-13b) | — | — | — | NOT_RUN_PENDING_DECISION |
| NX-23 | FC | Có | FAIL | PASS | B01 | PASS |
| NX-24 | FC | Có | FAIL | PASS | B01 | PASS |
| NX-25 | FC | Có | PASS | PASS | B01 | PASS |
| NX-26 | FC | Không (DR-13a) | — | — | — | NOT_RUN_PENDING_DECISION |
| NX-27 | FC | Không (DR-13a) | — | — | — | NOT_RUN_PENDING_DECISION |
| NX-28 | FC | Không (DR-13a) | — | — | — | NOT_RUN_PENDING_DECISION |
| NX-29 | FC | Không (DR-13a) | — | — | — | NOT_RUN_PENDING_DECISION |
| NX-30 | FC | Có | PASS | PASS | B01 | PASS |
| NX-31 | BR | Có | FAIL (lifespan không chặn) | PASS | B01 | PASS |
| NX-32 | AN | Có (không cần DR) | FAIL | PASS | B01 | PASS |
| NX-33 | AN | Không (DR-05a) | — | — | — | NOT_RUN_PENDING_DECISION |
| NX-34 | FC | Có | FAIL | PASS | B01 | PASS |
| NX-35 | FC | Có | FAIL (kế hoạch dự kiến PASS) | PASS | B01, [B02 probe](phase2-evidence/B02_nx35_event_loop_probe.txt) | PASS — lệch dự kiến đã được kiểm chứng, không phải regression |
| NX-36 | BR | Có | FAIL | PASS | B01 | PASS |
| NX-37 | BR | Có (re-based theo V-3) | FAIL (cả f99f713 và df64381: 502) | PASS | B01, A01/A02 | PASS |
| NX-38 | BR | Không (DR-06) | — | — | — | NOT_RUN_PENDING_DECISION |
| NX-39 | BR | Có | FAIL | PASS | B01 | PASS |
| NX-40 | RC | Có | PASS | PASS | B01 | PASS |
| NX-41 | BR | Có | FAIL | PASS | B01 | PASS |
| NX-42 | BR | Có | FAIL | PASS | B01 | PASS |
| NX-43 | RC | Có | PASS | PASS | B01 | PASS |
| NX-44 | BR | Có | FAIL | PASS | B01 | PASS |

**Thống kê 44 mã:** PASS 34 (trong đó 6 chỉ phần fail-safe: 01, 02, 07, 08, 14, 18) · FAIL 0 · chưa chạy 10 · bị chặn 0 (NX-37 hết bị chặn sau hotfix) · hoãn vì DR 10 (19, 20, 21, 22, 26, 27, 28, 29, 33, 38). BR đã chạy: 22/22 FAIL ở f99f713, PASS ở candidate. RC: 10/10 PASS cả ba cây. FC FAIL-trước (23, 24, 34) đúng dự kiến; 25, 30 PASS cả hai.

**NX-35 — kiểm chứng nhận định Phase 1.** Probe đo tick của event loop trong 1 giây `add_message` (PYTHONPATH ghim từng cây; lần thử đầu không ghim đã resolve cả hai về main checkout và bị loại): f99f713 **0 tick**, lệnh hủy không thể phát ra trong lúc ghi; candidate **19 tick**, hủy được phát giữa lúc ghi. Cả hai: lease trả sau cùng, chỉ còn USER, không vào context. Kết luận: FAIL ở baseline là hệ quả CB-07 (loop bị chặn), bất biến lease/completed-turn không bị phá.

**NX_VERDICT: PASS_WITH_CONDITIONS** (10 mã chờ DR không được tính PASS).

## 04. Fix01 / Fix02 / Fix04

Tất cả chạy mới trên candidate.

| Hạng mục | Bằng chứng | Kết quả |
|---|---|---|
| Fix01: N03, N04, N12, N46, N47 (real Qwen) | [results.json](phase2-evidence/final120/results.json) | PASS cả 5 |
| Fix01: hard price/RAM/storage, brand/category, SKU, exact count, compare per-target, fail-closed catalog | `test_query_integrity`, `test_query_product_v2`, `test_real_product_advisor`, `test_pagination_boundaries` trong python-focused 264 passed / python-full 582 passed; B06/B07 PASS | PASS |
| Fix02: N27, N28 (real Spring) | results.json | PASS |
| Fix02: ownership, payment integrity, nullable warehouse, không suy PAID từ DELIVERED, 409 an toàn | `test_order_payment_read`, `test_customer_context_mapping` (A02: 80 passed); T39/T42/T43/N25/N26 PASS | PASS — xem finding **F-01** (mã đơn không kèm "đơn hàng") |
| Fix04: B07–B11 | 120-run boundary cases + 8 control độc lập trên PostgreSQL/FastAPI/Ollama thật: B07, B08 (POST đồng thời 200/409), B09, B10 (restart FastAPI thật), B11, `cancel_enter`, `cancel_model`, `independent_sessions` | PASS |
| Fix04: N38, N40, N41 | results.json | PASS |
| Fix04: completed-turn, persistence, ownership (T57/T58/T60), refusal, frontend retry/race | 120-run + 19 UI check Chrome (UI09 retry, UI10 switch khi đang chờ, UI11 double-submit) | PASS |

**FIX01_REGRESSION_VERDICT: PASS · FIX02_REGRESSION_VERDICT: PASS_WITH_CONDITIONS (F-01) · FIX04_REGRESSION_VERDICT: PASS**

## 05. Full 120 Regression

- Thực thi thật: 104 chat case + 3 security case (Qwen thật khi cần, guard deterministic khi có) qua Spring thật trên DB clone cô lập; 12 boundary case theo execution class đã đóng băng (9 fault có kiểm soát, B08 đồng thời thật, B11 security thật, B12 knowledge timeout có kiểm soát); T59 bằng Chrome thật + restart FastAPI. Không gọi cả 120 là Qwen E2E.
- Plan: bản sao byte-exact `cases.json` (hash khớp, [plan_sha256.json](phase2-evidence/final120/evidence/plan_sha256.json)). Evaluator là bản sao không đổi logic của `final-regression-120/harness/evaluate_current.py` (chỉ đổi độ sâu đường dẫn ROOT; xem `phase2-evidence/final120/harness/README.md`).

| | PASS | PASS_WITH_WARNINGS | FAIL | DEFERRED |
|---|---|---|---|---|
| **Phase 2 (candidate)** | **111** | **3** | **2** | **4** |
| final-regression-120 (lịch sử) | 111 | 3 | 2 | 4 |
| Baseline chẩn đoán ban đầu | 99 | 8 | 9 | 4 |

Theo nhóm: T 57/2/0/1, N 42/1/2/3, B 12/0/0/0.

- **So với final-regression-120: 0 case đổi verdict → không có `REGRESSION_NEW`.**
- So với baseline chẩn đoán: 12 case tốt lên (N03, N04, N12, N27, N28, N38, N40, N41, N46, N47, B07, B10); N33 PASS_WITH_WARNINGS → FAIL do evaluator hiện hành chặt hơn (cùng kết quả final-regression-120).

| Case | Verdict | Phân loại | Quan sát thực |
|---|---|---|---|
| N33 | FAIL | KNOWN_EXISTING (RAG) | Hỏi chính sách đổi hàng tận nhà chính thức: trả trích đoạn DEMO kèm câu không xác nhận chính thức, nhưng **trích dẫn cả chunk "DEMO — Hướng dẫn kiểm tra thanh toán" không liên quan** |
| N34 | FAIL | KNOWN_EXISTING (RAG) | Hỏi sản phẩm giảm giá theo tài liệu: trích chunk đổi trả DEMO thay vì nói chưa đủ dữ liệu |
| T56, N43, N44, N48 | DEFERRED_APPROVED_SCOPE | DEFERRED_APPROVAL (multi-intent) | T56/N43 hỏi lại từng nhóm; N44 chỉ trả giỏ hàng (bỏ phần so sánh); N48 chỉ trả đơn hàng |
| T51 | PASS_WITH_WARNINGS | KNOWN_EXISTING | "Xiaomii 15" → hỏi lại tên model (không đoán) |
| T59 | PASS_WITH_WARNINGS | KNOWN_EXISTING | Reload/restart giữ text và ID; feedback/trace nằm trong RAM |
| N18 | PASS_WITH_WARNINGS | KNOWN_EXISTING | Chỉ trả tồn kho tại kho được chọn, không trả "tất cả các kho" |

Bảng đầy đủ 120 case (ID, expected, actual, verdict, error type, trace ID): [RESULTS_TABLE.md](phase2-evidence/final120/RESULTS_TABLE.md). Dữ liệu gốc: [results.json](phase2-evidence/final120/results.json), [trace_index.json](phase2-evidence/final120/trace_index.json), trace đã sanitize trong `final120/evidence/traces/`. Runtime fingerprint: [source_hashes.json](phase2-evidence/final120/source_hashes.json) (`source_version: phase2-cart-hotfix-acceptance-candidate`). Kiểm nguồn dữ liệu: mỗi biến thể/thẻ được đối chiếu với `/api/v1/products/{id}/detail`; đơn/payment/giỏ/bảo hành đối chiếu DB clone và API backend (check `Every retrieved product variant…`, `Authenticated backend/DB personal evidence…`).

**FROZEN_120_VERDICT: PASS_WITH_CONDITIONS** — 2 FAIL thuộc RAG (Official Knowledge chưa được duyệt) và 4 multi-intent DEFERRED vẫn mở; người dùng chưa chấp nhận chúng.

## 06. Backend Integration

| Bộ | Lệnh | Kết quả |
|---|---|---|
| Java integration/contract (Testcontainers) | `./mvnw -q -Dapi.version=1.44 -Dtest=AuthMe…,AuthLogin…,AuthLogout…,AuthRefresh…,ProductPersistence…,OrderCheckout…,OrderRead…,OrderWorkflow…,GoodsReceiptInventory…,WarehouseController…,WarehouseServiceImpl… test` | **89 tests, 0 failures, 0 errors, 0 skipped**, exit 0 ([java-test-summary.json](phase2-evidence/final120/evidence/java-test-summary.json)) |
| Cart contract | Không có test Java nào khẳng định GET khi chưa có giỏ; không thêm code Java. Chứng minh bằng Spring thật | 200, `cartId=null`, `items=[]`, `subtotal=0`; `/api/cart` với STAFF → 403 (`OrderCheckoutIntegrationTest`) |
| Order/Payment | OrderRead/OrderCheckout + T39/T42/T43/N25–N28 thật | PASS |
| Product | ProductPersistence + đối chiếu detail cho mọi thẻ trong 120-run | PASS |
| Ownership | T57 (401), T58 (403 phiên khách khác), T60 (trace khách khác 404), F4-foreign-code, ADMIN gọi chatbot → 403 | PASS |

Java không bị sửa nên không chạy toàn bộ Java suite.

## 07. Security and Data Integrity

- **Fingerprint:** clone business (12 bảng, JSON hash từng bảng) trước = sau; source dev DB (`source_snapshot`) trước = sau ([cleanup.json](phase2-evidence/final120/cleanup.json)). Hai script Phase 2 tự kiểm `carts/cart_items/orders/payments/order_items(/inventory)` trước = sau.
- **Fixture được phép (chỉ trong clone, đã dọn):** 5 order, 4 payment, 2 warranty, 1 ticket của harness; 2 tài khoản CUSTOMER tổng hợp chưa có giỏ cho Case A (users/customers/user_roles, mật khẩu băm sao chép từ seed) đã xóa cùng chat history/refresh token. Clone và container đã bị xóa.
- **Authorization:** T57/T58/T60, F4-foreign-code, ADMIN → 403, `/status` 404 ngoài dev (NX-41).
- **Không lộ secret:** N41/F7-secrets không trả cookie/JWT; trace che IMEI/serial (NX-44); quét evidence Phase 2 không có DSN/JWT/token.
- **Read-only:** T45–T47, N37, N39, Q10, F7-mutation từ chối trước khi đọc backend; script cart xác nhận chatbot chỉ gọi `GET /api/cart`.
- **Đồng thời / hủy:** B08, NX-34, NX-35, `cancel_*`, `independent_sessions`.
- **Lỗi DB không tạo dữ liệu giả:** B09/B10, NX-24.
- **Sự cố môi trường do nghiệm thu gây ra (ENV-INC-01):** bước build của harness ghi đè `backend/target/backend-0.0.1-SNAPSHOT.jar` khi backend dev của người dùng (8080) đang chạy từ file đó → JVM đó lỗi `NoClassDefFoundError`, chatbot dev trả 503 an toàn. Đã dừng đúng PID đó và chạy lại `scripts/start.sh` (chỉ backend được khởi động lại). Trường `normal_application_processes_modified=false` trong `cleanup.json` là literal cố định của harness, **không đúng** cho lần chạy này. Chi tiết: [B04_environment_incident.json](phase2-evidence/B04_environment_incident.json).
- **Quan sát môi trường:** harness chạy container clone với `docker run -P`, nên DB clone được publish trên `0.0.0.0:55000` (mật khẩu ngẫu nhiên) trong thời gian chạy; container đã xóa.

**SECURITY_VERDICT: PASS · DATA_INTEGRITY_VERDICT: PASS_WITH_CONDITIONS** (dữ liệu nguyên vẹn; có ENV-INC-01 với process của người dùng, đã khắc phục).

## 08. Frontend/Browser UAT

| Bộ | Kết quả |
|---|---|
| Vitest (`npm test`) | 7 file, **59 passed** (dòng `Tests 59 passed`; bộ đếm tự động của harness ghi nhầm 66) |
| ESLint `--max-warnings 0` | exit 0 |
| Production build | exit 0 (`index-*.js` 498 kB) |
| 19 UI check Chrome thật (UI01–UI19) | 19/19 PASS ([ui_inventory.json](phase2-evidence/final120/ui_inventory.json)) |
| 26 bước Part C Chrome thật (checklist, đa lượt, ProductCard → trang chi tiết, đơn hàng, giỏ A/B/C, từ chối, ADMIN, 3 viewport) | 26/26 PASS, 0 page error ([phase2_uat_browser.json](phase2-evidence/final120/evidence/phase2_uat_browser.json), ảnh `final120/evidence/uat/`) |
| Responsive 390 / 768 / 1440 | overflow ngang 0px, nút gửi hiển thị |
| UAT stack thật của người dùng (5173) | status "AI thật" + PostgreSQL; tìm laptop 200 (2 thẻ), giỏ hàng 200 khớp backend, từ chối đặt hàng; 0 page error ([phase2_uat_stack_smoke.json](phase2-evidence/final120/evidence/phase2_uat_stack_smoke.json)) |

Quan sát UX: "Có máy nào rẻ hơn không?" sau lượt hỏi chi tiết dùng giá thẻ vừa xem làm mốc nên có thể không còn sản phẩm rẻ hơn (không bịa); khung "Kỳ vọng" của Lab bám theo kịch bản đang chọn, không theo câu gõ tay.

**UI_LAB_VERDICT: PASS (UI_LAB_READY) · UI_STOREFRONT_VERDICT: DEFERRED** (DR-03, không có widget storefront).

## 09. Runtime Configuration

- Acceptance stack: AI thật, advisor `real`, conversation `postgres`, Qwen `qwen3:4b-instruct` + embedding `qwen3-embedding:0.6b`, Spring 8081, frontend 5174 proxy `/api` → 8081, `/api/v1/chat` → 8091. Đã dọn.
- UAT stack (để người dùng dùng): `scripts/start.sh` của dự án; frontend `http://localhost:5173` (Vite dev, route `/chatbot-lab` bật trong dev), backend 8080, chatbot 8090 (`--reload`, đang chạy đúng source candidate: reload lúc 11:40:04 sau lần ghi cuối, mọi file khớp fingerprint), Ollama 11434, PostgreSQL `ecommerce-postgres` (dev, chỉ có tài khoản seed). `.env` chatbot: `APP_ENV=development`, AI `true`, advisor `real`, conversation `postgres`, trace bật (dev), kho đã cấu hình; các URL DB có cấu hình (không in giá trị).
- Validator production: kiểm bằng NX-31 (lifespan + ma trận) — không áp dụng cho UAT vì `APP_ENV=development`.
- Chưa có `/api/v1/ready` (DR-05a). `/api/v1/health` chỉ là liveness. Quên đặt `APP_ENV` khi deploy vẫn chạy được ở dev-mode, chỉ có log WARNING.

**GROUP_C_VERDICT: PASS_WITH_CONDITIONS** (không có readiness; rủi ro APP_ENV phụ thuộc gate triển khai).

## 10. Remaining Findings

| ID | Loại | Severity | Vị trí | Tái hiện | Actual / Expected | Regression? | Chặn UAT? | Đề xuất phạm vi sửa |
|---|---|---|---|---|---|---|---|---|
| **F-01** | KNOWN_EXISTING | **P1 (đề xuất)** | `chat/policy.py:read_only_plan` (chỉ đọc mã đơn khi câu có "đơn hàng"/"đơn của tôi") + `customer_context/service.py` (PAYMENT_STATUS không mã → đơn mới nhất) | `seed.customer1`: "ORD-DIAG-NOPAY thanh toán chưa?", "Mã ORD-DIAG-NOPAY đã thanh toán chưa?", "ORD-DIAG-NOPAY của tôi thế nào?" (6/6 lần) | Trả "ORD-DIAG-DELIVERED … PAID" (đơn khác, có ghi mã) / phải xét đúng ORD-DIAG-NOPAY hoặc hỏi lại. Dữ liệu là thật và thuộc chính khách, không bịa, nhưng trả lời **sai đơn** | Không: Qwen trả `{"intent":"PAYMENT_STATUS"}`, không có `references`, nên E5 không bỏ mất gì; hành vi giống trước Phase 1 ([phase2_order_code_without_keyword.json](phase2-evidence/final120/evidence/phase2_order_code_without_keyword.json)) | **Cần người dùng quyết định** (D-1) | Guard nhận mã `ORD-…` cả khi không có chữ "đơn"; mã chỉ định không khớp → không chọn đơn khác |
| F-02 | KNOWN_EXISTING | P3 | `read_only_plan` | "Đơn này đã thanh toán chưa?" | Liệt kê đơn kèm "chưa có dữ liệu thanh toán…" thay vì trả lời thanh toán (không bịa) | Không | Không | Hỗ trợ tham chiếu đơn trong phiên (cần thiết kế) |
| F-03 | KNOWN_EXISTING | P2 | Knowledge retrieval | N33, N34 | Trích chunk DEMO không liên quan | Không | Không (ngoài phạm vi Official RAG) | Theo checkpoint RAG chính thức |
| F-04 | APPROVED_DEFERRED | — | Orchestration | T56, N43, N44, N48 | Chỉ xử lý một ý | Không | Không | Multi-intent (hoãn) |
| F-05 | KNOWN_EXISTING | P3 | Lab UI | — | Khung "Kỳ vọng" theo kịch bản, không theo câu gõ | Không | Không | UI nhỏ (cần DR-03) |
| F-06 | KNOWN_EXISTING | P3 | `@traced('customer.context')` | Giỏ chưa tạo / đơn rỗng | Span ghi `error`, trace tổng `warning` dù là trạng thái hợp lệ | Không (cùng cơ chế Phase 1) | Không | Trả kết quả thay vì exception nội bộ |
| ENV-INC-01 | ENVIRONMENT_LIMITATION | P2 (harness) | `post-upgrade-validation/harness/environment.py` | Chạy harness khi backend dev đang chạy từ `target/*.jar` | Ghi đè jar đang dùng → JVM dev lỗi; đã khắc phục | Có từ trước trong harness | Không (đã khắc phục) | Build ra thư mục riêng |
| ENV-02 | ENVIRONMENT_LIMITATION | P3 | `environment.py` (`docker run -P`) | — | DB clone publish trên 0.0.0.0 trong lúc chạy | Có từ trước | Không | Bind 127.0.0.1 |
| PD-* | PENDING_DECISION | — | — | — | DR-01, 02, 03, 04, 05a, 05b, 06, 07, 08, 09, 10, 11, 12, 13a, 13b | — | — | — |

Không có NEW_BLOCKER hay REGRESSION mới trong bộ 120 và NX.

## 11. Production Readiness

**Không PRODUCTION_READY.** Còn thiếu: widget storefront (DR-03); readiness probe (DR-05a) và gate triển khai L4 chưa kiểm trên hạ tầng thật; catalog >500 sản phẩm/100 brand trả 503 (DR-04); promotion chồng lấn trả 503 (DR-13a); feedback/idempotency/kho theo khách (DR-07/08); Official Knowledge RAG; multi-intent; chưa load test hay quyết định pooling; F-01.

## 12. Final Technical Verdict

| Verdict | Giá trị |
|---|---|
| GROUP_A_VERDICT | PASS_WITH_CONDITIONS — đúng và fail-safe; nhiều cách nói giá (tầm/khoảng/rẻ hơn X/ngân sách/giá X) sẽ **hỏi lại** bằng câu chung cho tới khi DR-01/02/12/13b được duyệt |
| GROUP_B_VERDICT | PASS_WITH_CONDITIONS — B3 đạt; B1/B2/B4 DEFERRED, vẫn 503 |
| GROUP_C_VERDICT | PASS_WITH_CONDITIONS |
| GROUP_E_VERDICT | PASS_WITH_CONDITIONS — E1 (đơn), E3–E6 đạt; F-01 có từ trước |
| CART_HOTFIX_VERDICT | PASS |
| FIX01_REGRESSION_VERDICT | PASS |
| FIX02_REGRESSION_VERDICT | PASS_WITH_CONDITIONS (F-01) |
| FIX04_REGRESSION_VERDICT | PASS |
| NX_VERDICT | PASS_WITH_CONDITIONS |
| FROZEN_120_VERDICT | PASS_WITH_CONDITIONS |
| UI_LAB_VERDICT | PASS |
| UI_STOREFRONT_VERDICT | DEFERRED |
| SECURITY_VERDICT | PASS |
| DATA_INTEGRITY_VERDICT | PASS_WITH_CONDITIONS |
| **Tổng thể** | **PASS_WITH_CONDITIONS** |

**Đề xuất trạng thái UAT: `READY_FOR_MANUAL_UAT_WITH_CONDITIONS`.** Các điều kiện dưới đây **chưa** được người dùng chấp nhận. Nếu người dùng không chấp nhận D-1, trạng thái phải là `FIX_REQUIRED` cho F-01 trước khi UAT.

- **D-1:** chấp nhận F-01 làm giới hạn đã biết trong UAT (người thử phải hỏi "Đơn hàng ORD-… của tôi…"), hoặc mở checkpoint sửa F-01 trước UAT.
- **D-2:** RAG N33/N34 ngoài phạm vi UAT cho tới khi có tài liệu chính thức.
- **D-3:** multi-intent không thuộc phạm vi UAT.
- **D-4:** các cách nói giá chờ DR-01/02 chỉ cho câu hỏi lại an toàn.
- **D-5:** UAT chỉ trên Chatbot Lab (UI_LAB_READY), không phải chatbot cho khách hàng.

## 13. Manual UAT Handoff

- **URL đã kiểm chứng:** `http://localhost:5173/chatbot-lab` (Chrome thật, đăng nhập `seed.customer1`, AI thật + PostgreSQL, sau khi khôi phục backend).
- **Dịch vụ cần tiếp tục chạy:** `ecommerce-postgres` (Docker), Ollama 11434, backend 8080, chatbot 8090, frontend 5173 — tất cả do `scripts/start.sh` quản lý (pid trong `.local/pids/`).
- **Tài khoản (dữ liệu dev, đọc lúc bàn giao):** `seed.customer1` (giỏ 1 sản phẩm; 1 đơn PENDING, payment PENDING), `seed.customer2` (giỏ đã tạo nhưng rỗng; 1 đơn CANCELLED, payment PENDING). Các đơn fixture `ORD-DIAG-*` chỉ có trong clone đã xóa, **không** có trong DB dev; mật khẩu seed theo `chatbot-service/docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md`. Không có tài khoản dev nào chưa từng có giỏ và không có đăng ký tự phục vụ, nên Case A chỉ được nghiệm thu trong clone.
- **Hướng dẫn và checklist:** [04_MANUAL_UAT_GUIDE.md](04_MANUAL_UAT_GUIDE.md).
- Dữ liệu UAT trong DB dev: chỉ ghi chat history (hành vi bình thường của Lab).
