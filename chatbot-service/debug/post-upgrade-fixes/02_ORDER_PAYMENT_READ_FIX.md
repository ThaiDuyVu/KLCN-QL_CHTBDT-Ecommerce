# 02 — Order/Payment Read Fix

[C2C] STATE: EXECUTED · TASK_ID: c2c_chatbot_fix02_order_payment · ITERATION: 1

## 1. Executive summary

F05/N28 được sửa bằng null-safe warehouse mapping trong read summary. F04/N27 giữ nguyên Spring exactly-one-payment invariant và409; chatbot nhận diện đúng một error shape trên owned detail GET, trả explanation không thể xác minh thanh toán thay vì generic502. Không tạo payment, không đoán PAID/UNPAID/PENDING, không thay bằng đơn khác.

**31 Java tests PASS;396 full Python tests PASS,0 skipped;15 runtime order/security prompts PASS.** Hai Fix01 controls N03/N12 chạy thêm với Qwen thật đều PASS; N27/N28 repeat có fingerprint business tables trước/sau bằng nhau. Không có BLOCKED_CONTRACT. Kết quả sẵn sàng independent review, chưa tự tuyên bố accepted/production-ready.

## 2. Current Git/source baseline

Workspace connector: **QL_CHTBDT_Ecommerce**, `dev/thai-duy-vu`, source HEAD **595ccd3e3f85d956369f679357856fd978eeb99d** (`595ccd3`, merge PR46). Working tree sạch ban đầu. Fetch GitHub rồi fast-forward current branch để khớp connector. HEAD source đồng nhất với accepted follow-up `8b1753f`; so với diagnostic `1c62129` chỉ có các thay đổi Fix01 đã ghi nhận, chưa sửa F04/F05.

Đã đọc current Java/Python, V4/schema và error handler; review matrix/results/root-cause/final report, N27/N28 traces/fixtures, Fix01 acceptance/follow-up. Fix01 source không bị sửa;111 test integrity instances vẫn nằm trong full regression. [Baseline](evidence/fix02/baseline.json), [executed app/Java source hashes](evidence/fix02/runtime/source_worktree_sha256.json), [actual JAR hash](evidence/fix02/runtime/runtime_binary.json).

Hash source thực thi trùng submitted working tree. Sau push, dùng commit chứa báo cáo này; HEAD baseline ở trên không đại diện các thay đổi chưa commit tại thời điểm test.

## 3. N27/N28 reproduced causes

Tái hiện mới trên stack thật trước sửa, không chỉ dựa vào historical claims:

| Case | Prompt | Backend trước | Chatbot trước | Root cause |
|---|---|---:|---:|---|
| N27 | Đơn hàng ORD-DIAG-NOPAY của tôi đã thanh toán chưa? |409, existing ApiErrorResponse.message |502 generic | full() từ chối paymentRows.size()!=1; adapter mất nghĩa specific conflict |
| N28 | Đơn hàng ORD-DIAG-NULLWAREHOUSE của tôi đã thanh toán chưa? |500 |502 generic | summary(): immutable Map.of().get(null) ném NPE khi order warehouse_id null |

[New before runtime responses/traces](evidence/fix02/before-runtime/results.json), [actual upstream body](evidence/fix02/before-runtime/owned_error_shape.json). Original historical traces vẫn nguyên: N27 `ca157971-8898-4040-966b-b13b70adf848`; N28 `eaef270b-10b0-4540-959f-cc891633e23f`.

Java regression mới trước sửa chạy1 test,1 error,exit1; root NullPointerException tại OrderServiceImpl.summary:208. [Sanitized failure](evidence/fix02/java-before-failure.json). Đây là lỗi mapping thực, không compilation error.

## 4. Payment read-contract decision và rationale

Áp dụng conservative strategy được yêu cầu. Existing GlobalExceptionHandler map CommerceException thành JSON `ApiErrorResponse(String message)`. Read conflict thật là:

```json
{"message":"Đơn không có đúng một payment; cần rà dữ liệu trước khi xử lý"}
```

Chỉ khi **GET + `/api/orders/mine/<valid UUID>` + HTTP409 + exact JSON object trên** mới phát sinh internal OwnedOrderPaymentIntegrityError. Không dựa vào409 đơn thuần, substring hoặc order.status. Staff namespace, mutations, malformed/different/extra-field error bodies giữ mapping cũ. Không thêm public code/enum/endpoint/DTO.

CustomerContextService đã chọn candidate từ owned list trước detail. Internal error truyền về ChatService; service dùng field `QueryPlan.clarification` hiện có, customer context rỗng (không có OrderContext/PaymentContext giả), rồi existing deterministic response branch trả:

> Không thể xác minh trạng thái thanh toán của đơn hàng này vì dữ liệu thanh toán chưa đầy đủ hoặc không nhất quán.

**Backend409 giữ nguyên; chatbot trả200 ChatResponse chứa unavailable explanation.** Đây là response giải thích thành công, không phải xác nhận payment thành công. Frozen N27 expected yêu cầu explicitly unknown payment, không yêu cầu giữ một HTTP error; outcome mới đáp ứng expected. Không che một retained chatbot502 để gọi PASS. Ordinary errors401/403/404/409→existing502/500→502/timeouts503 không bị remap toàn cục.

Contract decision này không cần phê duyệt thêm trong conservative scope. Nếu Spring thay localization/message/shape, adapter fail closed về ordinary error; muốn machine-readable discriminator bền hơn cần review contract riêng, không triển khai ở đây.

## 5. Null warehouse fix

Giữ `warehouseId=o.getWarehouseId()`. Chỉ lookup warehouseName khi ID không null; null→null. Không tự chọn kho, không invent tên/ID. Warehouse hợp lệ vẫn map từ repository. Summary/full được dùng chung cho owner và staff list/detail, nên đều được sửa read path.

V4 cho phép nullable orders.warehouse_id; schema/migration không đổi. Checkout vẫn bắt buộc cart có kho ACTIVE hợp lệ. Inventory reserve/release/delivery, order transitions và payment handling không bị thay đổi.

## 6. Files changed

| File | Why |
|---|---|
| `backend/.../order/service/OrderServiceImpl.java` | Một null guard trong summary; không sửa exactly-one-payment check hoặc mutations. |
| `backend/.../order/integration/OrderReadIntegrationTest.java` (mới) |10 test instances: null/non-null/mixed warehouse, owner/staff roles, missing/multiple/normal payment, foreign404, unknown404, VNPAY gate và unrelated installment409. |
| `backend/.../order/integration/OrderCheckoutIntegrationTest.java` | Thêm2 test: missing selected warehouse không financial mutation; INSTALLMENT checkout exactly one payment và approval gate. |
| `chatbot-service/app/shared/backend/errors.py` (mới) | Internal typed error, exact existing discriminator và safe static explanation. |
| `chatbot-service/app/shared/backend/client.py` | Narrow owned-GET conflict recognition trước generic HTTP error mapping. |
| `chatbot-service/app/features/chat/service.py` | Handle internal unavailable outcome qua existing clarification, không tạo business evidence. |
| `chatbot-service/tests/test_order_payment_read.py` (mới) |34 tests cho exact error mapping, negative controls, owned/latest/exact selection, missing payment DTO, no model/no mutation, B03/B04. |
| `debug/post-upgrade-fixes/harness/fix02_*.py` | Runtime, cleanup và reproducible diagnostic scaffolding; không sửa original harness/reports. |

OrderResponse, controllers, GlobalExceptionHandler, CustomerContext DTO/mapper, ResponseGeneration, auth/security, schemas/migrations và accepted Fix01 normalization/contracts giữ nguyên. Không đổi RAG hoặc transaction guards (Fix03).

## 7. Before/after behavior

| Case | Backend trước → sau | Chatbot trước → sau | Evidence/grounding sau | Verdict |
|---|---|---|---|---|
| N27 |409 →409, same cardinality guard |502 generic →200 explicit unavailable | QueryPlan PAYMENT_STATUS/PAYMENT_STATUS field + exact order_code, final clarification; no loaded order/payment evidence |PASS |
| N28 |500 →200 |502 →200 | warehouseId=null,warehouseName=null; real COD/PENDING payment, exact original order, amount preserved |PASS |

### N27

Trace `5f2b6f64-33b2-433c-a001-d34eea7bc178`; HTTP200; 86.22ms.

```text
Không thể xác minh trạng thái thanh toán của đơn hàng này vì dữ liệu thanh toán chưa đầy đủ hoặc không nhất quán.
```

[Response/initial and final QueryPlan/CustomerContext](evidence/fix02/runtime/results.json), [trace](evidence/fix02/runtime/evidence/traces/N27.json).

### N28

Trace `0873d75d-2bb4-43ca-810e-aec1b81555c6`; HTTP200; 84.11ms.

```text
ORD-DIAG-NULLWAREHOUSE: trạng thái đơn hàng PENDING; tổng tiền 18990000.0 VND; trạng thái thanh toán: PENDING; phương thức: COD; số tiền: 18990000.0 VND.
```

[Response/initial and final QueryPlan/CustomerContext](evidence/fix02/runtime/results.json), [trace](evidence/fix02/runtime/evidence/traces/N28.json).

N27 trace status warning phản ánh handled upstream conflict, không phải ChatResponse failure. Sequence: owned selection → detail409 → backend.owned_payment_integrity → internal error caught → customer.payment_unavailable → GroundedContext clarification → deterministic_guard/clarification → HTTP200. N28 trace ok. Cả hai dropped_events=0.

## 8. Authorization/ownership regression

- Real anonymous owned detail401; CUSTOMER truy cập staff endpoint403; unknown owned UUID404.
- Other customer đọc các primary-owned orders trả404, kể cả missing/multiple payments và null warehouse. Owned check diễn ra trước full()/payment diagnostics; không tiết lộ cardinality của foreign order.
- C-FOREIGN: exact code thuộc secondary customer không nằm trong primary-owned list; không detail fetch, không dùng đơn khác. Secondary đọc chính anomalous order của mình nhận409 theo existing invariant.
- STAFF list/detail thực trả200 với null warehouse; Java tests lặp cả STAFF/MANAGER/ADMIN. Đây là role permissions hiện có, không mở quyền mới.

[Read/security projections](evidence/fix02/runtime/security_and_read_controls.json). Không publish full recipient DTO, private customer IDs, JWT/cookies/headers.

## 9. Checkout/payment invariant regression

Exactly-one-payment guard giữ nguyên cho missing0 và multiple2, owner/staff detail đều409 cùng exact body. Test schema cho phép nhiều payments cùng order, nên multiple fixture hợp lệ ở DB nhưng inconsistent theo service; không gọi đó là normal checkout output.

Existing checkout COD tạo đúng một payment, totals/prices snapshot đúng, selected warehouse bắt buộc, cancel/release và delivery/reservation/concurrency rules vẫn qua tests. INSTALLMENT checkout tạo đúng một PENDING payment + installment; chưa APPROVED thì không CONFIRMED, approved mới cho phép. VNPAY PENDING read giữ expiry/payment evidence và không CONFIRMED; disabled sandbox checkout vẫn bị từ chối như trước. Không sửa gateway callbacks hoặc thật sự gửi thanh toán.

No-payment DELIVERED không thành PAID; valid PENDING/FAILED/PAID đến từ payment DTO. Known inconsistent order không bị thay bằng older/latest other order; C-LATEST-INCONSISTENT kiểm tra latest chính là no-payment và trả unknown dù còn đơn PAID khác.

## 10. Java tests

| Suite | Passed | Failed/errors/skipped |
|---|---:|---|
| OrderReadIntegrationTest |10 |0/0/0 |
| OrderCheckoutIntegrationTest |11 |0/0/0 |
| OrderWorkflowTest |4 |0/0/0 |
| AuthMeIntegrationTest |6 |0/0/0 |
| Total |31 |0/0/0 |

Dedicated Testcontainers pgvector databases, Flyway hiện có, actual MockMvc/security/repositories. [Surefire summary](evidence/fix02/java-test-summary.json). Exact Maven command, exit code/timestamp và sanitized output nằm trong [commands](evidence/fix02/evidence/commands.json). Đây là toàn bộ suites order hiện có + new read suite và relevant auth identity; không tuyên bố đã chạy mọi unrelated backend test.

## 11. Python tests

Focused QU Fix01 + backend/client/customer-context/grounding/new payment tests: **191 passed**. Full chatbot: **396 passed,0failed,0skipped**, gồm isolated PostgreSQL persistence/knowledge integration; test settings không trỏ source DB.34 test instances mới, không cộng số focused vào total396. Initial focused smoke80 passed; final saved runs là191/396. Một Starlette/httpx deprecation warning còn tồn tại.

Negative controls: unrelated409, INSTALLMENT409, server500, malformed409 bodies, wrong path/method/UUID, 401/403/404, timeout503, compatible payment=None DTO, latest/exact selection, mismatched identity và malformed cart. B03/B04 chạy lại bằng controlled MockTransport và nhận502 như cũ; đây không phải real-Qwen boundary proof.

[Focused output](evidence/fix02/evidence/commands/python-focused.txt), [full output](evidence/fix02/evidence/commands/python-full.txt). Existing Fix01 normalizer/contracts/follow-up tests vẫn pass; không sửa accepted logic để ép order cases pass.

## 12. Real-stack runtime evidence

Real FastAPI/Spring/isolated PostgreSQL-pgvector; Ollama/Qwen thật sẵn sàng.15 order/safety prompts route bởi existing deterministic read_only_plan, do đó không cần Qwen model call cho chính các prompt này. Không gọi chúng là15 lần Qwen inference. Hai extra Fix01 controls N03/N12 thực sự có query.model_attempt và Ollama HTTP success, xác minh model thật trong pipeline.

| Case | Actual intent | HTTP | Verdict | Evidence |
|---|---|---:|---|---|
| T06 | ORDER_STATUS | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/T06.json) |
| T38 | ORDER_STATUS | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/T38.json) |
| T39 | PAYMENT_STATUS | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/T39.json) |
| T42 | PAYMENT_STATUS | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/T42.json) |
| T43 | ORDER_STATUS | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/T43.json) |
| T44 | UNKNOWN | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/T44.json) |
| T45 | UNKNOWN | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/T45.json) |
| T46 | UNKNOWN | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/T46.json) |
| T47 | UNKNOWN | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/T47.json) |
| N26 | PAYMENT_STATUS | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/N26.json) |
| N27 | PAYMENT_STATUS | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/N27.json) |
| N28 | PAYMENT_STATUS | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/N28.json) |
| C-MULTIPAY | PAYMENT_STATUS | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/C-MULTIPAY.json) |
| C-FOREIGN | PAYMENT_STATUS | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/C-FOREIGN.json) |
| C-LATEST-INCONSISTENT | PAYMENT_STATUS | 200 | PASS | [trace](evidence/fix02/runtime/evidence/traces/C-LATEST-INCONSISTENT.json) |

11 requested historical/mandatory cases + N26 FAILED +3 negative/selection controls=15 distinct business prompts. Extra N03/N12 add2 distinct product controls, not part of that15 matrix. Extra N27/N28 fingerprinted repeats and before-fix reproductions are reported separately, not inflated into distinct coverage or statistical reliability.

[Runtime results](evidence/fix02/runtime/results.json), [trace index](evidence/fix02/runtime/trace_index.json), [extra real Qwen/fingerprint proof](evidence/fix02/extra-runtime/business_and_model_proof.json), [before run](evidence/fix02/before-runtime/results.json). All eligible response facts inspected against live Spring detail; no product cards/sources for order cases.

Per-request business hashes compare orders/payments/items/installments/inventory/warranty/cart tables. Additional N27/N28 repeats save actual before/after table counts+hashes; equal. The temporary latest-order timestamp is an isolated fixture setup change, restored after the request and not a chatbot mutation. All fixture inserts exist only in disposable clone; source DB access is read-only fingerprint/dump. Cleanup removes container/fixture graph and restores original source DB running state. [Cleanup](evidence/fix02/cleanup.json).

## 13. Remaining limitations / blocked decisions

No portion is BLOCKED_CONTRACT or BLOCKED_ENVIRONMENT. The chosen specific message comparison is intentionally conservative and localization-sensitive; drift will use ordinary502 until a separately approved stable discriminator exists. No automatic repair/backfill or tolerant successful Spring detail for inconsistent payments was implemented.

The no-payment/multiple-payment fixtures are schema-valid anomalies; no evidence normal checkout generates them. Normal checkout requires a warehouse; read compatibility does not authorize legacy mutation repair. Real VNPAY bank transactions/callback settlement, performance/load and full browser UI were not executed. No frontend source changes; API ChatResponse is the UI-visible contract under test.

Official knowledge, RAG relevance, transaction guard language limitations, orphan conversation turns and other prior findings remain outside Fix02; Fix03 has not started.

## 14. Compatibility risks

No Flyway/schema/public DTO/status/error-code additions. New behavior is scoped to the proven owned-detail cardinality error; chatbot consumers now receive200 with unavailable explanation for that case while Spring still409. All unrelated HTTP mappings and identity mismatches remain errors as before. Existing clarification is application-controlled, not model-supplied.

Anomaly outcomes carry no order/payment DTO and do not state a verified payment status; clients must display message rather than infer payment from HTTP200. Trace warning for a handled inconsistency is expected. Preserve exact error regression tests if backend wording changes.

## 15. Git status/diff and evidence preservation

Working changes are the files listed in section6 plus this report/new sanitized evidence. Protected diagnostic and Fix01 files are compared with recorded SHA256 and not overwritten. [Protected manifest](evidence/fix02/protected_reports_sha256.json), [diff/status](evidence/fix02/git_status.txt), [summary](evidence/fix02/summary.json). Source/binary hashes identify actual tested working tree; all schema/auth/checkout core mutation methods remain unchanged.

User explicitly requested automatic push when finished in the preceding turn; this persists and authorizes commit/push after validation. The final chat handoff supplies the resulting commit and clean remote-sync status; this diagnostic baseline record is preserved.

## 16. Final C2C handoff

STATE: EXECUTED · TASK_ID: c2c_chatbot_fix02_order_payment · ITERATION: 1.

F04 resolved through approved conservative read-error handling; F05 null mapping fixed. N27/N28 live PASS, all15 business controls PASS, two real-Qwen Fix01 controls PASS, Java31/Python396 passed. No outstanding contract approval required for the implemented scope. Independent ChatGPT/user review and acceptance remain separate from execution. **STOP after Fix02; do not start Fix03 without approval.**
