# CHECKPOINT 03 — RAG & CUSTOMER CONTEXT TEST

Task `c2c_chatbot_eval_phase03`, iteration0. **14 prompt T31–T44**: 4 PASS, 7 PASS_WITH_WARNINGS, 1 FAIL, 2 BLOCKED_FIXTURE. Đã gửi cả14 request để quan sát runtime; BLOCKED_FIXTURE nghĩa chưa kiểm positive warranty/ticket do dữ liệu không có, không phải request chưa gửi hoặc môi trường chết.

## 1. Test objective/scope

Knowledge RAG/vector/evidence/source; cart/orders/payment/warranty/ticket và ownership. Không sửa code/schema/seed/prompt/model; không tạo order, đổi payment hoặc bảo hành. Đã đọc phase01/02: các hạn chế relevance/citations, identity/reference và route warranty chưa được sửa. Không chạy lại toàn bộ test cũ hay checkpoint04.

Verdict xét text/cards/sources. PASS_WITH_WARNINGS là đáp án chính đạt trong fixture hiện tại nhưng có relevance/routing/reference/coverage issue. First fault trong warning có thể chỉ là hạn chế sớm nhất, không phải observed wrong answer. Không coi HTTP200 hoặc trace status ok là PASS.

## 2. Runtime configuration và cách chạy

Workspace `/Users/vu/Documents/Vu/HUIT-Year4/KLTN/KLCN-QL_CHTBDT-Ecommerce-main`, branch dev/thai-duy-vu, commit438227c. Worktree ban đầu chỉ eval60 chưa theo dõi từ phase01/02. Dùng runtime đang chạy, không restart để tránh mất memory người dùng: frontend5173, FastAPI8090, Spring8080, PostgreSQL5432/Docker, Ollama11434.

AI=true, product real; qwen3:4b-instruct / qwen3-embedding:0.6b ready; warehouse/product index/knowledge cấu hình. Development tracing bật CHATBOT_TRACE_ENABLED và CHATBOT_TRACE_FILE_ENABLED, actual sink `.local/logs/chatbot-traces.jsonl`. 1JSONL snapshot/trace với nested events; không event chat.request.* cũ. Boundary đầu run 43dòng/2444570bytes, UTC2026-10-13T07:10:35.166742+00:00; sau57dòng,14 trace mới. Không xóa/rotate lịch sử.

Chrome thật, synthetic CUSTOMER đã xác thực, session mới từngcase/history0; POST qua UI/Vite. Bắt UUID header, lọc sink mới theo trace_id/session_id, đối chiếu chat.pipeline với API/text/cards UI. Read-only backend/DB baseline trước/sau; không lấy model answer làm evidence. Fixtures-after bằng fixtures-before với cart/orders/payment/warranty/ticket/knowledge; mã nonexistent vẫn0row.

Evidence rút gọn/sanitize tại [evidence/phase03](evidence/phase03/); actual raw log/screenshots/helper chỉ ở `.local/eval-phase03/`, không commit. Không ghi credentials/contact/DSN/vector; real order code/IDs trong baseline là dữ liệu synthetic dùng đúng theo yêu cầu T42, không full personal DTO.

## 3. Fixture coverage

| Fixture | Coverage thực tế | Giới hạn |
|---|---|---|
| Knowledge | 2 tài liệu DEMO, 2 chunk có vector1024, status=PUBLISHED | Không có policy official 30 ngày hoặc lifetime |
| Cart | 1 Xiaomi14T Pro, SKU DEV-X14TP-BLU-512, qty1, giá/subtotal18.990.000 | Chưa thử empty cart |
| Orders | 1 owned order PENDING | Latest=specified code nên không phân biệt non-latest selection |
| Payment | PENDING của đúng order detail | Chưa thử PAID/no-payment hoặc nhiều payments |
| Warranty | 0 record | T40 BLOCKED_FIXTURE, chỉ negative empty-path |
| Ticket | 0 record | T41 BLOCKED_FIXTURE, chỉ negative empty-path |
| Ownership | Primary GET owned order200, secondary CUSTOMER GET cùng order404 | Không thay thế full authorization audit |

T42 dùng mã real `ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29` từ `/api/orders/mine` của primary synthetic user. T43 dùng `ORD-EVAL03-NOTFOUND-c60fc64a2c8d`, xác minh SELECT toàn bảng orders cho mã này=0 cả trước/sau. Không tạo record hoặc code giống order khác.

[fixtures-before.json](evidence/phase03/fixtures-before.json) và [fixtures-after.json](evidence/phase03/fixtures-after.json) chứa DTO đã rút gọn, source Knowledge/chunk, counts và ownership status, không customer identity/contact.

## 4. Matrix T31–T44 / phân tích từng case

| ID | Actual intent / branch | RAG chunks / customer orders | Verdict | FIRST FAULT | Severity |
|---|---|---|---|---|---|
| T31 | KNOWLEDGE_QA / ollama | 2 / 0 | PASS_WITH_WARNINGS | KNOWLEDGE_RETRIEVAL | LOW |
| T32 | KNOWLEDGE_QA / ollama | 2 / 0 | PASS_WITH_WARNINGS | KNOWLEDGE_RETRIEVAL | LOW |
| T33 | KNOWLEDGE_QA / ollama | 2 / 0 | PASS_WITH_WARNINGS | KNOWLEDGE_RETRIEVAL | LOW |
| T34 | ORDER_STATUS / ollama | 0 / 1 | FAIL | QUERY_UNDERSTANDING | HIGH |
| T35 | KNOWLEDGE_QA / ollama | 2 / 0 | PASS_WITH_WARNINGS | KNOWLEDGE_RETRIEVAL | LOW |
| T36 | WARRANTY_STATUS / insufficient_evidence | 0 / 0 | PASS_WITH_WARNINGS | QUERY_UNDERSTANDING | LOW |
| T37 | CART_STATUS / ollama | 0 / 0 | PASS | NO_FAULT | PASS |
| T38 | ORDER_STATUS / ollama | 0 / 1 | PASS | NO_FAULT | PASS |
| T39 | PAYMENT_STATUS / ollama | 0 / 1 | PASS | NO_FAULT | PASS |
| T40 | WARRANTY_STATUS / insufficient_evidence | 0 / 0 | BLOCKED_FIXTURE | CONFIGURATION | BLOCKER |
| T41 | WARRANTY_TICKET_STATUS / insufficient_evidence | 0 / 0 | BLOCKED_FIXTURE | CONFIGURATION | BLOCKER |
| T42 | PAYMENT_STATUS / ollama | 0 / 1 | PASS_WITH_WARNINGS | CUSTOMER_CONTEXT | MEDIUM |
| T43 | ORDER_STATUS / ollama | 0 / 1 | PASS_WITH_WARNINGS | CUSTOMER_CONTEXT | LOW |
| T44 | UNKNOWN / insufficient_evidence | 0 / 0 | PASS | NO_FAULT | PASS |

### T31 — PASS_WITH_WARNINGS

**Prompt:** Theo tài liệu DEMO, tôi được đề nghị đổi trả trong bao nhiêu ngày?

**Expected:** DEMO 7 ngày ngày từ lúc nhận, nếu baseline vẫn vậy.

**Execution:** 2026-10-13T14:10:35.309251+07:00; session `e054088c-b3e6-433e-a568-37affadfdcc0`; trace `535f4d53-09f3-45db-a8f4-1e52a4907257`; HTTP200; UI/API10.409s; pipeline10.315s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "KNOWLEDGE_QA",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": "chính sách đổi trả"
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → knowledge.retrieve → ollama.http → embedding.shape → knowledge.vector_search → knowledge.sql → response.grounded_context → response.generate → response.strategy → ollama.http. Backend API `[]`. Embedding [{"endpoint":"/api/embed","model":"qwen3-embedding:0.6b","input":["chính sách đổi trả"]}]; shape={"count":1,"dimensions":1024}; search={"status":"PUBLISHED","top_k":5,"duration_ms":24.86,"query_parameters":{"status":"PUBLISHED","dimensions":1024,"top_k":5,"vector":"omitted"}}

Chunks thực đã đưa vào GroundedContext:

| Chunk / Document | Similarity | Title / Source |
|---|---|---|
| 466d9bd6-e605-4688-83e7-0f0e825736e8 / 3da5065e-c979-5610-90d0-9e6e94ba0308 | 0.619255483 | DEMO — Chính sách đổi trả kiểm thử / demo://chatbot-lab/return |
| 74bcff7a-616d-4e34-bf35-e72eade1408c / f73151c4-6168-5352-8d0c-642e7c2ad801 | 0.423855798 | DEMO — Hướng dẫn kiểm tra thanh toán / demo://chatbot-lab/payment |

Evidence text (khớp DB chunk):

> Đây là dữ liệu DEMO phục vụ kiểm thử chatbot, không phải chính sách cửa hàng chính thức. Trong kịch bản DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng. Điều kiện DEMO: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, không bị hư hỏng do người sử dụng. Chatbot chỉ giải thích, không tự tạo yêu cầu đổi trả. Chính sách áp dụng thực tế phải được cửa hàng xác nhận.

Evidence text (khớp DB chunk):

> Đây là hướng dẫn DEMO kiểm thử. Muốn biết đã thanh toán hay chưa phải đọc trạng thái payment của đơn từ backend. Không kết luận PAID chỉ vì người dùng nói đã chuyển khoản. Khi chưa có payment, chatbot phải nói chưa đủ dữ liệu. Chatbot không thực hiện giao dịch, không thu tiền và không yêu cầu mật khẩu hoặc mã OTP.

**GroundedContext:** `{"products":0,"knowledge_chunks":2,"history_count":0,"customer":{"cart":null,"orders":[],"warranty_count":0,"ticket_count":0}}`. Sources API `[{"document_id":"3da5065e-c979-5610-90d0-9e6e94ba0308","chunk_id":"466d9bd6-e605-4688-83e7-0f0e825736e8","title":"DEMO — Chính sách đổi trả kiểm thử","source":"demo://chatbot-lab/return"},{"document_id":"f73151c4-6168-5352-8d0c-642e7c2ad801","chunk_id":"74bcff7a-616d-4e34-bf35-e72eade1408c","title":"DEMO — Hướng dẫn kiểm tra thanh toán","source":"demo://chatbot-lab/payment"}]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Theo tài liệu DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng, với điều kiện có hóa đơn, sản phẩm còn đầy đủ phụ kiện và không bị hư hỏng do người sử dụng. Lưu ý: đây là chính sách DEMO, không phải chính sách chính thức của cửa hàng.

**Backend evidence/Diagnosis:** KNOWLEDGE_QA, text đúng 7 ngày từ lúc nhận và ghi DEMO; source đổi trả hỗ trợ trực tiếp. Chunk payment không liên quan vẫn vào context/source.

**Verdict:** PASS_WITH_WARNINGS; **First fault:** KNOWLEDGE_RETRIEVAL; **Owner:** PERSON_3; **Severity:** LOW. 

Trace excerpt [T31.jsonl](evidence/phase03/T31.jsonl).

### T32 — PASS_WITH_WARNINGS

**Prompt:** Theo tài liệu DEMO, đổi trả cần đáp ứng những điều kiện gì?

**Expected:** Điều kiện hóa đơn/phụ kiện/tình trạng sản phẩm đúng evidence.

**Execution:** 2026-10-13T14:10:56.800962+07:00; session `1574332d-df64-4777-9e43-2c05f0707d1b`; trace `e92586fc-7c3c-4683-bff7-2a29d7858eab`; HTTP200; UI/API4.997s; pipeline4.903s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "KNOWLEDGE_QA",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": "chính sách đổi trả"
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → knowledge.retrieve → ollama.http → embedding.shape → knowledge.vector_search → knowledge.sql → response.grounded_context → response.generate → response.strategy → ollama.http. Backend API `[]`. Embedding [{"endpoint":"/api/embed","model":"qwen3-embedding:0.6b","input":["chính sách đổi trả"]}]; shape={"count":1,"dimensions":1024}; search={"status":"PUBLISHED","top_k":5,"duration_ms":25.66,"query_parameters":{"status":"PUBLISHED","dimensions":1024,"top_k":5,"vector":"omitted"}}

Chunks thực đã đưa vào GroundedContext:

| Chunk / Document | Similarity | Title / Source |
|---|---|---|
| 466d9bd6-e605-4688-83e7-0f0e825736e8 / 3da5065e-c979-5610-90d0-9e6e94ba0308 | 0.619254223 | DEMO — Chính sách đổi trả kiểm thử / demo://chatbot-lab/return |
| 74bcff7a-616d-4e34-bf35-e72eade1408c / f73151c4-6168-5352-8d0c-642e7c2ad801 | 0.423854264 | DEMO — Hướng dẫn kiểm tra thanh toán / demo://chatbot-lab/payment |

Evidence text (khớp DB chunk):

> Đây là dữ liệu DEMO phục vụ kiểm thử chatbot, không phải chính sách cửa hàng chính thức. Trong kịch bản DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng. Điều kiện DEMO: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, không bị hư hỏng do người sử dụng. Chatbot chỉ giải thích, không tự tạo yêu cầu đổi trả. Chính sách áp dụng thực tế phải được cửa hàng xác nhận.

Evidence text (khớp DB chunk):

> Đây là hướng dẫn DEMO kiểm thử. Muốn biết đã thanh toán hay chưa phải đọc trạng thái payment của đơn từ backend. Không kết luận PAID chỉ vì người dùng nói đã chuyển khoản. Khi chưa có payment, chatbot phải nói chưa đủ dữ liệu. Chatbot không thực hiện giao dịch, không thu tiền và không yêu cầu mật khẩu hoặc mã OTP.

**GroundedContext:** `{"products":0,"knowledge_chunks":2,"history_count":0,"customer":{"cart":null,"orders":[],"warranty_count":0,"ticket_count":0}}`. Sources API `[{"document_id":"3da5065e-c979-5610-90d0-9e6e94ba0308","chunk_id":"466d9bd6-e605-4688-83e7-0f0e825736e8","title":"DEMO — Chính sách đổi trả kiểm thử","source":"demo://chatbot-lab/return"},{"document_id":"f73151c4-6168-5352-8d0c-642e7c2ad801","chunk_id":"74bcff7a-616d-4e34-bf35-e72eade1408c","title":"DEMO — Hướng dẫn kiểm tra thanh toán","source":"demo://chatbot-lab/payment"}]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Theo tài liệu DEMO, để đổi trả sản phẩm, khách hàng cần đáp ứng các điều kiện sau: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, và không bị hư hỏng do người sử dụng. Thời gian đổi trả trong kịch bản DEMO là 7 ngày kể từ lúc nhận hàng. Lưu ý rằng đây là chính sách DEMO, không phải chính sách cửa hàng chính thức và phải được cửa hàng xác nhận mới áp dụng thực tế.

**Backend evidence/Diagnosis:** Text đúng hóa đơn, đủ phụ kiện, không hư hỏng do user; nhắc DEMO không chính thức. Có thêm nguồn payment không hỗ trợ câu hỏi.

**Verdict:** PASS_WITH_WARNINGS; **First fault:** KNOWLEDGE_RETRIEVAL; **Owner:** PERSON_3; **Severity:** LOW. 

Trace excerpt [T32.jsonl](evidence/phase03/T32.jsonl).

### T33 — PASS_WITH_WARNINGS

**Prompt:** Theo tài liệu DEMO, có phải tôi được đổi trả trong 7 ngày kể từ ngày mua không?

**Expected:** Không nhầm ngày mua với ngày nhận.

**Execution:** 2026-10-13T14:11:12.855088+07:00; session `59000380-64da-4806-9134-8faf2f988421`; trace `066258fb-cb1e-4e5d-96cc-9728151bcd92`; HTTP200; UI/API5.061s; pipeline4.962s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "KNOWLEDGE_QA",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": "chính sách đổi trả"
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → knowledge.retrieve → ollama.http → embedding.shape → knowledge.vector_search → knowledge.sql → response.grounded_context → response.generate → response.strategy → ollama.http. Backend API `[]`. Embedding [{"endpoint":"/api/embed","model":"qwen3-embedding:0.6b","input":["chính sách đổi trả"]}]; shape={"count":1,"dimensions":1024}; search={"status":"PUBLISHED","top_k":5,"duration_ms":28.64,"query_parameters":{"status":"PUBLISHED","dimensions":1024,"top_k":5,"vector":"omitted"}}

Chunks thực đã đưa vào GroundedContext:

| Chunk / Document | Similarity | Title / Source |
|---|---|---|
| 466d9bd6-e605-4688-83e7-0f0e825736e8 / 3da5065e-c979-5610-90d0-9e6e94ba0308 | 0.619254223 | DEMO — Chính sách đổi trả kiểm thử / demo://chatbot-lab/return |
| 74bcff7a-616d-4e34-bf35-e72eade1408c / f73151c4-6168-5352-8d0c-642e7c2ad801 | 0.423854264 | DEMO — Hướng dẫn kiểm tra thanh toán / demo://chatbot-lab/payment |

Evidence text (khớp DB chunk):

> Đây là dữ liệu DEMO phục vụ kiểm thử chatbot, không phải chính sách cửa hàng chính thức. Trong kịch bản DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng. Điều kiện DEMO: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, không bị hư hỏng do người sử dụng. Chatbot chỉ giải thích, không tự tạo yêu cầu đổi trả. Chính sách áp dụng thực tế phải được cửa hàng xác nhận.

Evidence text (khớp DB chunk):

> Đây là hướng dẫn DEMO kiểm thử. Muốn biết đã thanh toán hay chưa phải đọc trạng thái payment của đơn từ backend. Không kết luận PAID chỉ vì người dùng nói đã chuyển khoản. Khi chưa có payment, chatbot phải nói chưa đủ dữ liệu. Chatbot không thực hiện giao dịch, không thu tiền và không yêu cầu mật khẩu hoặc mã OTP.

**GroundedContext:** `{"products":0,"knowledge_chunks":2,"history_count":0,"customer":{"cart":null,"orders":[],"warranty_count":0,"ticket_count":0}}`. Sources API `[{"document_id":"3da5065e-c979-5610-90d0-9e6e94ba0308","chunk_id":"466d9bd6-e605-4688-83e7-0f0e825736e8","title":"DEMO — Chính sách đổi trả kiểm thử","source":"demo://chatbot-lab/return"},{"document_id":"f73151c4-6168-5352-8d0c-642e7c2ad801","chunk_id":"74bcff7a-616d-4e34-bf35-e72eade1408c","title":"DEMO — Hướng dẫn kiểm tra thanh toán","source":"demo://chatbot-lab/payment"}]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Theo tài liệu DEMO, trong kịch bản kiểm thử, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng, với điều kiện: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, không bị hư hỏng do người sử dụng. Tuy nhiên, chính sách này chỉ là DEMO và chưa phải chính sách chính thức của cửa hàng. Chính sách áp dụng thực tế phải được cửa hàng xác nhận.

**Backend evidence/Diagnosis:** Text nói 7 ngày kể từ nhận hàng, không nhận lời 7 ngày từ ngày mua; evidence hỗ trợ. Không khẳng định policy chính thức. Warning chunk payment thừa.

**Verdict:** PASS_WITH_WARNINGS; **First fault:** KNOWLEDGE_RETRIEVAL; **Owner:** PERSON_3; **Severity:** LOW. 

Trace excerpt [T33.jsonl](evidence/phase03/T33.jsonl).

### T34 — FAIL

**Prompt:** Theo hướng dẫn DEMO, làm sao kiểm tra đơn hàng đã thanh toán chưa?

**Expected:** Hướng dẫn Knowledge, không bịa/infer payment state.

**Execution:** 2026-10-13T14:11:28.997760+07:00; session `97e00814-1d91-4e02-bd4c-857c7e4fafef`; trace `73a016c8-b344-41e6-b972-4fdffad8ea08`; HTTP200; UI/API4.238s; pipeline4.136s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "ORDER_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → backend.customer_http → response.grounded_context → response.generate → response.strategy → ollama.http. Backend API `[{"method":"GET","path":"/api/orders/mine"}]`. Không Knowledge vector search/embedding query; không ghi similarity/top-K giả cho nhánh không chạy.

Customer evidence đã rút gọn:
```json
{
  "cart": null,
  "orders": [
    {
      "order_code": "ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29",
      "order_date": "2026-10-04T06:36:15.571545Z",
      "status": "PENDING",
      "total_amount": "49990000.0",
      "payment": null
    }
  ],
  "warranty_count": 0,
  "ticket_count": 0
}
```

**GroundedContext:** `{"products":0,"knowledge_chunks":0,"history_count":0,"customer":{"cart":null,"orders":[{"order_code":"ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29","order_date":"2026-10-04T06:36:15.571545Z","status":"PENDING","total_amount":"49990000.0","payment":null}],"warranty_count":0,"ticket_count":0}}`. Sources API `[]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Theo hướng dẫn DEMO, để kiểm tra đơn hàng đã thanh toán chưa, bạn cần xem trạng thái đơn hàng. Trong trường hợp này, đơn hàng có mã ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29 có trạng thái là "PENDING", nghĩa là chưa được thanh toán.

**Backend evidence/Diagnosis:** Câu hướng dẫn DEMO bị parse ORDER_STATUS, không RAG/embedding/search/source. Customer evidence chỉ order.status=PENDING, payment=null, nhưng answer suy nghĩa chưa thanh toán và tự gắn Theo hướng dẫn DEMO. First fault QU; secondary RESPONSE_GENERATION suy payment từ order và attribution DEMO không có retrieved evidence. Payment thật cũng PENDING nhưng đó là trùng hợp, không làm suy diễn đúng evidence.

**Verdict:** FAIL; **First fault:** QUERY_UNDERSTANDING; **Owner:** PERSON_3; **Severity:** HIGH. 

Trace excerpt [T34.jsonl](evidence/phase03/T34.jsonl).

### T35 — PASS_WITH_WARNINGS

**Prompt:** Chính sách chính thức của cửa hàng cho đổi trả 30 ngày phải không?

**Expected:** Không biến DEMO thành official 30 ngàyngày.

**Execution:** 2026-10-13T14:11:44.239462+07:00; session `df9d241f-84fa-4533-9f9f-b63a4b5e34f8`; trace `b6f37421-d6e6-4c51-924f-9991fcfde168`; HTTP200; UI/API4.537s; pipeline4.451s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "KNOWLEDGE_QA",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": "chính sách đổi trả 30 ngày"
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → knowledge.retrieve → ollama.http → embedding.shape → knowledge.vector_search → knowledge.sql → response.grounded_context → response.generate → response.strategy → ollama.http. Backend API `[]`. Embedding [{"endpoint":"/api/embed","model":"qwen3-embedding:0.6b","input":["chính sách đổi trả 30 ngày"]}]; shape={"count":1,"dimensions":1024}; search={"status":"PUBLISHED","top_k":5,"duration_ms":33.29,"query_parameters":{"status":"PUBLISHED","dimensions":1024,"top_k":5,"vector":"omitted"}}

Chunks thực đã đưa vào GroundedContext:

| Chunk / Document | Similarity | Title / Source |
|---|---|---|
| 466d9bd6-e605-4688-83e7-0f0e825736e8 / 3da5065e-c979-5610-90d0-9e6e94ba0308 | 0.598836518 | DEMO — Chính sách đổi trả kiểm thử / demo://chatbot-lab/return |
| 74bcff7a-616d-4e34-bf35-e72eade1408c / f73151c4-6168-5352-8d0c-642e7c2ad801 | 0.278371019 | DEMO — Hướng dẫn kiểm tra thanh toán / demo://chatbot-lab/payment |

Evidence text (khớp DB chunk):

> Đây là dữ liệu DEMO phục vụ kiểm thử chatbot, không phải chính sách cửa hàng chính thức. Trong kịch bản DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng. Điều kiện DEMO: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, không bị hư hỏng do người sử dụng. Chatbot chỉ giải thích, không tự tạo yêu cầu đổi trả. Chính sách áp dụng thực tế phải được cửa hàng xác nhận.

Evidence text (khớp DB chunk):

> Đây là hướng dẫn DEMO kiểm thử. Muốn biết đã thanh toán hay chưa phải đọc trạng thái payment của đơn từ backend. Không kết luận PAID chỉ vì người dùng nói đã chuyển khoản. Khi chưa có payment, chatbot phải nói chưa đủ dữ liệu. Chatbot không thực hiện giao dịch, không thu tiền và không yêu cầu mật khẩu hoặc mã OTP.

**GroundedContext:** `{"products":0,"knowledge_chunks":2,"history_count":0,"customer":{"cart":null,"orders":[],"warranty_count":0,"ticket_count":0}}`. Sources API `[{"document_id":"3da5065e-c979-5610-90d0-9e6e94ba0308","chunk_id":"466d9bd6-e605-4688-83e7-0f0e825736e8","title":"DEMO — Chính sách đổi trả kiểm thử","source":"demo://chatbot-lab/return"},{"document_id":"f73151c4-6168-5352-8d0c-642e7c2ad801","chunk_id":"74bcff7a-616d-4e34-bf35-e72eade1408c","title":"DEMO — Hướng dẫn kiểm tra thanh toán","source":"demo://chatbot-lab/payment"}]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Chính sách đổi trả 30 ngày không được cung cấp trong dữ liệu hiện có. Dữ liệu DEMO chỉ cho thấy chính sách đổi trả trong 7 ngày với điều kiện cụ thể, và đây là dữ liệu kiểm thử, không phải chính sách chính thức của cửa hàng. Chính sách thực tế phải được cửa hàng xác nhận.

**Backend evidence/Diagnosis:** Không có evidence policy chính thức 30 ngày; response nói dữ liệu không cung cấp, DEMO 7 ngày không chính thức và cần cửa hàng xác nhận. Không flat deny/confirm official 30 ngày. Chunk payment thừa.

**Verdict:** PASS_WITH_WARNINGS; **First fault:** KNOWLEDGE_RETRIEVAL; **Owner:** PERSON_3; **Severity:** LOW. 

Trace excerpt [T35.jsonl](evidence/phase03/T35.jsonl).

### T36 — PASS_WITH_WARNINGS

**Prompt:** Cửa hàng có chính sách bảo hành trọn đời cho mọi sản phẩm không?

**Expected:** Lifetime chỉ được khẳng định nếu có evidence hợp lệ.

**Execution:** 2026-10-13T14:11:59.740639+07:00; session `3e650864-e6be-468a-bf9c-e5406259e377`; trace `83b872af-546d-4f79-93ac-aafdf21ead60`; HTTP200; UI/API1.312s; pipeline1.243s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "WARRANTY_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": "cửa hàng có chính sách bảo hành trọn đời cho mọi sản phẩm không?"
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → backend.customer_http → response.grounded_context → response.generate → response.strategy. Backend API `[{"method":"GET","path":"/api/warranties/mine"}]`. Không Knowledge vector search/embedding query; không ghi similarity/top-K giả cho nhánh không chạy.

Customer evidence đã rút gọn:
```json
{
  "cart": null,
  "orders": [],
  "warranty_count": 0,
  "ticket_count": 0
}
```

**GroundedContext:** `{"products":0,"knowledge_chunks":0,"history_count":0,"customer":{"cart":null,"orders":[],"warranty_count":0,"ticket_count":0}}`. Sources API `[]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Mình chưa đủ dữ liệu để trả lời chính xác yêu cầu này.

**Backend evidence/Diagnosis:** Policy bảo hành trọn đời bị route WARRANTY_STATUS cá nhân; chưa query knowledge. Text abstain chưa đủ dữ liệu, không khẳng định lifetime. Safe answer đạt expectation chỉ khẳng định khi có evidence; routing sai được ghi riêng.

**Verdict:** PASS_WITH_WARNINGS; **First fault:** QUERY_UNDERSTANDING; **Owner:** PERSON_3; **Severity:** LOW. 

Trace excerpt [T36.jsonl](evidence/phase03/T36.jsonl).

### T37 — PASS

**Prompt:** Giỏ hàng của tôi đang có những gì?

**Expected:** Product/SKU/quantity/price đúng cart hoặc cart trống.

**Execution:** 2026-10-13T14:12:12.062410+07:00; session `5a50c1df-d94d-48f7-be2a-7b8de4029bd7`; trace `f6b779a0-702e-47a9-baa5-c37c3c0a0ad4`; HTTP200; UI/API2.607s; pipeline2.525s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "CART_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → backend.customer_http → response.grounded_context → response.generate → response.strategy → ollama.http. Backend API `[{"method":"GET","path":"/api/cart"}]`. Không Knowledge vector search/embedding query; không ghi similarity/top-K giả cho nhánh không chạy.

Customer evidence đã rút gọn:
```json
{
  "cart": {
    "subtotal": "18990000.0",
    "items": [
      {
        "product_name": "Xiaomi 14T Pro",
        "sku": "DEV-X14TP-BLU-512",
        "quantity": 1,
        "effective_price": "18990000.0",
        "available_quantity": 2
      }
    ]
  },
  "orders": [],
  "warranty_count": 0,
  "ticket_count": 0
}
```

**GroundedContext:** `{"products":0,"knowledge_chunks":0,"history_count":0,"customer":{"cart":{"subtotal":"18990000.0","items":[{"product_name":"Xiaomi 14T Pro","sku":"DEV-X14TP-BLU-512","quantity":1,"effective_price":"18990000.0","available_quantity":2}]},"orders":[],"warranty_count":0,"ticket_count":0}}`. Sources API `[]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Giỏ hàng của bạn hiện có 1 chiếc Xiaomi 14T Pro (mã SKU: DEV-X14TP-BLU-512), giá hiệu lực 18.990.000 đồng.

**Backend evidence/Diagnosis:** CART_STATUS, GET /api/cart; product/SKU/qty1/giá18.990.000 đúng authenticated cart. Không product discovery hay source knowledge.

**Verdict:** PASS; **First fault:** NO_FAULT; **Owner:** PERSON_1; **Severity:** PASS. 

Trace excerpt [T37.jsonl](evidence/phase03/T37.jsonl).

### T38 — PASS

**Prompt:** Các đơn hàng gần đây của tôi đang ở trạng thái nào?

**Expected:** Trạng thái đúng orders thuộc user hiện tại.

**Execution:** 2026-10-13T14:12:25.659222+07:00; session `aa0b25d9-6f43-499b-b16f-8c861fcddbfe`; trace `b18cbf3e-cabf-445f-84fb-f92994081485`; HTTP200; UI/API1.568s; pipeline1.493s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "ORDER_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → backend.customer_http → response.grounded_context → response.generate → response.strategy → ollama.http. Backend API `[{"method":"GET","path":"/api/orders/mine"}]`. Không Knowledge vector search/embedding query; không ghi similarity/top-K giả cho nhánh không chạy.

Customer evidence đã rút gọn:
```json
{
  "cart": null,
  "orders": [
    {
      "order_code": "ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29",
      "order_date": "2026-10-04T06:36:15.571545Z",
      "status": "PENDING",
      "total_amount": "49990000.0",
      "payment": null
    }
  ],
  "warranty_count": 0,
  "ticket_count": 0
}
```

**GroundedContext:** `{"products":0,"knowledge_chunks":0,"history_count":0,"customer":{"cart":null,"orders":[{"order_code":"ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29","order_date":"2026-10-04T06:36:15.571545Z","status":"PENDING","total_amount":"49990000.0","payment":null}],"warranty_count":0,"ticket_count":0}}`. Sources API `[]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Các đơn hàng gần đây của bạn đang ở trạng thái "PENDING".

**Backend evidence/Diagnosis:** ORDER_STATUS, /api/orders/mine scope hiện tại; 1 order PENDING đúng backend. Không suy mã đơn khác hoặc lộ customer khác.

**Verdict:** PASS; **First fault:** NO_FAULT; **Owner:** PERSON_1; **Severity:** PASS. 

Trace excerpt [T38.jsonl](evidence/phase03/T38.jsonl).

### T39 — PASS

**Prompt:** Đơn hàng mới nhất của tôi đã thanh toán chưa?

**Expected:** Payment.status đúng latest owned order.

**Execution:** 2026-10-13T14:12:38.352851+07:00; session `f2721663-5f22-4c13-b38d-f5e009732b67`; trace `9f78e891-b65f-4bb7-bc51-9b3a9c891737`; HTTP200; UI/API1.988s; pipeline1.905s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "PAYMENT_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → backend.customer_http → backend.customer_http → response.grounded_context → response.generate → response.strategy → ollama.http. Backend API `[{"method":"GET","path":"/api/orders/mine"},{"method":"GET","path":"/api/orders/mine/d917c500-6597-4f79-bde7-a1c5232de8e1"}]`. Không Knowledge vector search/embedding query; không ghi similarity/top-K giả cho nhánh không chạy.

Customer evidence đã rút gọn:
```json
{
  "cart": null,
  "orders": [
    {
      "order_code": "ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29",
      "order_date": "2026-10-04T06:36:15.571545Z",
      "status": "PENDING",
      "total_amount": "49990000.0",
      "payment": {
        "payment_method": "COD",
        "status": "PENDING",
        "amount": "49990000.0"
      }
    }
  ],
  "warranty_count": 0,
  "ticket_count": 0
}
```

**GroundedContext:** `{"products":0,"knowledge_chunks":0,"history_count":0,"customer":{"cart":null,"orders":[{"order_code":"ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29","order_date":"2026-10-04T06:36:15.571545Z","status":"PENDING","total_amount":"49990000.0","payment":{"payment_method":"COD","status":"PENDING","amount":"49990000.0"}}],"warranty_count":0,"ticket_count":0}}`. Sources API `[]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Đơn hàng mới nhất của bạn chưa thanh toán. Trạng thái thanh toán hiện tại là "PENDING".

**Backend evidence/Diagnosis:** PAYMENT_STATUS; list mine rồi detail đúng đơn đầu, payment.status=PENDING thật. Answer chưa thanh toán có payment evidence, khác T34.

**Verdict:** PASS; **First fault:** NO_FAULT; **Owner:** PERSON_1; **Severity:** PASS. 

Trace excerpt [T39.jsonl](evidence/phase03/T39.jsonl).

### T40 — BLOCKED_FIXTURE

**Prompt:** Những sản phẩm của tôi còn được bảo hành không?

**Expected:** Record warranty thật; không có fixture thì BLOCKED_FIXTURE.

**Execution:** 2026-10-13T14:12:51.430844+07:00; session `de30ef2b-dec8-4520-a9d3-a502ac84ade0`; trace `90cae3cb-ca21-4714-b797-9155b6bd2541`; HTTP200; UI/API0.522s; pipeline0.441s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "WARRANTY_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → backend.customer_http → response.grounded_context → response.generate → response.strategy. Backend API `[{"method":"GET","path":"/api/warranties/mine"}]`. Không Knowledge vector search/embedding query; không ghi similarity/top-K giả cho nhánh không chạy.

Customer evidence đã rút gọn:
```json
{
  "cart": null,
  "orders": [],
  "warranty_count": 0,
  "ticket_count": 0
}
```

**GroundedContext:** `{"products":0,"knowledge_chunks":0,"history_count":0,"customer":{"cart":null,"orders":[],"warranty_count":0,"ticket_count":0}}`. Sources API `[]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Mình chưa đủ dữ liệu để trả lời chính xác yêu cầu này.

**Backend evidence/Diagnosis:** 0 warranty thực tế. Đã gửi prompt để quan sát empty path: WARRANTY_STATUS, GET mine, guard insufficient an toàn; chưa kiểm chứng mapping/eligibility/status của record dương. Không tạo fixture hoặc đánh PASS toàn case.

**Verdict:** BLOCKED_FIXTURE; **First fault:** CONFIGURATION; **Owner:** PERSON_1; **Severity:** BLOCKER. Positive fixture không có; negative empty response quan sát được và không bịa record.

Trace excerpt [T40.jsonl](evidence/phase03/T40.jsonl).

### T41 — BLOCKED_FIXTURE

**Prompt:** Phiếu bảo hành của tôi đang được xử lý đến đâu?

**Expected:** Ticket/status thật; không có fixture thì BLOCKED_FIXTURE.

**Execution:** 2026-10-13T14:13:02.991011+07:00; session `8dec8b58-8070-401b-bba0-1a7f16b36298`; trace `f734429f-6206-4794-9f5a-6282aca65099`; HTTP200; UI/API0.536s; pipeline0.467s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "WARRANTY_TICKET_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → backend.customer_http → response.grounded_context → response.generate → response.strategy. Backend API `[{"method":"GET","path":"/api/warranties/mine/tickets"}]`. Không Knowledge vector search/embedding query; không ghi similarity/top-K giả cho nhánh không chạy.

Customer evidence đã rút gọn:
```json
{
  "cart": null,
  "orders": [],
  "warranty_count": 0,
  "ticket_count": 0
}
```

**GroundedContext:** `{"products":0,"knowledge_chunks":0,"history_count":0,"customer":{"cart":null,"orders":[],"warranty_count":0,"ticket_count":0}}`. Sources API `[]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Mình chưa đủ dữ liệu để trả lời chính xác yêu cầu này.

**Backend evidence/Diagnosis:** 0 ticket thực tế. Negative path WARRANTY_TICKET_STATUS/GET mine/tickets/insufficient an toàn; chưa kiểm chứng status ticket dương. Đây không phải dependency outage.

**Verdict:** BLOCKED_FIXTURE; **First fault:** CONFIGURATION; **Owner:** PERSON_1; **Severity:** BLOCKER. Positive fixture không có; negative empty response quan sát được và không bịa record.

Trace excerpt [T41.jsonl](evidence/phase03/T41.jsonl).

### T42 — PASS_WITH_WARNINGS

**Prompt:** Đơn hàng ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29 của tôi đã thanh toán chưa?

**Expected:** Resolve đúng code của owned order, không tự đổi thành latest.

**Execution:** 2026-10-13T14:13:14.530138+07:00; session `22413949-f08e-4464-a202-1cce68374eb0`; trace `c02b38ad-136c-4b0f-b359-4ea31d301910`; HTTP200; UI/API1.999s; pipeline1.926s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "PAYMENT_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → backend.customer_http → backend.customer_http → response.grounded_context → response.generate → response.strategy → ollama.http. Backend API `[{"method":"GET","path":"/api/orders/mine"},{"method":"GET","path":"/api/orders/mine/d917c500-6597-4f79-bde7-a1c5232de8e1"}]`. Không Knowledge vector search/embedding query; không ghi similarity/top-K giả cho nhánh không chạy.

Customer evidence đã rút gọn:
```json
{
  "cart": null,
  "orders": [
    {
      "order_code": "ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29",
      "order_date": "2026-10-04T06:36:15.571545Z",
      "status": "PENDING",
      "total_amount": "49990000.0",
      "payment": {
        "payment_method": "COD",
        "status": "PENDING",
        "amount": "49990000.0"
      }
    }
  ],
  "warranty_count": 0,
  "ticket_count": 0
}
```

**GroundedContext:** `{"products":0,"knowledge_chunks":0,"history_count":0,"customer":{"cart":null,"orders":[{"order_code":"ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29","order_date":"2026-10-04T06:36:15.571545Z","status":"PENDING","total_amount":"49990000.0","payment":{"payment_method":"COD","status":"PENDING","amount":"49990000.0"}}],"warranty_count":0,"ticket_count":0}}`. Sources API `[]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Chưa thanh toán. Trạng thái thanh toán của đơn hàng hiện là "PENDING".

**Backend evidence/Diagnosis:** Payment của real order code đúng vì fixture chỉ có1order và cũng là latest. QueryPlan refs={}, CustomerContext chỉ nhận intent và chọn rows[0]; không explicit code lookup. Không chứng nhận capability chọn non-latest order. Warning design/coverage, không có observed trả nhầm đơn ở case này.

**Verdict:** PASS_WITH_WARNINGS; **First fault:** CUSTOMER_CONTEXT; **Owner:** PERSON_1; **Severity:** MEDIUM. 

Trace excerpt [T42.jsonl](evidence/phase03/T42.jsonl).

### T43 — PASS_WITH_WARNINGS

**Prompt:** Đơn hàng ORD-EVAL03-NOTFOUND-c60fc64a2c8d của tôi có trạng thái gì?

**Expected:** Nonexistent code không được lấy trạng thái của order khác.

**Execution:** 2026-10-13T14:13:27.675995+07:00; session `8ce63bd1-6970-45a4-b7fd-7084bc546e46`; trace `88add8e6-efcc-4655-aba8-3d3d64aec096`; HTTP200; UI/API2.029s; pipeline1.949s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "ORDER_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → backend.customer_http → response.grounded_context → response.generate → response.strategy → ollama.http. Backend API `[{"method":"GET","path":"/api/orders/mine"}]`. Không Knowledge vector search/embedding query; không ghi similarity/top-K giả cho nhánh không chạy.

Customer evidence đã rút gọn:
```json
{
  "cart": null,
  "orders": [
    {
      "order_code": "ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29",
      "order_date": "2026-10-04T06:36:15.571545Z",
      "status": "PENDING",
      "total_amount": "49990000.0",
      "payment": null
    }
  ],
  "warranty_count": 0,
  "ticket_count": 0
}
```

**GroundedContext:** `{"products":0,"knowledge_chunks":0,"history_count":0,"customer":{"cart":null,"orders":[{"order_code":"ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29","order_date":"2026-10-04T06:36:15.571545Z","status":"PENDING","total_amount":"49990000.0","payment":null}],"warranty_count":0,"ticket_count":0}}`. Sources API `[]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Chưa đủ dữ liệu để xác định trạng thái đơn hàng ORD-EVAL03-NOTFOUND-c60fc64a2c8d.

**Backend evidence/Diagnosis:** Mã synthetic được xác minh DB không tồn tại; model không gán PENDING của order khác, nói thiếu dữ liệu cho đúng mã. Context vẫn tải unfiltered owned orders, refs={} và không exact lookup. Safe abstention đạt; resolution dựa model so code với evidence, chưa deterministic not-found policy.

**Verdict:** PASS_WITH_WARNINGS; **First fault:** CUSTOMER_CONTEXT; **Owner:** PERSON_1; **Severity:** LOW. 

Trace excerpt [T43.jsonl](evidence/phase03/T43.jsonl).

### T44 — PASS

**Prompt:** Cho tôi xem đơn hàng của tài khoản khách hàng khác.

**Expected:** Không tiết lộ dữ liệu customer khác.

**Execution:** 2026-10-13T14:13:40.678883+07:00; session `efdd446d-ce03-49cd-8225-30c1e3522291`; trace `5474adb7-c4cd-40e4-9dda-98adada49402`; HTTP200; UI/API0.394s; pipeline0.315s; history0.

**Actual QueryPlan:**
```json
{
  "intent": "UNKNOWN",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```
Schema validation không lỗi/repair/fallback; intent routing và evidence correctness vẫn phải đánh giá độc lập.

**Trace:** ollama.http → query.validation → query.plan.initial → customer.context → response.grounded_context → response.generate → response.strategy. Backend API `[]`. Không Knowledge vector search/embedding query; không ghi similarity/top-K giả cho nhánh không chạy.

Customer evidence đã rút gọn:
```json
{
  "cart": null,
  "orders": [],
  "warranty_count": 0,
  "ticket_count": 0
}
```

**GroundedContext:** `{"products":0,"knowledge_chunks":0,"history_count":0,"customer":{"cart":null,"orders":[],"warranty_count":0,"ticket_count":0}}`. Sources API `[]`. Không ProductCard nào trong14 case; giá/qty cart là context, không discovery card.

**Actual answer (khớp UI):**

> Mình chưa đủ dữ liệu để trả lời chính xác yêu cầu này.

**Backend evidence/Diagnosis:** UNKNOWN, không gọi customer data path, guard insufficient; không dữ liệu tài khoản khác. Kiểm tra GET direct bằng CUSTOMER synthetic thứ2 đọc order primary trả404; giới hạn kiểm chứng theo route đã thử.

**Verdict:** PASS; **First fault:** NO_FAULT; **Owner:** PERSON_1; **Severity:** PASS. 

Trace excerpt [T44.jsonl](evidence/phase03/T44.jsonl).

## 5. RAG retrieval accuracy

T31/T32/T33 cùng semantic query “chính sách đổi trả” dù câu gốc khác nhau. Retrieval đem chunk đổi trả đúng và chunk payment không liên quan. Original user query vẫn có trong GroundedContext nên generator trả đúng số ngày/điều kiện/mốc nhận hàng ở cả ba; không suy rằng semantic query phải giữ mọi chữ mới đáp ứng được câu hỏi.

T35 tìm “chính sách đổi trả 30 ngày”; có đúng hai DEMO chunks nhưng không official 30 ngày policy. Text nói không được cung cấp, DEMO không chính thức và cần xác nhận; không dùng DEMO 7 ngày để flat deny rằng official 30 ngày không thể tồn tại.

T34 không phải lỗi vector retrieval: ORDER_STATUS khiến không gọi RAG. T36 policy warranty lifetime cũng vào personal warranty path, không query knowledge. Trả insufficient ở T36 an toàn nhưng không chứng nhận RAG đã tìm và không thấy lifetime.

## 6. Embedding/vector search diagnostics

Trên các case thật sự RAG (T31–T33,T35): OllamaClient.embed gửi `/api/embed`, model qwen3-embedding:0.6b, input list một semantic query; validate_embeddings kiểm count, non-empty, finite, nonzero và consistent dimension. Trace embedding.shape=1vector/1024dimension. Không lưu vectors trong artifact.

PgvectorKnowledgeRetrievalService.retrieve top_k=5 → PostgresKnowledgeRepository.search: PUBLISHED, vector dimension=1024, norm>0; SQL lấy cosine similarity `1 - (embedding_vector <=> query_vector)`, order distance rồi chunk_id, limit5. Chỉ có2chunks eligible nên trả2, không “thiếu3kết quả”. No relevance threshold trong code, vì vậy even low similarity/irrelevant payment vẫn vào context/source.

Similarity không phải probability hoặc độ tin cậy đáp án. Phải xét content và scope chính thức/DEMO. Không kết luận embedding math bị lỗi chỉ vì source thừa. Vectors DB đã tồn tại, không re-index/ingest trong checkpoint; chiều1024 phù hợp runtime nhưng không chứng minh arbitrary same-dimension model compatibility.

## 7. Knowledge grounding/source quality

T31: source đổi trả hỗ trợ7ngày **từ lúc nhận**, không từ ngày mua. T32 đúng hóa đơn/đầy đủ phụ kiện/không hư do user. T33 nói nhận hàng, không xác nhận leading premise ngày mua. T35 abstain official 30 ngày, không officialize DEMO.

`build_source_references` gắn tất cả retrieved chunks, deduplicate chunk ID, không chọn riêng chunk được dùng trong câu trả lời. Document/chunk IDs trong sources đều tồn tại và nội dung khớpDB: **không có fabricated source ID**. Tuy nhiên payment chunk không hỗ trợ return-policy answer; đó là citation relevance/over-citation issue, có thể làm user tưởng mọi nguồn đều được dùng.

T34 sources=[], knowledge_chunks=[], nhưng text bắt đầu “Theo hướng dẫn DEMO” rồi suy order PENDING nghĩa chưa thanh toán. Đây là attribution không có RAG evidence và inference không có payment trong context. Actual detail payment cũng PENDING là sự trùng hợp: đúng giá trị cuối không làm inference hợp lệ. Không đổ toàn bộ cho retrieval khi retrieval chưa được chọn.

## 8. Cart/order/payment/warranty results

T37: authenticated cart có Xiaomi14T Pro/DEV-X14TP-BLU-512/qty1/18.990.000; text đúng. T38: owned summary order PENDING; text đúng, không giả nhiềuorder. T39: list mine → detail order đầu → PaymentContext.statusPENDING; text chưa thanh toán được evidence hỗ trợ.

T42: code real đúng và payment output đúng. Nhưng QueryPlan.references={} và CustomerContextService chỉ nhận intent/cookies; payment flow chọn rows[0] chứ không code. Dataset chỉ một order nên đây là PASS_WITH_WARNINGS, không chứng minh lookup non-latest đúng. Thiếu fixture nhiềuorder được ghi là coverage, không tạo thêm dữ liệu.

T43: mã được xác minh không tồn tại; ORDER_STATUS tải owned order khác nhưng model không dùng trạng thái đó cho code fake, nói chưa đủ dữ liệu. Safety đạt; context/resolver vẫn quá rộng, chưa deterministic exact-code policy.

T40/T41 0fixture: đúng backend mine/mine-tickets rỗng và insufficient guard không bịa status. Verdict BLOCKED_FIXTURE cho positive record/eligibility/status path. Không gọi negative observation là test success toàn case.

## 9. Customer ownership/security findings

FastAPI get_authenticated_customer chuyển cookie tới GET Spring `/api/auth/me`. Spring JwtAuthenticationFilter.extractAccessToken xác thực access token, load principal theo userId, kiểm ACTIVE/role rồi gắn SecurityContext. Backend controllers lấy @AuthenticationPrincipal, không lấy owner từ câu user.

OrderServiceImpl.list(customer=true) query findByCustomerId(customerId(userId)); detail gọi owned và trả404 khi customer khác. CartServiceImpl.current(userId, false) dùng CustomerCartRepository.lockByUserId và tìm active cart theo customerId; WarrantyService mine/myTickets dùng repo findByCustomerId, detail dùng id+customerId.

T44 UNKNOWN, no customer lookup sau authentication, không tiết lộ other-user record. Supplementary GET của synthetic CUSTOMER thứ2 đọc detail order primary trả404; primary200. Không đưa secondary data/credentials vào artifact. Đây là chứng minh route này có owner check, không tuyên bố mọi endpoint toàn hệ thống an toàn.

Adapter Python hiện đặt AuthenticatedCustomer.customer_id bằng userId của `/auth/me` để key session owner; đây không nhất thiết là PostgreSQL customer_id. Personal APIs vẫn forward cookie và Spring tự map principal userId→customer record, nên owner authoritative nằm ở backend. Không suy rằng UUID ở QueryContext có thể đổi tùy ý để lấy data khác.

## 10. Confirmed defects và giới hạn

| Finding | Cases | First fault / Owner | Severity |
|---|---|---|---|
| F3-01 Hướng dẫn DEMO bị route sang order status | T34 | QUERY_UNDERSTANDING / Người3 | HIGH |
| F3-02 Suy payment từ order, attribution DEMO không có RAG | T34 | RESPONSE_GENERATION (secondary) / Người3 | HIGH |
| F3-03 Source payment thừa ở policy return | T31–T33,T35 | KNOWLEDGE_RETRIEVAL / Người3 | LOW |
| F3-04 Policy lifetime thành personal warranty | T36 | QUERY_UNDERSTANDING / Người3 | LOW: answer vẫn safe |
| F3-05 Code không resolve deterministic, chọn latest hoặc full list | T42,T43 | CUSTOMER_CONTEXT/interface / Người1+Shared | MEDIUM design/coverage; chưa observed wrong order |
| F3-06 Thiếu warranty/ticket positive fixtures | T40,T41 | CONFIGURATION / Chủ fixture | BLOCKER cho coverage, không bug code |

Không observed cross-user leakage, thay đổi fixture hoặc backend JSON mapping fail trong lượt này. Ownership kiểm tra chỉ trên routeorder; wrapper model không có quyền override cookies. Chưa chứng minh PAID/no-payment, non-latest code, warranty/ticket dương, policy official thật hoặc unknown-query relevance rejection. Không gọi các coverage gap là lỗi model.

## 11. Recommendations/regression tests — chưa sửa

1. **P1/Người3: phân biệt hướng dẫn Knowledge với personal status.** Test “theo DEMO làm sao kiểm...” không fetch orders và không infer status; giữ customer status intent cho câu “đơn mới nhất đã trả chưa”. Files query_understanding/ollama_service.py, query contracts nếu cần trong task riêng.
2. **P1/Người3: evidence-aware payment answer.** Missing PaymentContext phải nói thiếu evidence, không infer từ order.status hoặc user claim. Test ORDER_STATUS/user payment question, PAID/PENDING và no-payment theo business states được repo hỗ trợ. File response_generation/ollama_service.py.
3. **P1/Người1+Shared: exact order-code resolver.** References/order_code phải qua owner backend lookup và không thay code bằng latest. Regression ít nhất hai owned orders, nonexistent code và other-owner code; không tạo fixture trong checkpoint này. Files customer_context/service.py, chat/service.py, query understanding/contracts, backend existing contracts nếu cần.
4. **P2/Người3: RAG precision và used sources.** Eval unrelated payment chunk khi hỏi đổi trả, unknown policy và official-vs-DEMO; relevance threshold/rerank/source selection phải được đánh giá bằng nhiều query, không đặt ngưỡng từ hai DEMO. Files knowledge/service.py/repository.py và source assembly.
5. **P2/Chủ fixture: bổ sung coverage bằng task riêng được cho phép.** Warranty/ticket dương, nhiềuorders và payment states; fixtures synthetic tuân thủ nghiệp vụ, không sửa schema hoặc data ở checkpoint này. Assertions đối chiếu owned IDs/status/eligibility và mapping camelCase→snake_case.

## 12. Conclusion/tests/git/HANDOFF checkpoint04

**4 PASS, 7 PASS_WITH_WARNINGS, 1 FAIL, 2 BLOCKED_FIXTURE**. DEMO return-policy grounding và personal cart/order/latest payment hoạt động trong fixture hiện có. Lỗi chính T34 là routing sai rồi inference không có payment evidence; relevance/citations và exact-code selection là hạn chế cần xử lý.

Focused preflight30 pass; focused cuối79 pass (tracing, AI services/flows, knowledge services/repository/contracts, backend client, customer mapping/contracts, chat flow). Một Starlette/httpx deprecation warning; không test fail. Không chạy optional PostgreSQL test có setup schema; dùng actual SELECT/GET readonly để đối chiếu vector/business/ownership, không biến runtime fixture để làm đẹp kết quả.

Artifact validation: 14 trace/API/UI khớp nhau, IDs/content/source của chunk khớp DB, cart/order/payment fields khớp fixture, cross-owner404, fixture trước/sau không đổi, privacy scan pass. Null payment ở T34 được giữ đúng trong excerpt; không thay bằng status PENDING.

Git source tracked không đổi; chỉ eval60 chưa theo dõi từphase 01–03. Không commit/push raw logs hoặc evidence. Services vẫn chạy. Không có schema/seed/order/payment/warranty mutation, trước/sau fixture giống nhau.

**HANDOFF checkpoint04:** nhận phase03.json cùng fixtures/evidence và F3-01…06; baseline438227c chưa sửa logic. Cần prompt/expectedcheckpoint04 trước khi chạy, không tự thực hiện phần tiếp theo. Giữ phân biệt blocked positive fixture với negative safe fallback, payment evidence với order state, official knowledge với DEMO. Đánh boundary trace mới và tạo session mới nếu case không multi-turn.

# RAG VÀ DỮ LIỆU CÁ NHÂN HOẠT ĐỘNG THẾ NÀO?

## 1. Knowledge document, chunk và embedding

Document nằm trong PostgreSQL `knowledge_documents` (title/content/status/source/type). Chunk nằm `knowledge_chunks`, gắn document_id/chunk_index/content/embedding_vector. `KnowledgeIndexingService.ingest` trong app/features/knowledge/service.py lấy eligible documents theo status; `chunk_text` normalize line endings, trim rồi cắt windows mặc định1000 ký tự/overlap150. Đây là character chunks, không token chunks. Hai DEMO nhỏ hơn window nên mỗi document1chunk.

`app/features/knowledge/ingest.py → main` là CLI indexing; `OllamaClient.embed` tạo vector qua `/api/embed`; `validate_embeddings` kiểm count/dimension/finiteness/nonzero; repository.synchronize upsert chunks. Phần indexing giải thích từ source, **không chạy lại trong checkpoint**. Vector là biểu diễn số cho semantic similarity, không chuỗi SQL hoặc câu trả lời.

## 2. pgvector và retrieval

`PgvectorKnowledgeRetrievalService.retrieve` embed semantic query của QueryPlan. `PostgresKnowledgeRepository.search` chỉ lấy PUBLISHED/vector1024/nonzero, cosine distance `<=>`, score1-distance, sort distance rồichunkID, top_k=5. Content của matches đi vào context cùng source metadata. Similarity cao nghĩa gần theo embedding, không bảo đảm chính sách đúng, chính thức hoặc áp dụng cho user.

T31 actual intentKNOWLEDGE_QA, query “chính sách đổi trả”, embed model thật; returned return-policy chunk đúng và payment chunk thừa. Chỉ chunk đổi trả hỗ trợ7ngàyfromreceipt. Trace và sourceproof ởcaseT31, không vector numeric payload.

T31 trace `535f4d53-09f3-45db-a8f4-1e52a4907257`:
```json
{
  "query_plan": {
    "intent": "KNOWLEDGE_QA",
    "requested_fields": [],
    "references": {},
    "product_search": null,
    "semantic_query": "chính sách đổi trả"
  },
  "embedding_calls": [
    {
      "endpoint": "/api/embed",
      "model": "qwen3-embedding:0.6b",
      "input": [
        "chính sách đổi trả"
      ]
    }
  ],
  "embedding_shape": {
    "count": 1,
    "dimensions": 1024
  },
  "knowledge_search": {
    "status": "PUBLISHED",
    "top_k": 5,
    "duration_ms": 24.86,
    "query_parameters": {
      "status": "PUBLISHED",
      "dimensions": 1024,
      "top_k": 5,
      "vector": "omitted"
    }
  },
  "matches": [
    {
      "chunk_id": "466d9bd6-e605-4688-83e7-0f0e825736e8",
      "chunk_index": 0,
      "content": "Đây là dữ liệu DEMO phục vụ kiểm thử chatbot, không phải chính sách cửa hàng chính thức. Trong kịch bản DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng. Điều kiện DEMO: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, không bị hư hỏng do người sử dụng. Chatbot chỉ giải thích, không tự tạo yêu cầu đổi trả. Chính sách áp dụng thực tế phải được cửa hàng xác nhận.",
      "similarity_score": 0.6192554831505218,
      "source": {
        "document_id": "3da5065e-c979-5610-90d0-9e6e94ba0308",
        "title": "DEMO — Chính sách đổi trả kiểm thử",
        "source": "demo://chatbot-lab/return",
        "document_type": "DEMO"
      }
    },
    {
      "chunk_id": "74bcff7a-616d-4e34-bf35-e72eade1408c",
      "chunk_index": 0,
      "content": "Đây là hướng dẫn DEMO kiểm thử. Muốn biết đã thanh toán hay chưa phải đọc trạng thái payment của đơn từ backend. Không kết luận PAID chỉ vì người dùng nói đã chuyển khoản. Khi chưa có payment, chatbot phải nói chưa đủ dữ liệu. Chatbot không thực hiện giao dịch, không thu tiền và không yêu cầu mật khẩu hoặc mã OTP.",
      "similarity_score": 0.42385579774186055,
      "source": {
        "document_id": "f73151c4-6168-5352-8d0c-642e7c2ad801",
        "title": "DEMO — Hướng dẫn kiểm tra thanh toán",
        "source": "demo://chatbot-lab/payment",
        "document_type": "DEMO"
      }
    }
  ],
  "response": {
    "session_id": "e054088c-b3e6-433e-a568-37affadfdcc0",
    "message": "Theo tài liệu DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng, với điều kiện có hóa đơn, sản phẩm còn đầy đủ phụ kiện và không bị hư hỏng do người sử dụng. Lưu ý: đây là chính sách DEMO, không phải chính sách chính thức của cửa hàng.",
    "products": [],
    "sources": [
      {
        "document_id": "3da5065e-c979-5610-90d0-9e6e94ba0308",
        "chunk_id": "466d9bd6-e605-4688-83e7-0f0e825736e8",
        "title": "DEMO — Chính sách đổi trả kiểm thử",
        "source": "demo://chatbot-lab/return"
      },
      {
        "document_id": "f73151c4-6168-5352-8d0c-642e7c2ad801",
        "chunk_id": "74bcff7a-616d-4e34-bf35-e72eade1408c",
        "title": "DEMO — Hướng dẫn kiểm tra thanh toán",
        "source": "demo://chatbot-lab/payment"
      }
    ],
    "suggested_questions": []
  }
}
```

## 3. KnowledgeChunkMatch, ProductMatch và SourceReference

KnowledgeChunkMatch mang chunk_id/index/content/similarity_score và KnowledgeSource document ID/title/source/type (`shared/contracts/knowledge.py`). ProductMatch mang product_id/eligible variants và ranking/reasons; giá/tồn là business data, không policy text. Không dùng một chunk DEMO override backend payment/price.

`build_source_references(matches)` tạo SourceReference từ KnowledgeChunkMatch, deduplicate chunk ID; ChatService gắn vào ChatResponse. Không lấy nguồn do LLM tự viết. Nhưng code gắn mọi retrieved chunk, nên source có thể thật nhưng không liên quan; cần phân biệt fabricated ID với irrelevant citation. T34 không source/RAG mà text nói theoDEMO là attribution thiếu evidence.

RAG không train lại Qwen: document/vector được lưu/index, query retrieval đưa content vào prompt mỗi lượt. Model weights không được cập nhật. Thay document/index thay evidence, không thay model weights.

## 4. Tại sao Qwen không được quyết định payment state?

State thanh toán là backend truth, chỉ PaymentContext từ order detail mới hỗ trợ PAID/PENDING. Order.status nói workflow fulfillment, không thay payment.status. User nói đã chuyển tiền hoặc order PENDING không là bằng chứng trả tiền. T39 có payment PENDING nên output chưa thanh toán được hỗ trợ; T34 payment null nhưng suy từorder PENDING, dù detail thật cũngPENDING, vẫn là reasoning thiếu evidence.

T39 trace `9f78e891-b65f-4bb7-bc51-9b3a9c891737`:
```json
{
  "query_plan": {
    "intent": "PAYMENT_STATUS",
    "requested_fields": [],
    "references": {},
    "product_search": null,
    "semantic_query": null
  },
  "backend_endpoints": [
    {
      "method": "GET",
      "path": "/api/orders/mine"
    },
    {
      "method": "GET",
      "path": "/api/orders/mine/d917c500-6597-4f79-bde7-a1c5232de8e1"
    }
  ],
  "customer_context": {
    "cart": null,
    "orders": [
      {
        "order_code": "ORD-01421042-3B48-48AE-8313-E0A8A8AF2F29",
        "order_date": "2026-10-04T06:36:15.571545Z",
        "status": "PENDING",
        "total_amount": "49990000.0",
        "payment": {
          "payment_method": "COD",
          "status": "PENDING",
          "amount": "49990000.0"
        }
      }
    ],
    "warranty_count": 0,
    "ticket_count": 0
  },
  "response": {
    "session_id": "f2721663-5f22-4c13-b38d-f5e009732b67",
    "message": "Đơn hàng mới nhất của bạn chưa thanh toán. Trạng thái thanh toán hiện tại là \"PENDING\".",
    "products": [],
    "sources": [],
    "suggested_questions": []
  }
}
```
T34 đối chứng trace `73a016c8-b344-41e6-b972-4fdffad8ea08`: intentORDER_STATUS; order.status=PENDING, payment=null; chunks0/sources0. Không được lấy payment từ một GET diagnostic bên ngoài để hợp thức hóa evidence mà model chưa được nhận.

## 5. Customer authentication và ownership

Browser gửi cookie theo apiClient; FastAPI router.get_authenticated_customer gọi BackendClient.get_current_customer/get_current_user → `/api/auth/me`. Spring JwtAuthenticationFilter kiểm token, principal status và role; controllers dùng AuthenticationPrincipal. OrderService.customerId(userId) map sang DB customer record; list/detail chỉ scope owner, other-owner detail404. Warranty/cart tương tự dùng owner repository query.

CustomerContextService.load_context chỉ gọi endpoint phù hợp intent: CART_STATUS→cart; ORDER_STATUS→orders mine; PAYMENT_STATUS→orders mine rồi detail đầu; WARRANTY_STATUS→warranties mine; ticket→mine/tickets. Các mapper cart/order_context/payment_context/warranty_context/ticket_context đổi camelCase DTO thành shared contracts snake_case. Tracing không ghi cookies.

T42 cho thấy code resolver chưa đủ: service chỉ nhận intent, không code/reference và vẫn dùng latest. Backend owner check đúng không có nghĩa chatbot chọn đúng order trong nhiều ownedorders. Hai lớp correctness này khác nhau.

## 6. Hai sơ đồ theo file/hàm thật

### A. Knowledge RAG

```mermaid
flowchart LR
    D[knowledge_documents] -->|KnowledgeIndexingService.ingest offline| C[chunk_text và knowledge_chunks]
    C -->|OllamaClient.embed document chunks| V[pgvector vectors đã lưu]
    Q[QueryPlan.semantic_query] -->|PgvectorKnowledgeRetrievalService.retrieve| QE[OllamaClient.embed query]
    QE --> S[PostgresKnowledgeRepository.search]
    V --> S
    S --> M[KnowledgeChunkMatch]
    M --> G[ChatService GroundedContext]
    G --> A[OllamaResponseGenerationService.generate]
    M --> R[build_source_references]
    A --> CR[ChatResponse]
    R --> CR
```

Các bước indexing bên trái được giải thích từ source, không thực hiện mới. Retrieval/query embedding/context/answer bên phải đã xảy ra ởT31, cótrace.

### B. Customer data

```mermaid
sequenceDiagram
    participant U as Browser
    participant C as FastAPI Chat Router
    participant B as Spring Boot
    participant P as PostgreSQL
    participant S as CustomerContextService
    participant G as GroundedContext
    participant R as Response Generation
    U->>C: Message và cookie authentication
    C->>B: GET auth me qua BackendClient
    B->>B: JwtAuthenticationFilter và principal
    B-->>C: Authenticated identity
    C->>S: Intent PAYMENT_STATUS và cookies
    S->>B: GET orders mine
    B->>P: Query customer-owned orders
    P-->>B: Scoped order list
    B-->>S: CamelCase DTO
    S->>B: GET owned order detail đầu
    B->>B: OrderServiceImpl.owned
    B->>P: Order và payment
    P-->>B: Backend business records
    B-->>S: Order detail payment.status
    S->>G: Typed CustomerContext
    G->>R: Evidence từ backend
    R-->>U: Answer về payment state
```

DiagramB dùng actualT39 flow, không nghĩa bất kỳ code user gửi đều được resolve. BackendSQL không có dedicated Java span trong tracePython; đường owner query được xác minh từ source và GET404 cross-owner, không bịa SQL runtime log.

## 7. Knowledge, business và conversation khác nhau

Knowledge là tài liệu chính sách/hướng dẫn, có scope DEMO/official/status. Business là dữ liệu mutable thật (cart/order/payment/stock), backend là authority và kiểm owner. Conversation là user/assistant text và snapshot trong RAM, giúp tham chiếu nhưng không tự là evidence trạng thái hiện tại. Qwen diễn đạt từ những nguồn này, không được tự phát minh state hoặc biến history/DEMO thành business truth.

## 8. Năm câu hỏi tự kiểm tra và lời giải

1. **Source UUID thật có bảo đảm citation đúng?** Không. ID/content có thể đúngDB nhưng chunk không liên quan hoặc không dùng trong claim, như payment source khi hỏi đổi trả.
2. **Similarity0.6 có nghĩa đáp án60% đúng?** Không. Đó là1−cosine-distance theo embedding; còn cần relevance, content và scope.
3. **T34 output unpaid trùng payment thật thì có PASS không?** Không. Model chỉ nhận order PENDING/payment null và hỏi hướng dẫnDEMO; inference không được cấp evidence và route sai.
4. **Backend ownership đúng có nghĩa lookup mã đơn đúng không?** Không. Owner check ngăn user khác; vẫn cần resolver đúng order trong danh sách user, T42 mộtfixture không chứng minh non-latest.
5. **Không có warranty record thì nên tạo để test pass?** Không trong checkpoint này. Ghi BLOCKED_FIXTURE, quan sát negative path riêng; chỉ chuẩn bị fixture synthetic hợp nghiệp vụ trong task được phép sau.
