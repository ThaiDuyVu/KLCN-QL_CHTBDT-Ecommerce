# Chatbot Runtime Debug Report

Task `c2c_chatbot_runtime_debug_8cases` · iteration 1 · 13/10/2026 (Asia/Ho_Chi_Minh).

**Đã chạy 9 request cho 8 nhóm case: 7 PASS, 2 FAIL, 0 BLOCKED. Tất cả HTTP 200 nhưng 2 câu trả lời không đạt hành vi kỳ vọng.** Đây là một lượt quan sát cho mỗi prompt/context, không phải chứng minh tỷ lệ ổn định thống kê. Case 4 gồm 4A và 4B. Không sửa business logic, prompt, contract, retrieval, frontend, tracing, schema hoặc seed.

## 1. Environment

- Branch `dev/thai-duy-vu`, commit `d347a6f`; ahead 11 so với remote-tracking local, không fetch/push.
- Worktree trước/sau: chỉ `?? chatbot-service/debug/` (báo cáo iteration 0 được thay bằng kết quả iteration 1); không có thay đổi source được theo dõi. Helper/chụp ảnh/raw observations ở `.local/diagnostic-8cases/`, không commit.
- Docker Engine 29.8.1, PostgreSQL pgvector healthy. Khởi động bằng script hiện có `bash scripts/start.sh`.
- Backend :8080, frontend :5173, chatbot :8090, Ollama :11434 đều reachable. Trình duyệt Chrome thật đăng nhập rồi gửi qua UI Chatbot Lab/Vite proxy, dùng cookie/CSRF của flow có sẵn; tài khoản loại CUSTOMER tổng hợp.
- AI=true; Product Advisor=real; chat `qwen3:4b-instruct`; embedding `qwen3-embedding:0.6b`. Hai model ready từ status và thực sự được gọi trong trace.
- Warehouse đã cấu hình, backend detail trả tồn kho tại kho đó. Product semantic index 16 record có vector; Knowledge 2 chunk DEMO có vector, 1024 chiều, status PUBLISHED. Đã đối chiếu DB nguồn bằng SELECT; không seed/index lại.
- APP_ENV=development, CHATBOT_TRACE_ENABLED=true, CHATBOT_TRACE_FILE_ENABLED=true đã có trong .env; không cần đổi config. Không có cờ payload độc lập: trace đang ghi payload đã che theo implementation.
- Sink thực: `.local/logs/chatbot-traces.jsonl` tại gốc repo; giữ nguyên lịch sử cũ. Boundary đầu run: 4 dòng, byte offset 160395, timestamp 2026-10-13T05:39:30.152973+00:00. Kết thúc: 13 dòng; 9 trace mới, không rotate trong lượt này.

## 2. Actual tracing implementation

Module `chatbot-service/app/shared/tracing/__init__.py`: ContextVar truyền qua asyncio.to_thread, span/decorator ghi input/output/status/offset_ms/duration_ms/parent_id. JSONL **một snapshot mỗi request**, không phải một dòng mỗi event. Trong excerpt giữ nguyên thứ tự events theo thời điểm bắt đầu; span cha nằm trước nhưng kết thúc sau con, nên không cộng duration cha/con.

Correlation: Chatbot Lab tạo header X-Chat-Trace-Id, response trả cùng UUID, JSONL có trace_id và session_id. Extraction đọc phần sau byte boundary rồi lọc cả hai UUID; không dùng trace API làm nguồn thay thế cho file. 9/9 ChatResponse bằng output chat.pipeline; UI message/card names bằng API; 0 lỗi JavaScript quan sát. Không có dropped_events/truncated trong 9 trace. Trace status đều ok: không đồng nghĩa câu trả lời đúng.

Actual events dùng trong run: request.accepted; chat.pipeline; conversation.context; query.understand; query.model_attempt; ollama.chat/http/http_status/result; query.validation; query.plan.initial/effective; product.search/cards/embedding/vector_ranking/sql; backend.product_http/product_result; customer.context; backend.customer_http; backend.http_status; knowledge.retrieve/vector_search/sql; embedding.shape; retrieval.summary; response.grounded_context/generate/strategy. Validation/repair/fallback/error event được implementation hỗ trợ nhưng không xuất hiện trong run này.

Focused tests hiện tại: `tests/test_tracing.py` (6 tests), không thấy thêm Python tracing-specific test file. Những giả định `tests/test_pipeline_tracing.py`, sink `.local/logs/chatbot.log`, cờ CHATBOT_TRACE_PAYLOADS và event schema chat.request.* trong iteration 0 là cũ, không phải lý do chặn iteration 1.

Excerpt ở `chatbot-runtime-run/*.jsonl` đã che identity/contact/credentials, bỏ vector và rút gọn raw customer HTTP DTO. Không ghi secrets, DB URL hoặc full personal backend DTO vào báo cáo. Prompt JSON nhúng cũng được sanitize. Bằng chứng nghiệp vụ chỉ là dữ liệu synthetic: SKU/giá/số lượng/trạng thái. Không stage/commit runtime logs.

## 3. Pipeline

```text
User -> Router -> ChatService -> Query Understanding -> QueryPlan
     -> Product / Knowledge / Customer Context -> GroundedContext
     -> Response Generation -> ChatResponse
```

Branch `deterministic_guard` kèm reason là tên thực trong code: greeting, no_matching_products, transaction_not_supported. Branch `ollama` là model-generated. Không đổi tên chúng thành enum không tồn tại.

## 4. Test matrix

PASS/FAIL xét điều kiện chính của case. Các hạn chế không làm sai đáp án chính được ghi riêng ở mục 9 và verdict QUESTIONABLE; NO_FAULT ở bảng nghĩa là không có lỗi làm case chính thất bại, không phủ nhận finding phụ.

| Case | Prompt | Actual intent | Retrieval path | Response branch | Result | First fault | Owner |
|---|---|---|---|---|---|---|---|
| case-01 | Xin chào | GREETING | none | greeting | PASS | NO_FAULT | PERSON_3 |
| case-02 | Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB. | PRODUCT_DISCOVERY | product | ollama | PASS | NO_FAULT | PERSON_2 |
| case-03 | Xiaomi 15 còn hàng không? | PRODUCT_DISCOVERY | product | no_matching_products | FAIL | QUERY_UNDERSTANDING | PERSON_3 |
| case-04a | Có loại rẻ hơn không? | PRODUCT_DISCOVERY | product | no_matching_products | PASS | NO_FAULT | PERSON_1 |
| case-04b | Có loại rẻ hơn không? | PRODUCT_DISCOVERY | product | ollama | FAIL | QUERY_UNDERSTANDING | PERSON_3 |
| case-05 | Theo tài liệu DEMO, điều kiện đổi trả trong bao nhiêu ngày? | KNOWLEDGE_QA | knowledge | ollama | PASS | NO_FAULT | PERSON_3 |
| case-06 | Giỏ hàng của tôi đang có những gì? | CART_STATUS | cart | ollama | PASS | NO_FAULT | PERSON_1 |
| case-07 | Đơn hàng mới nhất của tôi đã thanh toán chưa? | PAYMENT_STATUS | orders → detail.payment | ollama | PASS | NO_FAULT | PERSON_1 |
| case-08 | Hãy đặt ngay một laptop và thanh toán giúp tôi. | PRODUCT_DISCOVERY | product | transaction_not_supported | PASS | NO_FAULT | PERSON_1 |

## 5. Case-by-case analysis

### case-01 — 01-greeting

**Prompt:** Xin chào

**Expected:** GREETING, deterministic, không retrieval/card/source.

**A. Input:** 2026-10-13T12:39:30.438307+07:00; session `d82bd6f8-efaf-416e-8df5-7af44aecbf98`; trace `16b81aa4-7ed0-4425-817f-d2da902dc752`; history_count=0 (đếm conversation.context.recent_messages, không phải field không tồn tại request.started). HTTP 200; UI/API wall time 5.477s; trace duration 5.379s.

**B. Query Understanding — CORRECT.** QueryPlan thực:

```json
{
  "intent": "GREETING",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```

Không có effective ProductSearchPlan vì không đi nhánh sản phẩm.

Validation: 1 lần model attempt; không có validation_failed/repair/fallback. JSON hợp schema không đảm bảo đúng domain/ý định.

**Important trace sequence:** request.accepted → chat.pipeline → conversation.context → query.validation → query.plan.initial → customer.context → response.grounded_context → response.generate → response.strategy.

**C. Retrieval / business context — CORRECT.** Candidate pairs=0, unique products=0; semantic ranking=không; ProductMatch=0; ProductCard=0; knowledge chunks=0. Các số 0 ở nhánh không chạy không có nghĩa hệ thống đã query rồi trả rỗng.

**D. Grounding — CORRECT.** products=0; knowledge_chunks=0; customer summary={
  "cart_present": false,
  "cart_items": 0,
  "recent_orders": 0,
  "payment_statuses": []
}. Relevant history=0. ProductMatch evidence chứa IDs/variant SKU/giá/RAM/tồn kho, không có product_name/brand trong contract hiện tại.

**E. Response — CORRECT.** Branch `greeting`. Message thực (khớp UI):

> Xin chào! Mình có thể hỗ trợ tìm sản phẩm và giải đáp thông tin cửa hàng.

UI hiển thị 0 card: không có. Sources=0. Trace response == API: đã đối chiếu tự động.

**Diagnosis:** Greeting được xử lý bằng code; không gọi product/knowledge retrieval hoặc model tạo response.

**First fault:** NO_FAULT. **Owner:** PERSON_3. **Severity:** PASS. **Result:** PASS.

Trace excerpt: [case-01-greeting.jsonl](chatbot-runtime-run/case-01-greeting.jsonl).

### case-02 — 02-product-filter

**Prompt:** Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB.

**Expected:** Laptop giá ≤20.000.000 và RAM ≥16GB; mọi card là variant backend thật.

**A. Input:** 2026-10-13T12:39:46.965294+07:00; session `3a047273-76bc-44dc-bf1d-2c11af0f2976`; trace `a7c75313-7066-4a7a-a491-32311f057a8f`; history_count=0 (đếm conversation.context.recent_messages, không phải field không tồn tại request.started). HTTP 200; UI/API wall time 8.307s; trace duration 8.222s.

**B. Query Understanding — CORRECT.** QueryPlan thực:

```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": "20000000",
    "min_ram_gb": 16,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "laptop giá dưới 20 triệu, RAM ít nhất 16GB",
    "top_k": 5
  },
  "semantic_query": null
}
```

Effective search:
```json
{
  "product_search": {
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": "20000000",
    "min_ram_gb": 16,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "laptop giá dưới 20 triệu, RAM ít nhất 16GB",
    "top_k": 5
  },
  "below_minimum": false,
  "cheaper_followup": false
}
```

Validation: 1 lần model attempt; không có validation_failed/repair/fallback. JSON hợp schema không đảm bảo đúng domain/ý định.

**Important trace sequence:** request.accepted → chat.pipeline → conversation.context → query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.vector_ranking → backend.product_http → product.cards → backend.product_http → customer.context → response.grounded_context → response.generate → response.strategy.

**C. Retrieval / business context — CORRECT.** Candidate pairs=1, unique products=1; semantic ranking=có; ProductMatch=1; ProductCard=1; knowledge chunks=0. Các số 0 ở nhánh không chạy không có nghĩa hệ thống đã query rồi trả rỗng.

Top scores: [
  [
    "a96df51c-538c-4fa9-8018-6b49ca025b34",
    0.4710991244788898
  ]
].

Đối chiếu backend độc lập (cùng kho):
```json
[
  {
    "product_id": "a96df51c-538c-4fa9-8018-6b49ca025b34",
    "variant_id": "9ede9a3c-6406-4058-9ce0-414d19b7939f",
    "product_name": "Dell Inspiron 15",
    "sku": "DEV-DELL-I15-512",
    "effective_price": 15990000.0,
    "ram": "16GB",
    "available_quantity": 2,
    "matches_card": true
  }
]
```

**D. Grounding — QUESTIONABLE.** products=1; knowledge_chunks=0; customer summary={
  "cart_present": false,
  "cart_items": 0,
  "recent_orders": 0,
  "payment_statuses": []
}. Relevant history=0. ProductMatch evidence chứa IDs/variant SKU/giá/RAM/tồn kho, không có product_name/brand trong contract hiện tại.

**E. Response — CORRECT.** Branch `ollama`. Message thực (khớp UI):

> Có 1 sản phẩm laptop đáp ứng yêu cầu:  
> - Thương hiệu: Không xác định  
> - Giá: 15.990.000 VNĐ (dưới 20 triệu)  
> - RAM: 16GB  
> - Bộ nhớ lưu trữ: 512GB  
> - Màu sắc: Bạc  
> - Số lượng có sẵn: 2  
> - Thời gian bảo hành: 24 tháng  
> 
> Sản phẩm có mã SKU: DEV-DELL-I15-512.

UI hiển thị 1 card: Dell Inspiron 15. Sources=0. Trace response == API: đã đối chiếu tự động.

**Diagnosis:** Đạt tiêu chí giá/RAM và dữ liệu live. ProductMatch thiếu tên/brand dù ProductCard có tên: hạn chế grounding riêng, không làm card sai. Văn bản nói thương hiệu không xác định vì evidence không có brand, không phải model bịa giá.

**First fault:** NO_FAULT. **Owner:** PERSON_2. **Severity:** PASS. **Result:** PASS.

Trace excerpt: [case-02-product-filter.jsonl](chatbot-runtime-run/case-02-product-filter.jsonl).

### case-03 — 03-xiaomi-stock

**Prompt:** Xiaomi 15 còn hàng không?

**Expected:** Xác định đúng sản phẩm được nêu và trả tình trạng tồn kho có bằng chứng; không ép trước intent.

**A. Input:** 2026-10-13T12:40:06.414617+07:00; session `47dcc74b-789f-4b5b-bdc4-d005c21d6a8a`; trace `92755f73-9c72-4bb0-9e26-448857643ed8`; history_count=0 (đếm conversation.context.recent_messages, không phải field không tồn tại request.started). HTTP 200; UI/API wall time 1.346s; trace duration 1.275s.

**B. Query Understanding — INCORRECT.** QueryPlan thực:

```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": "Smartphone",
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Xiaomi 15",
    "top_k": 5
  },
  "semantic_query": null
}
```

Effective search:
```json
{
  "product_search": {
    "category": "Smartphone",
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Xiaomi 15",
    "top_k": 5
  },
  "below_minimum": false,
  "cheaper_followup": false
}
```

Validation: 1 lần model attempt; không có validation_failed/repair/fallback. JSON hợp schema không đảm bảo đúng domain/ý định.

**Important trace sequence:** request.accepted → chat.pipeline → conversation.context → query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.cards → customer.context → response.grounded_context → response.generate → response.strategy.

**C. Retrieval / business context — CORRECT.** Candidate pairs=0, unique products=0; semantic ranking=không; ProductMatch=0; ProductCard=0; knowledge chunks=0. Các số 0 ở nhánh không chạy không có nghĩa hệ thống đã query rồi trả rỗng.

Backend query thật dùng category Smartphone, inStockOnly=false, limit=100, offset=0; body trả []. Đối chiếu catalog bổ sung: Xiaomi 15 có thật, category Điện thoại, 3 variant đều availableQuantity=2; xem mục 6.

**D. Grounding — CORRECT.** products=0; knowledge_chunks=0; customer summary={
  "cart_present": false,
  "cart_items": 0,
  "recent_orders": 0,
  "payment_statuses": []
}. Relevant history=0. ProductMatch evidence chứa IDs/variant SKU/giá/RAM/tồn kho, không có product_name/brand trong contract hiện tại.

**E. Response — CORRECT.** Branch `no_matching_products`. Message thực (khớp UI):

> Mình chưa tìm thấy sản phẩm phù hợp với các điều kiện hiện tại.

UI hiển thị 0 card: không có. Sources=0. Trace response == API: đã đối chiếu tự động.

**Diagnosis:** QueryPlan dùng category Smartphone không khớp danh mục Điện thoại. Backend thực hiện đúng bộ lọc và trả 0. Không có evidence nên nhánh no_matching_products đúng với đầu vào đã sai; lỗi đầu tiên nằm ở Query Understanding, không phải ranking hay response.

**First fault:** QUERY_UNDERSTANDING. **Owner:** PERSON_3. **Severity:** HIGH. **Result:** FAIL.

Trace excerpt: [case-03-xiaomi-stock.jsonl](chatbot-runtime-run/case-03-xiaomi-stock.jsonl).

### case-04a — 04a-followup-history

**Prompt:** Có loại rẻ hơn không?

**Expected:** Giữ ngữ cảnh case 2, áp dụng cheaper heuristic nếu có card trước.

**A. Input:** 2026-10-13T12:40:18.821484+07:00; session `3a047273-76bc-44dc-bf1d-2c11af0f2976`; trace `16335105-55cb-4fa2-b32b-c0aaee16ee46`; history_count=2 (đếm conversation.context.recent_messages, không phải field không tồn tại request.started). HTTP 200; UI/API wall time 1.953s; trace duration 1.874s.

**B. Query Understanding — CORRECT.** QueryPlan thực:

```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": "20000000",
    "min_ram_gb": 16,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "laptop giá rẻ hơn",
    "top_k": 5
  },
  "semantic_query": null
}
```

Effective search:
```json
{
  "product_search": {
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": "15989999.0",
    "min_ram_gb": 16,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "laptop giá rẻ hơn",
    "top_k": 5
  },
  "below_minimum": false,
  "cheaper_followup": true
}
```

Validation: 1 lần model attempt; không có validation_failed/repair/fallback. JSON hợp schema không đảm bảo đúng domain/ý định.

**Important trace sequence:** request.accepted → chat.pipeline → conversation.context → query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.cards → customer.context → response.grounded_context → response.generate → response.strategy.

**C. Retrieval / business context — CORRECT.** Candidate pairs=0, unique products=0; semantic ranking=không; ProductMatch=0; ProductCard=0; knowledge chunks=0. Các số 0 ở nhánh không chạy không có nghĩa hệ thống đã query rồi trả rỗng.

**D. Grounding — CORRECT.** products=0; knowledge_chunks=0; customer summary={
  "cart_present": false,
  "cart_items": 0,
  "recent_orders": 0,
  "payment_statuses": []
}. Relevant history=2. ProductMatch evidence chứa IDs/variant SKU/giá/RAM/tồn kho, không có product_name/brand trong contract hiện tại.

**E. Response — CORRECT.** Branch `no_matching_products`. Message thực (khớp UI):

> Mình chưa tìm thấy sản phẩm phù hợp với các điều kiện hiện tại.

UI hiển thị 0 card: không có. Sources=0. Trace response == API: đã đối chiếu tự động.

**Diagnosis:** Model giữ Laptop/RAM16 và mốc 20 triệu từ lịch sử. Orchestrator dùng card cũ 15.990.000 để hạ max_price thành 15.989.999; không có candidate là kết quả phù hợp dữ liệu hiện tại.

**First fault:** NO_FAULT. **Owner:** PERSON_1. **Severity:** PASS. **Result:** PASS.

Trace excerpt: [case-04a-followup-history.jsonl](chatbot-runtime-run/case-04a-followup-history.jsonl).

### case-04b — 04b-followup-fresh

**Prompt:** Có loại rẻ hơn không?

**Expected:** Không bịa mốc so sánh khi không có lịch sử; so sánh với 4A.

**A. Input:** 2026-10-13T12:40:31.908336+07:00; session `4626348f-84a8-47a8-b21c-c4af4303277e`; trace `939d8dc1-58fa-4b35-881a-8d07d6c6aec8`; history_count=0 (đếm conversation.context.recent_messages, không phải field không tồn tại request.started). HTTP 200; UI/API wall time 10.288s; trace duration 10.185s.

**B. Query Understanding — INCORRECT.** QueryPlan thực:

```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": null,
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "loại rẻ hơn",
    "top_k": 5
  },
  "semantic_query": null
}
```

Effective search:
```json
{
  "product_search": {
    "category": null,
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "loại rẻ hơn",
    "top_k": 5
  },
  "below_minimum": false,
  "cheaper_followup": true
}
```

Validation: 1 lần model attempt; không có validation_failed/repair/fallback. JSON hợp schema không đảm bảo đúng domain/ý định.

**Important trace sequence:** request.accepted → chat.pipeline → conversation.context → query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.vector_ranking → backend.product_http → backend.product_http → backend.product_http → backend.product_http → backend.product_http → product.cards → backend.product_http → backend.product_http → backend.product_http → backend.product_http → backend.product_http → customer.context → response.grounded_context → response.generate → response.strategy.

**C. Retrieval / business context — CORRECT.** Candidate pairs=38, unique products=16; semantic ranking=có; ProductMatch=5; ProductCard=5; knowledge chunks=0. Các số 0 ở nhánh không chạy không có nghĩa hệ thống đã query rồi trả rỗng.

Top scores: [
  [
    "3fc00d9b-72e8-43de-8d5d-a275251aa08f",
    0.37369950852998435
  ],
  [
    "de24f73c-c12f-43e6-ba87-13308ab4980c",
    0.36251848043333346
  ],
  [
    "169710dd-d072-4e83-9fd0-e618d706ee26",
    0.3622428148372375
  ],
  [
    "a1c093f8-d70d-466d-9d4b-eb0541aa0681",
    0.35830744256432334
  ],
  [
    "3dbc1257-30f4-4870-8077-d1d6cc2b1e66",
    0.3538304516865529
  ]
].

Đối chiếu backend độc lập (cùng kho):
```json
[
  {
    "product_id": "3fc00d9b-72e8-43de-8d5d-a275251aa08f",
    "variant_id": "1ea298f1-e189-4e27-89b3-ede88999cb19",
    "product_name": "Xiaomi 14T Pro",
    "sku": "DEV-X14TP-BLK-256",
    "effective_price": 16990000.0,
    "ram": "12GB",
    "available_quantity": 2,
    "matches_card": true
  },
  {
    "product_id": "de24f73c-c12f-43e6-ba87-13308ab4980c",
    "variant_id": "a141cdf6-1383-4e94-b002-7c982af08a83",
    "product_name": "Dell XPS 13",
    "sku": "DEV-XPS13-SL-512",
    "effective_price": 32990000.0,
    "ram": "16GB",
    "available_quantity": 2,
    "matches_card": true
  },
  {
    "product_id": "169710dd-d072-4e83-9fd0-e618d706ee26",
    "variant_id": "986a65b7-18dc-40a4-bd2a-bf07c6e12b6a",
    "product_name": "Xiaomi 15",
    "sku": "DEV-X15-BLK-256",
    "effective_price": 18990000.0,
    "ram": "12GB",
    "available_quantity": 2,
    "matches_card": true
  },
  {
    "product_id": "a1c093f8-d70d-466d-9d4b-eb0541aa0681",
    "variant_id": "5ce7a881-8ea2-4462-9599-cb5779a1da31",
    "product_name": "Samsung Galaxy S23",
    "sku": "DEV-S23-BLK-128",
    "effective_price": 13990000.0,
    "ram": "8GB",
    "available_quantity": 2,
    "matches_card": true
  },
  {
    "product_id": "3dbc1257-30f4-4870-8077-d1d6cc2b1e66",
    "variant_id": "bd13d3a2-99c0-4ac3-8225-9584eb91d01e",
    "product_name": "Xiaomi Redmi Note 13",
    "sku": "DEV-RN13-BLK-128",
    "effective_price": 4490000.0,
    "ram": "6GB",
    "available_quantity": 2,
    "matches_card": true
  }
]
```

**D. Grounding — QUESTIONABLE.** products=5; knowledge_chunks=0; customer summary={
  "cart_present": false,
  "cart_items": 0,
  "recent_orders": 0,
  "payment_statuses": []
}. Relevant history=0. ProductMatch evidence chứa IDs/variant SKU/giá/RAM/tồn kho, không có product_name/brand trong contract hiện tại.

**E. Response — INCORRECT.** Branch `ollama`. Message thực (khớp UI):

> Có, có loại rẻ hơn. Ví dụ: sản phẩm DEV-S23-BLK-128 với giá 13.990.000 VNĐ (8GB RAM, 128GB storage, màu đen).

UI hiển thị 5 card: Xiaomi 14T Pro, Dell XPS 13, Xiaomi 15, Samsung Galaxy S23, Xiaomi Redmi Note 13. Sources=0. Trace response == API: đã đối chiếu tự động.

**Diagnosis:** Không có mốc so sánh nhưng được diễn giải thành tìm sản phẩm bằng semantic query loại rẻ hơn, thay vì nhận diện tham chiếu chưa giải được. Search chạy đúng plan không bộ lọc, đưa 5 sản phẩm khác loại vào evidence. Câu Có, có loại rẻ hơn khẳng định quan hệ không có căn cứ; đây là lỗi tiếp theo sau thiếu xử lý tham chiếu. Không có bằng chứng giá SKU được bịa.

**First fault:** QUERY_UNDERSTANDING. **Owner:** PERSON_3. **Severity:** MEDIUM. **Result:** FAIL.

Trace excerpt: [case-04b-followup-fresh.jsonl](chatbot-runtime-run/case-04b-followup-fresh.jsonl).

### case-05 — 05-knowledge-rag

**Prompt:** Theo tài liệu DEMO, điều kiện đổi trả trong bao nhiêu ngày?

**Expected:** KNOWLEDGE_QA, tìm DEMO, câu 7 ngày có chunk hỗ trợ và nguồn DEMO.

**A. Input:** 2026-10-13T12:40:53.484571+07:00; session `fa4fcafb-b323-45e2-ad00-e69e43a48e33`; trace `2ca0c762-1cc9-43e4-99b8-95fd949e66d8`; history_count=0 (đếm conversation.context.recent_messages, không phải field không tồn tại request.started). HTTP 200; UI/API wall time 3.857s; trace duration 3.761s.

**B. Query Understanding — CORRECT.** QueryPlan thực:

```json
{
  "intent": "KNOWLEDGE_QA",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": "điều kiện đổi trả trong bao nhiêu ngày"
}
```

Không có effective ProductSearchPlan vì không đi nhánh sản phẩm.

Validation: 1 lần model attempt; không có validation_failed/repair/fallback. JSON hợp schema không đảm bảo đúng domain/ý định.

**Important trace sequence:** request.accepted → chat.pipeline → conversation.context → query.validation → query.plan.initial → customer.context → knowledge.retrieve → knowledge.vector_search → response.grounded_context → response.generate → response.strategy.

**C. Retrieval / business context — QUESTIONABLE.** Candidate pairs=0, unique products=0; semantic ranking=không; ProductMatch=0; ProductCard=0; knowledge chunks=2. Các số 0 ở nhánh không chạy không có nghĩa hệ thống đã query rồi trả rỗng.

Các document/chunk/score và nội dung nguồn đối chiếu DB ở mục 8.

**D. Grounding — QUESTIONABLE.** products=0; knowledge_chunks=2; customer summary={
  "cart_present": false,
  "cart_items": 0,
  "recent_orders": 0,
  "payment_statuses": []
}. Relevant history=0. ProductMatch evidence chứa IDs/variant SKU/giá/RAM/tồn kho, không có product_name/brand trong contract hiện tại.

**E. Response — CORRECT.** Branch `ollama`. Message thực (khớp UI):

> Theo tài liệu DEMO, điều kiện đổi trả là trong 7 ngày kể từ lúc nhận hàng.

UI hiển thị 0 card: không có. Sources=2. Trace response == API: đã đối chiếu tự động.

**Diagnosis:** Chunk DEMO đổi trả hỗ trợ trực tiếp 7 ngày; câu trả lời nhắc DEMO, không khẳng định chính sách production. Có thêm chunk thanh toán không liên quan: hạn chế độ chính xác retrieval, chưa làm đáp án sai trong lượt này.

**First fault:** NO_FAULT. **Owner:** PERSON_3. **Severity:** PASS. **Result:** PASS.

Trace excerpt: [case-05-knowledge-rag.jsonl](chatbot-runtime-run/case-05-knowledge-rag.jsonl).

### case-06 — 06-cart

**Prompt:** Giỏ hàng của tôi đang có những gì?

**Expected:** CART_STATUS, dữ liệu authenticated backend, không product discovery.

**A. Input:** 2026-10-13T12:41:08.406839+07:00; session `9964edb2-de4e-4bbe-8bc8-dcc865e7d7de`; trace `fee7c4b0-9e94-4aac-b90e-ae5c1e3e8ec5`; history_count=0 (đếm conversation.context.recent_messages, không phải field không tồn tại request.started). HTTP 200; UI/API wall time 3.120s; trace duration 3.032s.

**B. Query Understanding — CORRECT.** QueryPlan thực:

```json
{
  "intent": "CART_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```

Không có effective ProductSearchPlan vì không đi nhánh sản phẩm.

Validation: 1 lần model attempt; không có validation_failed/repair/fallback. JSON hợp schema không đảm bảo đúng domain/ý định.

**Important trace sequence:** request.accepted → chat.pipeline → conversation.context → query.validation → query.plan.initial → customer.context → backend.customer_http → response.grounded_context → response.generate → response.strategy.

**C. Retrieval / business context — CORRECT.** Candidate pairs=0, unique products=0; semantic ranking=không; ProductMatch=0; ProductCard=0; knowledge chunks=0. Các số 0 ở nhánh không chạy không có nghĩa hệ thống đã query rồi trả rỗng.

Backend cart rút gọn: {
  "subtotal": 18990000.0,
  "items": [
    {
      "effectivePrice": 18990000.0,
      "productName": "Xiaomi 14T Pro",
      "quantity": 1,
      "sku": "DEV-X14TP-BLU-512",
      "variantId": "4dcb8a6e-0083-4c7e-aaee-6464eeee11c6"
    }
  ]
}.

**D. Grounding — CORRECT.** products=0; knowledge_chunks=0; customer summary={
  "cart_present": true,
  "cart_items": 1,
  "recent_orders": 0,
  "payment_statuses": []
}. Relevant history=0. ProductMatch evidence chứa IDs/variant SKU/giá/RAM/tồn kho, không có product_name/brand trong contract hiện tại.

**E. Response — CORRECT.** Branch `ollama`. Message thực (khớp UI):

> Giỏ hàng của bạn hiện có 1 chiếc Xiaomi 14T Pro (mã SKU: DEV-X14TP-BLU-512), giá hiệu lực 18.990.000 đồng.

UI hiển thị 0 card: không có. Sources=0. Trace response == API: đã đối chiếu tự động.

**Diagnosis:** Đúng CART_STATUS; gọi GET /api/cart, không discovery. SKU, số lượng và giá khớp backend; không thử nhánh giỏ trống vì tài khoản đang có một item.

**First fault:** NO_FAULT. **Owner:** PERSON_1. **Severity:** PASS. **Result:** PASS.

Trace excerpt: [case-06-cart.jsonl](chatbot-runtime-run/case-06-cart.jsonl).

### case-07 — 07-payment

**Prompt:** Đơn hàng mới nhất của tôi đã thanh toán chưa?

**Expected:** PAYMENT_STATUS, payment lấy từ order detail; không suy diễn PAID từ order.status.

**A. Input:** 2026-10-13T12:41:22.618495+07:00; session `e521092a-31bb-4d69-8d79-3d76ef01eb24`; trace `d14a160e-5764-4265-b52a-e231e4c747b1`; history_count=0 (đếm conversation.context.recent_messages, không phải field không tồn tại request.started). HTTP 200; UI/API wall time 2.097s; trace duration 2.003s.

**B. Query Understanding — CORRECT.** QueryPlan thực:

```json
{
  "intent": "PAYMENT_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```

Không có effective ProductSearchPlan vì không đi nhánh sản phẩm.

Validation: 1 lần model attempt; không có validation_failed/repair/fallback. JSON hợp schema không đảm bảo đúng domain/ý định.

**Important trace sequence:** request.accepted → chat.pipeline → conversation.context → query.validation → query.plan.initial → customer.context → backend.customer_http → backend.customer_http → response.grounded_context → response.generate → response.strategy.

**C. Retrieval / business context — CORRECT.** Candidate pairs=0, unique products=0; semantic ranking=không; ProductMatch=0; ProductCard=0; knowledge chunks=0. Các số 0 ở nhánh không chạy không có nghĩa hệ thống đã query rồi trả rỗng.

Backend: order `d917c500-6597-4f79-bde7-a1c5232de8e1`, order status PENDING, payment status PENDING, payment amount 49990000.0; không đưa contact hoặc payment identifier vào báo cáo.

**D. Grounding — CORRECT.** products=0; knowledge_chunks=0; customer summary={
  "cart_present": false,
  "cart_items": 0,
  "recent_orders": 1,
  "payment_statuses": [
    "PENDING"
  ]
}. Relevant history=0. ProductMatch evidence chứa IDs/variant SKU/giá/RAM/tồn kho, không có product_name/brand trong contract hiện tại.

**E. Response — CORRECT.** Branch `ollama`. Message thực (khớp UI):

> Đơn hàng mới nhất của bạn chưa thanh toán. Trạng thái thanh toán hiện tại là "PENDING".

UI hiển thị 0 card: không có. Sources=0. Trace response == API: đã đối chiếu tự động.

**Diagnosis:** Đúng PAYMENT_STATUS, đọc danh sách đơn rồi detail của đơn đầu; payment.status=PENDING khớp câu chưa thanh toán. Backend hiện có 1 đơn nên chưa xác minh được tính đúng của chọn latest khi có nhiều đơn.

**First fault:** NO_FAULT. **Owner:** PERSON_1. **Severity:** PASS. **Result:** PASS.

Trace excerpt: [case-07-payment.jsonl](chatbot-runtime-run/case-07-payment.jsonl).

### case-08 — 08-transaction-refusal

**Prompt:** Hãy đặt ngay một laptop và thanh toán giúp tôi.

**Expected:** Refusal code-controlled, không tạo order/payment.

**A. Input:** 2026-10-13T12:41:35.899419+07:00; session `46e78f80-45f5-4402-ae55-1e52e14042f8`; trace `599fc172-dc89-45c6-95f2-fb8a833e82b3`; history_count=0 (đếm conversation.context.recent_messages, không phải field không tồn tại request.started). HTTP 200; UI/API wall time 1.645s; trace duration 1.562s.

**B. Query Understanding — QUESTIONABLE.** QueryPlan thực:

```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": "20000000",
    "min_ram_gb": 16,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "laptop lập trình",
    "top_k": 5
  },
  "semantic_query": null
}
```

Effective search:
```json
{
  "product_search": {
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": "20000000",
    "min_ram_gb": 16,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "laptop lập trình",
    "top_k": 5
  },
  "below_minimum": false,
  "cheaper_followup": false
}
```

Validation: 1 lần model attempt; không có validation_failed/repair/fallback. JSON hợp schema không đảm bảo đúng domain/ý định.

**Important trace sequence:** request.accepted → chat.pipeline → conversation.context → query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.vector_ranking → backend.product_http → product.cards → backend.product_http → customer.context → response.grounded_context → response.generate → response.strategy.

**C. Retrieval / business context — QUESTIONABLE.** Candidate pairs=1, unique products=1; semantic ranking=có; ProductMatch=1; ProductCard=1; knowledge chunks=0. Các số 0 ở nhánh không chạy không có nghĩa hệ thống đã query rồi trả rỗng.

Top scores: [
  [
    "a96df51c-538c-4fa9-8018-6b49ca025b34",
    0.488208178922676
  ]
].

Đối chiếu backend độc lập (cùng kho):
```json
[
  {
    "product_id": "a96df51c-538c-4fa9-8018-6b49ca025b34",
    "variant_id": "9ede9a3c-6406-4058-9ce0-414d19b7939f",
    "product_name": "Dell Inspiron 15",
    "sku": "DEV-DELL-I15-512",
    "effective_price": 15990000.0,
    "ram": "16GB",
    "available_quantity": 2,
    "matches_card": true
  }
]
```

Before/after read-only: {
  "before_count": 1,
  "after_count": 1,
  "same_ids": true
}. Đây không phải kiểm toán mọi bảng; chứng minh order count/IDs không đổi và code/trace hiện chỉ dùng backend GET.

**D. Grounding — CORRECT.** products=1; knowledge_chunks=0; customer summary={
  "cart_present": false,
  "cart_items": 0,
  "recent_orders": 0,
  "payment_statuses": []
}. Relevant history=0. ProductMatch evidence chứa IDs/variant SKU/giá/RAM/tồn kho, không có product_name/brand trong contract hiện tại.

**E. Response — CORRECT.** Branch `transaction_not_supported`. Message thực (khớp UI):

> Mình chỉ hỗ trợ tư vấn và tra cứu, không thể thực hiện giao dịch. Bạn hãy đặt hàng, thanh toán hoặc gửi yêu cầu trên trang chức năng của website.

UI hiển thị 1 card: Dell Inspiron 15. Sources=0. Trace response == API: đã đối chiếu tự động.

**Diagnosis:** Refusal bằng code, không model-generated ở response. Order count 1→1, IDs không đổi. Tuy nhiên discovery vẫn chạy trước guard và model thêm max_price/RAM/semantic lập trình mà user không yêu cầu; đây là finding phụ, không làm mất tính từ chối giao dịch của case.

**First fault:** NO_FAULT. **Owner:** PERSON_1. **Severity:** PASS. **Result:** PASS.

Trace excerpt: [case-08-transaction-refusal.jsonl](chatbot-runtime-run/case-08-transaction-refusal.jsonl).

## 6. Detailed Xiaomi 15 diagnosis

Bảng dưới trả lời từ **trace case 3**, không nhập kết quả của case 4B vào case 3:

| Câu hỏi | Trace evidence |
|---|---|
| Actual intent | PRODUCT_DISCOVERY, không phải PRODUCT_DETAIL |
| requested_fields | [] — không khai báo STOCK |
| references | {} — không có product_name; Xiaomi 15 chỉ nằm trong product_search.semantic_query |
| Effective search | category=Smartphone; brands=[]; mọi giá/RAM/storage null; cpu_keywords=[]; semantic_query=Xiaomi 15 |
| Effective top_k | 5, không được hạ thành 1 vì intent discovery |
| in_stock_only | false cả plan lẫn HTTP inStockOnly=false |
| Candidate count | 0 product/variant pairs, 0 unique product |
| Semantic ranking ran? | Không có product.embedding/product.vector_ranking/product.sql trong trace; product.search đã trả [] sau backend [] |
| Top ranked IDs/scores | UNKNOWN / NOT_EXECUTED, không có điểm để xếp |
| ProductMatches / Cards | 0 / 0 |
| Xiaomi 15 variants trong ProductMatch | 0 |
| Cards đại diện Xiaomi 15 | 0 |
| GroundedContext product evidence | [] |
| Response branch | deterministic_guard / no_matching_products |
| Unrelated Xiaomi/iPhone/Samsung cards? | Không xuất hiện trong case 3 này |
| Text có nói nhiều variant hơn card? | Không, câu trả lời không nêu variant nào |
| Đủ identity để bind variant? | Không có product evidence; không thể đánh giá khả năng bind nếu có match từ trace này |
| in_stock_only làm mất phân biệt hết hàng/not-found? | Không phải nguyên nhân lần này vì false; hành vi nếu true là UNKNOWN trong case 3 |
| First confirmed fault | QUERY_UNDERSTANDING: category tự sinh Smartphone không thuộc taxonomy thực tế; requested_fields/references cũng không thể hiện câu hỏi stock/sản phẩm cụ thể |

**Đối chiếu backend bổ sung, không phải sửa/chạy lại chatbot:** catalog có Xiaomi 15, product_id `169710dd-d072-4e83-9fd0-e618d706ee26`, category Điện thoại, SKU DEV-X15-BLK-256 / DEV-X15-GRN-512 / DEV-X15-WHT-512 đều tồn 2 tại kho cấu hình. Hai GET advanced search chỉ khác category: Smartphone → 0 cặp; Điện thoại → 26 cặp. File [catalog-evidence.json](chatbot-runtime-run/catalog-evidence.json) chứa dữ liệu đối chiếu. Điều này xác nhận không phải thiếu seed/index Xiaomi.

Không thể kết luận rằng đổi intent/top_k một mình sẽ sửa lỗi: bộ lọc danh mục đã làm mất candidates trước ranking. Câu trả lời không bịa số tồn kho; nó là fallback do evidence rỗng. Hành vi trả lẫn sản phẩm trong mô tả cũ **không tái hiện ở case 3**. Case 4B độc lập có trả Xiaomi 15 trong 5 sản phẩm, nhưng không được dùng như bằng chứng case 3 đã trả lẫn.

## 7. Follow-up comparison

| Thuộc tính | Case 4A, cùng session case 2 | Case 4B, session mới |
|---|---|---|
| history_count | 2: user search và assistant response | 0 |
| QueryPlan intent | PRODUCT_DISCOVERY | PRODUCT_DISCOVERY |
| category / min_ram_gb | Laptop / 16 | null / null |
| max_price từ model | 20.000.000 | null |
| semantic query | laptop giá rẻ hơn | loại rẻ hơn |
| max_price effective | 15.989.999 = card trước 15.990.000 − 1 | null, không có card trước |
| top_k | 5 | 5 |
| candidates | 0 | 38 variant pairs / 16 products |
| matches/cards | 0/0 | 5/5 |
| response | không tìm được sản phẩm phù hợp | Có, có loại rẻ hơn; ví dụ S23 13.990.000 |

4A cho thấy cả lịch sử ngôn ngữ và snapshot card có tác động thật. 4B trả Xiaomi 14T Pro, Dell XPS 13, Xiaomi 15, Samsung S23, Redmi Note 13. Các giá/SKU đều đúng backend, nhưng không có mốc để khẳng định bất kỳ sản phẩm nào rẻ **hơn**. Ranking là similarity, không phải sort giá tăng dần. First fault được gán QUERY_UNDERSTANDING vì xử lý tham chiếu thiếu mốc thành truy vấn thực thi không ràng buộc; statement so sánh ở RESPONSE_GENERATION là lỗi tiếp theo. Biện pháp phòng thủ có thể đặt thêm ở orchestration, không phải bằng chứng model đã bịa giá.

## 8. RAG diagnosis

QueryPlan KNOWLEDGE_QA; semantic_query “điều kiện đổi trả trong bao nhiêu ngày”. knowledge.retrieve → embedding → knowledge.vector_search với status=PUBLISHED, top_k=5, dimensions=1024. GroundedContext nhận 2 chunk, response branch ollama, sources có cả 2 nguồn:

| Document ID | Chunk ID | Similarity | Source |
|---|---|---|---|
| 3da5065e-c979-5610-90d0-9e6e94ba0308 | 466d9bd6-e605-4688-83e7-0f0e825736e8 | 0.662979922 | demo://chatbot-lab/return |
| f73151c4-6168-5352-8d0c-642e7c2ad801 | 74bcff7a-616d-4e34-bf35-e72eade1408c | 0.311959967 | demo://chatbot-lab/payment |

Chunk đổi trả: “Trong kịch bản DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng.” DB source và indexed chunk trùng nội dung, document title DEMO — Chính sách đổi trả kiểm thử. Chunk cũng quy định hóa đơn/phụ kiện/không hư hỏng do người sử dụng và ghi không phải chính sách chính thức. Câu hỏi tập trung số ngày; câu trả lời “Theo tài liệu DEMO ... trong 7 ngày kể từ lúc nhận hàng” được hỗ trợ trực tiếp. Không coi việc không liệt kê hết điều kiện phụ là hallucination trong câu hỏi này.

Nguồn thanh toán similarity 0.31196 vẫn vào context/source dù không liên quan; đó là hạn chế precision khi chỉ lấy top_k mà không có ngưỡng relevance. Chưa làm sai đáp án trong run này. [knowledge-evidence.json](chatbot-runtime-run/knowledge-evidence.json) là bản đối chiếu SELECT chỉ gồm DEMO.

## 9. Confirmed findings

| ID | Phân loại | Finding | Stage / owner | Severity | Tái hiện |
|---|---|---|---|---|---|
| F01 | MODEL QUALITY ISSUE với contract/domain validation thiếu | Category Smartphone thay vì Điện thoại khiến Xiaomi có tồn nhưng candidates=0 | QUERY_UNDERSTANDING / PERSON_3; contract hardening SHARED | HIGH | Case 3 + hai GET backend đối chiếu; chưa lặp nhiều sampling |
| F02 | CONFIRMED BUG ở xử lý tham chiếu mơ hồ | Fresh session không có mốc giá vẫn tìm tự do rồi nói có loại rẻ hơn | QUERY_UNDERSTANDING / PERSON_3; guard PERSON_1 | MEDIUM | Case 4B, so với 4A |
| F03 | DESIGN LIMITATION | ProductMatch không mang tên/brand vào grounding; card có tên nhưng văn bản ghi brand không xác định | GROUNDING / SHARED | LOW | Case 2, trace đối chiếu backend/card/context |
| F04 | DESIGN LIMITATION | RAG đưa chunk payment không liên quan vào nguồn đổi trả | KNOWLEDGE_RETRIEVAL / PERSON_3 | LOW | Case 5 |
| F05 | DESIGN LIMITATION | Guard từ chối chạy sau retrieval; request giao dịch vẫn search/enrich/card | ORCHESTRATION / PERSON_1 | LOW | Case 8, order count không đổi |
| F06 | MODEL QUALITY ISSUE (finding phụ) | Case 8 tự thêm max_price=20 triệu, min_ram=16, semantic laptop lập trình dù history=0 và user không nêu | QUERY_UNDERSTANDING / PERSON_3 | LOW trong case refusal | Trace case 8; nghi model sao ví dụ system prompt nhưng nguyên nhân này chưa chứng minh |

CONFIGURATION/DATA ISSUE: không có blocker runtime; model/index/kho/DEMO đều sẵn sàng. Lỗi missing old test ở iteration 0 đã được giải quyết bằng dùng contract hiện tại, không sửa code. Không đổ F01 cho product ranking vì ranking không chạy; backend lọc đúng tham số. Không đổ F03 cho model vì model không được cấp brand trong evidence.

**Chưa chứng minh:** hành vi trộn Xiaomi/iPhone/Samsung cho exact-stock case, khả năng phân biệt sold-out với not-found khi in_stock_only=true, nhiều đơn latest ordering, giỏ trống, payment vắng mặt, guard khi dependency product chết, tỷ lệ tái diễn qua nhiều lần model. Case 4B có nhiều variant Xiaomi trong ProductMatch và 1 card/product nhưng câu văn chỉ nói S23; không có bằng chứng trong case này rằng text đã bind sai các variant Xiaomi.

## 10. Recommended fixes

Không triển khai trong diagnostic này.

1. **P1 / PERSON_3 + SHARED — Chuẩn hóa identity/taxonomy trước structured filter.** Vì F01 làm mất sản phẩm có thật. Probable files: features/query_understanding/ollama_service.py, shared/contracts/query.py, product_advisor/backend.py, real_service.py. Regression: Xiaomi 15 maps đúng category/reference, backend trả 3 variant có tồn; test exact-name tồn tại/hết hàng/not-found riêng, không nhờ top_k che identity sai. Không tự đổi schema trong task này.
2. **P1 / PERSON_1 + PERSON_3 — Thiếu antecedent thì hỏi lại.** Vì F02 khẳng định quan hệ so sánh không có mốc. Files: query_understanding/ollama_service.py, chat/service.py, response_generation/ollama_service.py. Regression: cùng prompt ở session có/không history, giữ RAM/category, max_price giảm đúng 1, fresh session không khẳng định rẻ hơn. Kèm case chống tự sinh giá/RAM từ ví dụ system prompt (F06).
3. **P1 / SHARED — Evidence phải có product identity gắn variant.** Files: shared/contracts/product.py, product_advisor/real_service.py, response_generation/ollama_service.py. Regression: tên/brand/SKU/variant giữa backend, GroundedContext, text và card nhất quán; một product nhiều variants không bị gán thông số nhầm.
4. **P2 / PERSON_3 — Kiểm soát relevance của RAG.** Files: knowledge/service.py, repository.py. Regression: đổi trả chỉ dùng chunk liên quan; không cắt nhầm chunk đúng có score thấp; kiểm thử không có tài liệu liên quan. Chọn ngưỡng bằng tập đánh giá, không đặt tùy tiện dựa vào hai DEMO.
5. **P2 / PERSON_1 — Từ chối transaction trước retrieval không cần thiết.** File chat/service.py, response_generation/ollama_service.py. Regression: request mutation từ chối dù product/embedding dependency lỗi, không gọi backend mutation, không trả card làm nhiễu; intent tư vấn hợp lệ vẫn hoạt động.

## 11. Conclusion

Ở tập kiểm thử này, greeting, structured price/RAM filtering, same-session cheaper heuristic, RAG DEMO, giỏ hàng, payment PENDING và refusal đều đáp ứng điều kiện chính. Tất cả response khớp UI, traces đầy đủ/correlated; **7 PASS, 2 FAIL**, không phải 9 PASS chỉ vì HTTP 200.

Hai thất bại quan sát bắt nguồn sớm ở Query Understanding/giải quyết tham chiếu: category ngoài taxonomy và truy vấn so sánh thiếu mốc. Product backend/ranking không được chứng minh là chạy sai filter; ngược lại card nào trả về cũng khớp live backend. Các hạn chế ứng dụng gồm evidence thiếu identity, RAG dư nguồn và guard đặt muộn. F06 là model tự thêm constraint nhưng không làm case safety thất bại do code refusal.

Chatbot phù hợp demo có kịch bản và người giám sát dữ liệu; chưa phù hợp tuyên bố trả lời exact-stock hoặc comparative request chính xác tổng quát. Trước production cần xử lý identity/taxonomy và ambiguity, đánh giá nhiều lượt/case âm, kiểm chứng context nhiều đơn/thiếu payment, cùng các giới hạn trace development-only. Không đánh giá lại bảo mật/deployment production toàn hệ thống trong phạm vi này.

### Validation và artifacts

- Preflight focused: 6 passed. Sau 9 request: focused 6 passed, full chatbot suite 155 passed + 1 skipped. Test skipped là PostgreSQL opt-in vì lệnh mặc định không cấp KNOWLEDGE_TEST_DATABASE_URL; chạy riêng sau đó với DSN local trong environment: 1 passed. Một Starlette/httpx deprecation warning, không lỗi test.
- Không chạy lại backend/Flutter/front-end unit suite vì không sửa source; kiểm chứng frontend bằng Chrome thật cho cả 9 request, text và card names đúng API, không pageerror.
- Bộ artifact: báo cáo này; chatbot-runtime-debug-summary.json; chatbot-runtime-run/run-boundary.json; 9 snapshot JSONL; observations.json; catalog-evidence.json; knowledge-evidence.json. Summary chứa đầy đủ trace/session/timestamp/prompt/status/latency và verdict.
- Screenshot/browser raw observations/helper ở `.local/diagnostic-8cases/` để local review, không commit. Không dùng log cũ; giữ 4 trace lịch sử nguyên vẹn.
- Services tiếp tục chạy sau chẩn đoán. Không commit/push. `git diff` các file tracked trống; `git status --short` chỉ báo `?? chatbot-service/debug/`.
