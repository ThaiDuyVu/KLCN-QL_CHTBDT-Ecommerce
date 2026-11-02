# CHECKPOINT 01 — BASIC & STRUCTURED PRODUCT TEST

Task `c2c_chatbot_eval_phase01`, iteration 0. Phạm vi **T01–T14**, 14 request thực tế ở 14 phiên mới. Kết quả: **3 PASS, 6 PASS_WITH_WARNINGS, 5 FAIL, 0 BLOCKED**. Không coi HTTP 200 là PASS.

## 1. Mục tiêu, môi trường và phương pháp

Đánh giá hiểu intent cơ bản, chuyển yêu cầu giá/RAM/brand/category/tồn kho thành QueryPlan, retrieval, evidence và kết quả text/cards. Đây là TEST + DIAGNOSE + DOCUMENTATION: không sửa prompt/model, logic, contract, schema, seed, frontend hoặc tracing; không chạy checkpoint 02.

Workspace thực `/Users/vu/Documents/Vu/HUIT-Year4/KLTN/KLCN-QL_CHTBDT-Ecommerce-main` là repo `ThaiDuyVu/KLCN-QL_CHTBDT-Ecommerce` (tên thư mục không trùng nguyên văn QL_CHTBDT_Ecommerce trong yêu cầu nhưng remote và cấu trúc project đúng repo đang làm). Branch `dev/thai-duy-vu`, commit `438227c`; worktree ban đầu sạch. Không fetch/merge/commit/push trong checkpoint.

Dịch vụ đang chạy: frontend localhost:5173, Spring Boot :8080, FastAPI :8090, PostgreSQL :5432 Docker healthy, Ollama :11434. AI=true, Product Advisor=real; chat `qwen3:4b-instruct`, embedding `qwen3-embedding:0.6b`, cả hai ready. Kho đã cấu hình, index product 16 records và Knowledge 2 chunk DEMO có vector. Xác minh bằng status, truy vấn DB chỉ đọc và lời gọi thực tế. Không khởi động lại hoặc chỉnh .env vì runtime đã sẵn sàng.

Tracing hiện tại: `app/shared/tracing/__init__.py`; CHATBOT_TRACE_ENABLED=true và CHATBOT_TRACE_FILE_ENABLED=true trong development. Sink gốc repo `.local/logs/chatbot-traces.jsonl`: 1 snapshot/trace chứa events. Không có CHATBOT_TRACE_PAYLOADS. Dùng current events request.accepted, conversation.context, query.model_attempt/validation/plan.initial/plan.effective, backend.product_http/product_result, product.search/cards/vector_ranking/sql, customer.context, knowledge.retrieve/vector_search, response.grounded_context/strategy/generate, chat.pipeline. Không dùng tên event cũ chat.request.*.

Test data: CUSTOMER synthetic đã được repo tài liệu hóa; không đưa tên tài khoản đăng nhập, password, cookie, token, DB URL vào báo cáo. Tất cả session mới, history_count=0. Product/category/brand lấy backend live tại cùng warehouse; có tài liệu DEMO đổi trả, không policy production. Không thêm hoặc sửa seed.

### Các bước thực tế

1. Kiểm tra branch/worktree/source và focused preflight (22 pass).
2. Chrome thật đăng nhập bằng flow cookie/CSRF hiện có, mở `/chatbot-lab`.
3. Trước run đánh dấu sink: 13 dòng, byte offset 588882, timestamp 2026-10-13T06:29:36.723027+00:00; không xóa log cũ. Kết thúc 27 dòng, 14 trace mới; không rotate.
4. Với mỗi T: tạo session UUID mới, nhập đúng prompt, nhấn Gửi; bắt POST qua Vite proxy và response trace ID, đo latency từ UI/API.
5. Đọc riêng phần log sau boundary, lọc trace_id + session_id, giữ thứ tự events. Đối chiếu chat.pipeline output với ChatResponse, text/card names trên UI với API.
6. GET detail từng card tại kho của chatbot; đối chiếu product/variant/SKU/giá/RAM/brand/category/tồn kho. Với T07–T14, gọi thêm backend filter **đúng theo ý định test** để biết dataset thực có gì; đây là GET chẩn đoán, không thay QueryPlan hoặc chạy lại chatbot.
7. Rút gọn/sanitize evidence, chấm từng case và ghi first fault. Test đơn vị không được dùng để thay thế practical output.

Trace proof: [evidence/phase01/](evidence/phase01/), [backend-baseline.json](evidence/phase01/backend-baseline.json), [environment.json](evidence/phase01/environment.json). Raw log và screenshot chỉ ở `.local/eval-phase01/`, không commit. Excerpts bỏ prompt dài/model schema/identity và raw customer DTO, vẫn giữ plan/query/status/evidence/response cần chứng minh.

## 2. Bảng 14 test cases

PASS_WITH_WARNINGS nghĩa là yêu cầu chính được đáp ứng trong dữ liệu hiện tại nhưng có thiếu sót hoặc rủi ro đã quan sát. FAIL dựa trên cả text **và cards trên UI**, không chỉ câu văn. Một lần/case; chưa kết luận tỷ lệ ổn định qua nhiều sampling.

| ID | Prompt | Intent / Branch actual | Candidate pairs / Match / Card | Kết quả | FIRST FAULT | Severity |
|---|---|---|---|---|---|---|
| T01 | Xin chào | GREETING / greeting | 0 / 0 / 0 | PASS | NO_FAULT | PASS |
| T02 | Chào buổi sáng | GREETING / greeting | 0 / 0 / 0 | PASS | NO_FAULT | PASS |
| T03 | Bạn có thể giúp tôi những gì? | GREETING / greeting | 0 / 0 / 0 | PASS_WITH_WARNINGS | QUERY_UNDERSTANDING | LOW |
| T04 | Tôi muốn mua một chiếc điện thoại. | PRODUCT_DISCOVERY / ollama | 38 / 5 / 5 | PASS_WITH_WARNINGS | QUERY_UNDERSTANDING | LOW |
| T05 | Cửa hàng có cho đổi trả sản phẩm không? | KNOWLEDGE_QA / ollama | 0 / 0 / 0 | PASS_WITH_WARNINGS | KNOWLEDGE_RETRIEVAL | LOW |
| T06 | Tôi muốn biết tình trạng đơn hàng. | ORDER_STATUS / ollama | 0 / 0 / 0 | PASS | NO_FAULT | PASS |
| T07 | Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB. | PRODUCT_DISCOVERY / ollama | 1 / 1 / 1 | PASS_WITH_WARNINGS | GROUNDING | LOW |
| T08 | Tìm laptop Dell có RAM 16GB, giá không quá 20 triệu. | PRODUCT_DISCOVERY / ollama | 9 / 4 / 4 | FAIL | QUERY_UNDERSTANDING | HIGH |
| T09 | Tìm iPhone giá dưới 1 triệu. | PRODUCT_DISCOVERY / no_matching_products | 0 / 0 / 0 | PASS_WITH_WARNINGS | QUERY_UNDERSTANDING | LOW |
| T10 | Tìm laptop RAM ít nhất 32GB, giá dưới 20 triệu. | PRODUCT_DISCOVERY / ollama | 3 / 2 / 2 | FAIL | QUERY_UNDERSTANDING | HIGH |
| T11 | Tìm điện thoại Xiaomi dưới 20 triệu. | PRODUCT_DISCOVERY / no_matching_products | 0 / 0 / 0 | FAIL | QUERY_UNDERSTANDING | HIGH |
| T12 | Tìm điện thoại RAM tối thiểu 12GB. | PRODUCT_DISCOVERY / ollama | 19 / 5 / 5 | FAIL | QUERY_UNDERSTANDING | HIGH |
| T13 | Tìm laptop RAM 16GB đang còn hàng. | PRODUCT_DISCOVERY / ollama | 9 / 4 / 4 | PASS_WITH_WARNINGS | QUERY_UNDERSTANDING | MEDIUM |
| T14 | Tìm laptop giá từ 25 triệu đến 20 triệu. | PRODUCT_DISCOVERY / ollama | 11 / 5 / 5 | FAIL | QUERY_UNDERSTANDING | HIGH |

## 3. Expected vs Actual — phân tích từng case

### T01 — PASS

**Prompt:** Xin chào

**Expected:** GREETING; không gọi product/knowledge retrieval; câu chào phù hợp.

**Thực thi:** 2026-10-13T13:29:36.970500+07:00; session `0495495b-ef19-4060-ab9c-b21df3e1e3b8`; trace `197c6c3d-cfa3-4c8a-a5ff-05b63d526217`. HTTP 200; UI/API latency 5.713s; pipeline 5.613s. Fresh history=0.

**QueryPlan:**
```json
{
  "intent": "GREETING",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Không có effective ProductSearchPlan; không đi product path.

**Trace sequence:** query.validation → query.plan.initial → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[]
```

**Retrieval:** 0 product/variant pairs, 0 unique products; 0 ProductMatch; 0 card. Semantic ranking không chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

**GroundedContext:** {"products": 0, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `greeting`; 0 nguồn. Text thực, khớp UI:

> Xin chào! Mình có thể hỗ trợ tìm sản phẩm và giải đáp thông tin cửa hàng.

**Diagnosis / first fault:** GREETING, câu chào tiếng Việt, không discovery/RAG hoặc card/source.

**Owner:** PERSON_3. **Severity:** PASS. **Verdict:** PASS.

**Trace evidence:** [T01.jsonl](evidence/phase01/T01.jsonl).

### T02 — PASS

**Prompt:** Chào buổi sáng

**Expected:** Hiểu lời chào; không tự đưa dữ liệu giá/tồn/đơn.

**Thực thi:** 2026-10-13T13:29:53.720216+07:00; session `ed090377-215a-4093-a60f-b76aa92d0bc1`; trace `5ae518d4-1ce0-40eb-bb99-0a2c168687b2`. HTTP 200; UI/API latency 0.405s; pipeline 0.331s. Fresh history=0.

**QueryPlan:**
```json
{
  "intent": "GREETING",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Không có effective ProductSearchPlan; không đi product path.

**Trace sequence:** query.validation → query.plan.initial → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[]
```

**Retrieval:** 0 product/variant pairs, 0 unique products; 0 ProductMatch; 0 card. Semantic ranking không chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

**GroundedContext:** {"products": 0, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `greeting`; 0 nguồn. Text thực, khớp UI:

> Xin chào! Mình có thể hỗ trợ tìm sản phẩm và giải đáp thông tin cửa hàng.

**Diagnosis / first fault:** Biến thể lời chào được hiểu; không đưa giá/tồn/đơn.

**Owner:** PERSON_3. **Severity:** PASS. **Verdict:** PASS.

**Trace evidence:** [T02.jsonl](evidence/phase01/T02.jsonl).

### T03 — PASS_WITH_WARNINGS

**Prompt:** Bạn có thể giúp tôi những gì?

**Expected:** HELP hoặc hành vi trợ giúp phù hợp, mô tả đúng khả năng.

**Thực thi:** 2026-10-13T13:30:05.150136+07:00; session `de5e184f-701f-486f-83b6-7979a3a97712`; trace `56f8d5b8-4139-4272-b421-ded8ca8b6257`. HTTP 200; UI/API latency 0.420s; pipeline 0.343s. Fresh history=0.

**QueryPlan:**
```json
{
  "intent": "GREETING",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Không có effective ProductSearchPlan; không đi product path.

**Trace sequence:** query.validation → query.plan.initial → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[]
```

**Retrieval:** 0 product/variant pairs, 0 unique products; 0 ProductMatch; 0 card. Semantic ranking không chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

**GroundedContext:** {"products": 0, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `greeting`; 0 nguồn. Text thực, khớp UI:

> Xin chào! Mình có thể hỗ trợ tìm sản phẩm và giải đáp thông tin cửa hàng.

**Diagnosis / first fault:** Intent GREETING thay vì HELP. Câu chào vẫn nêu hai khả năng đúng (tìm sản phẩm/thông tin cửa hàng), nên không đánh FAIL theo điều kiện HELP hoặc hành vi trợ giúp phù hợp; cảnh báo mapping intent và nội dung trợ giúp sơ lược.

**Owner:** PERSON_3. **Severity:** LOW. **Verdict:** PASS_WITH_WARNINGS.

**Trace evidence:** [T03.jsonl](evidence/phase01/T03.jsonl).

### T04 — PASS_WITH_WARNINGS

**Prompt:** Tôi muốn mua một chiếc điện thoại.

**Expected:** Tư vấn điện thoại; hỏi thêm hoặc đề xuất; không tự gán RAM/giá.

**Thực thi:** 2026-10-13T13:30:16.469558+07:00; session `7f8213d8-ec29-47a7-bddd-2920d6ad2762`; trace `f8097dc4-b305-43bf-a62c-440a2407ecc7`. HTTP 200; UI/API latency 13.816s; pipeline 13.715s. Fresh history=0.

**QueryPlan:**
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
    "semantic_query": "điện thoại",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Effective plan giống plan gốc: {"product_search": {"category": null, "brands": [], "min_price": null, "max_price": null, "min_ram_gb": null, "min_storage_gb": null, "cpu_keywords": [], "in_stock_only": false, "semantic_query": "điện thoại", "top_k": 5}, "below_minimum": false, "cheaper_followup": false}

**Trace sequence:** query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.vector_ranking → backend.product_http → backend.product_http → backend.product_http → backend.product_http → backend.product_http → product.cards → backend.product_http → backend.product_http → backend.product_http → backend.product_http → backend.product_http → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/search/advanced",
    "params": [
      [
        "limit",
        "100"
      ],
      [
        "offset",
        "0"
      ],
      [
        "warehouseId",
        "d13ea247-ab70-473b-80d6-c8adea60334c"
      ],
      [
        "inStockOnly",
        "false"
      ]
    ]
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/a1c093f8-d70d-466d-9d4b-eb0541aa0681/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/3fc00d9b-72e8-43de-8d5d-a275251aa08f/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/169710dd-d072-4e83-9fd0-e618d706ee26/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/d08f9c12-2196-4fc3-8aab-78d763628ce6/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/6037875f-9598-49ad-92d4-5d48cc48d501/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/a1c093f8-d70d-466d-9d4b-eb0541aa0681/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/3fc00d9b-72e8-43de-8d5d-a275251aa08f/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/169710dd-d072-4e83-9fd0-e618d706ee26/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/d08f9c12-2196-4fc3-8aab-78d763628ce6/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/6037875f-9598-49ad-92d4-5d48cc48d501/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  }
]
```

**Retrieval:** 38 product/variant pairs, 16 unique products; 5 ProductMatch; 5 card. Semantic ranking có chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

Đối chiếu từng card với backend live:

| Product / SKU | Category / Brand | Giá | RAM | Tồn kho | Card khớp live? |
|---|---|---|---|---|---|
| Samsung Galaxy S23 / DEV-S23-BLK-128 | Điện thoại / Samsung | 13,990,000 | 8GB | 2 | True |
| Xiaomi 14T Pro / DEV-X14TP-BLK-256 | Điện thoại / Xiaomi | 16,990,000 | 12GB | 2 | True |
| Xiaomi 15 / DEV-X15-BLK-256 | Điện thoại / Xiaomi | 18,990,000 | 12GB | 2 | True |
| Samsung Galaxy A55 / DEV-A55-NVY-128 | Điện thoại / Samsung | 8,490,000 | 8GB | 2 | True |
| Samsung Galaxy Z Flip6 / DEV-ZF6-GRY-256 | Điện thoại / Samsung | 24,990,000 | 12GB | 2 | True |

**Chú ý:** khớp dữ liệu thật không đồng nghĩa khớp điều kiện người dùng.

**GroundedContext:** {"products": 5, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `ollama`; 0 nguồn. Text thực, khớp UI:

> Có 5 sản phẩm điện thoại được đề xuất, với các phiên bản khác nhau về RAM, bộ nhớ, màu sắc và giá cả. Tất cả các phiên bản đều có sẵn (available_quantity = 2) và bảo hành 12 tháng. Giá từ 8.490.000đ đến 28.990.000đ.

**Diagnosis / first fault:** Nhận diện discovery, không tự thêm RAM/giá; 5 card đều điện thoại và số liệu câu văn được variants hỗ trợ. Tuy nhiên category=null: chỉ semantic query giữ điện thoại, structured candidates gồm toàn catalog, có nguy cơ lọt sản phẩm khác loại.

**Owner:** PERSON_3. **Severity:** LOW. **Verdict:** PASS_WITH_WARNINGS.

**Trace evidence:** [T04.jsonl](evidence/phase01/T04.jsonl).

### T05 — PASS_WITH_WARNINGS

**Prompt:** Cửa hàng có cho đổi trả sản phẩm không?

**Expected:** Policy/knowledge; DEMO không trở thành policy chính thức.

**Thực thi:** 2026-10-13T13:30:41.584004+07:00; session `e997d714-a26a-4f29-a55d-32cfd870ca4a`; trace `69e1c078-dcbe-44f3-8ca6-db5323422e21`. HTTP 200; UI/API latency 4.957s; pipeline 4.870s. Fresh history=0.

**QueryPlan:**
```json
{
  "intent": "KNOWLEDGE_QA",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": "chính sách đổi trả"
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Không có effective ProductSearchPlan; không đi product path.

**Trace sequence:** query.validation → query.plan.initial → customer.context → knowledge.vector_search → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[]
```

**Retrieval:** 0 product/variant pairs, 0 unique products; 0 ProductMatch; 0 card. Semantic ranking không chạy. Knowledge chunks=2, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

**GroundedContext:** {"products": 0, "knowledge_chunks": 2, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `ollama`; 2 nguồn. Text thực, khớp UI:

> Cửa hàng có chính sách đổi trả trong 7 ngày kể từ lúc nhận hàng, với điều kiện: có hóa đơn, sản phẩm còn đầy đủ phụ kiện và không bị hư hỏng do người sử dụng. Đây là chính sách DEMO kiểm thử, không phải chính sách chính thức. Chính sách thực tế phải được cửa hàng xác nhận.

**Diagnosis / first fault:** KNOWLEDGE_QA; đáp án ghi rõ DEMO, không phải chính sách chính thức, phải cửa hàng xác nhận. Câu mở đầu Cửa hàng có chính sách... hơi khẳng định nhưng được đính chính rõ trong cùng response. Chunk payment không liên quan vẫn vào nguồn; không đánh thành bịa chính sách production.

**Owner:** PERSON_3. **Severity:** LOW. **Verdict:** PASS_WITH_WARNINGS.

**Trace evidence:** [T05.jsonl](evidence/phase01/T05.jsonl).

### T06 — PASS

**Prompt:** Tôi muốn biết tình trạng đơn hàng.

**Expected:** Tra đơn; không tự suy đoán mã/trạng thái.

**Thực thi:** 2026-10-13T13:30:57.599577+07:00; session `7c4e714d-776c-4348-bd6a-e8a847f320b6`; trace `4b78ac22-26d8-4adf-bb1c-6d268ae88eb0`. HTTP 200; UI/API latency 1.803s; pipeline 1.717s. Fresh history=0.

**QueryPlan:**
```json
{
  "intent": "ORDER_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Không có effective ProductSearchPlan; không đi product path.

**Trace sequence:** query.validation → query.plan.initial → customer.context → backend.customer_http → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[
  {
    "name": "backend.customer_http",
    "method": "GET",
    "path": "/api/orders/mine",
    "params": null
  }
]
```

**Retrieval:** 0 product/variant pairs, 0 unique products; 0 ProductMatch; 0 card. Semantic ranking không chạy. Knowledge chunks=0, customer orders=1. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

Evidence customer có 1 recent order với status PENDING; câu trả lời status được đối chiếu với GET backend độc lập. Không công bố full personal DTO hoặc mã đơn trong evidence xuất.

**GroundedContext:** {"products": 0, "knowledge_chunks": 0, "order_count": 1, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `ollama`; 0 nguồn. Text thực, khớp UI:

> Tình trạng đơn hàng hiện tại là PENDING.

**Diagnosis / first fault:** ORDER_STATUS; 1 đơn có status PENDING từ GET /api/orders/mine; response không bịa mã/trạng thái. Chưa thử trường hợp nhiều đơn hoặc không có đơn.

**Owner:** PERSON_1. **Severity:** PASS. **Verdict:** PASS.

**Trace evidence:** [T06.jsonl](evidence/phase01/T06.jsonl).

### T07 — PASS_WITH_WARNINGS

**Prompt:** Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB.

**Expected:** Laptop; max_price ≤20 triệu; min_ram ≥16; mọi card đúng backend/điều kiện.

**Thực thi:** 2026-10-13T13:31:10.500913+07:00; session `18e27386-8c7c-415a-833c-40df1ea41c66`; trace `31df84ee-6d35-49e5-88d0-6ae9549c8bea`. HTTP 200; UI/API latency 6.557s; pipeline 6.465s. Fresh history=0.

**QueryPlan:**
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

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Effective plan giống plan gốc: {"product_search": {"category": "Laptop", "brands": [], "min_price": null, "max_price": "20000000", "min_ram_gb": 16, "min_storage_gb": null, "cpu_keywords": [], "in_stock_only": false, "semantic_query": "laptop giá dưới 20 triệu, RAM ít nhất 16GB", "top_k": 5}, "below_minimum": false, "cheaper_followup": false}

**Trace sequence:** query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.vector_ranking → backend.product_http → product.cards → backend.product_http → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/search/advanced",
    "params": [
      [
        "limit",
        "100"
      ],
      [
        "offset",
        "0"
      ],
      [
        "category",
        "Laptop"
      ],
      [
        "maxPrice",
        "20000000"
      ],
      [
        "minRamGb",
        "16"
      ],
      [
        "warehouseId",
        "d13ea247-ab70-473b-80d6-c8adea60334c"
      ],
      [
        "inStockOnly",
        "false"
      ]
    ]
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/a96df51c-538c-4fa9-8018-6b49ca025b34/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/a96df51c-538c-4fa9-8018-6b49ca025b34/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  }
]
```

**Retrieval:** 1 product/variant pairs, 1 unique products; 1 ProductMatch; 1 card. Semantic ranking có chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

Đối chiếu từng card với backend live:

| Product / SKU | Category / Brand | Giá | RAM | Tồn kho | Card khớp live? |
|---|---|---|---|---|---|
| Dell Inspiron 15 / DEV-DELL-I15-512 | Laptop / Dell | 15,990,000 | 16GB | 2 | True |

**Chú ý:** khớp dữ liệu thật không đồng nghĩa khớp điều kiện người dùng.

Backend GET đối chiếu đúng intended filter: HTTP 200, 1 candidate pairs.

**GroundedContext:** {"products": 1, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `ollama`; 0 nguồn. Text thực, khớp UI:

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

**Diagnosis / first fault:** category Laptop, max_price 20 triệu, min_ram 16 đúng; 1 card Dell Inspiron 15 giá 15.990.000/RAM16 đúng backend. ProductMatch thiếu name/brand khiến câu văn ghi thương hiệu không xác định, dù backend/card biết danh tính.

**Owner:** SHARED. **Severity:** LOW. **Verdict:** PASS_WITH_WARNINGS.

**Trace evidence:** [T07.jsonl](evidence/phase01/T07.jsonl).

### T08 — FAIL

**Prompt:** Tìm laptop Dell có RAM 16GB, giá không quá 20 triệu.

**Expected:** Laptop + Dell + RAM16 + giá ≤20 triệu.

**Thực thi:** 2026-10-13T13:31:28.226711+07:00; session `ceea4ed7-1616-4ba2-a91e-35e5ef8e2459`; trace `015da243-1c59-4352-ac63-bd6dd26cf919`. HTTP 200; UI/API latency 15.449s; pipeline 15.341s. Fresh history=0.

**QueryPlan:**
```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": 16,
    "min_storage_gb": 1,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "laptop dell ram 16gb gia khong qua 20 trieu",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Effective plan giống plan gốc: {"product_search": {"category": "Laptop", "brands": [], "min_price": null, "max_price": null, "min_ram_gb": 16, "min_storage_gb": 1, "cpu_keywords": [], "in_stock_only": false, "semantic_query": "laptop dell ram 16gb gia khong qua 20 trieu", "top_k": 5}, "below_minimum": false, "cheaper_followup": false}

**Trace sequence:** query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.vector_ranking → backend.product_http → backend.product_http → backend.product_http → backend.product_http → product.cards → backend.product_http → backend.product_http → backend.product_http → backend.product_http → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/search/advanced",
    "params": [
      [
        "limit",
        "100"
      ],
      [
        "offset",
        "0"
      ],
      [
        "category",
        "Laptop"
      ],
      [
        "minRamGb",
        "16"
      ],
      [
        "minStorageGb",
        "1"
      ],
      [
        "warehouseId",
        "d13ea247-ab70-473b-80d6-c8adea60334c"
      ],
      [
        "inStockOnly",
        "false"
      ]
    ]
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/de24f73c-c12f-43e6-ba87-13308ab4980c/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/a96df51c-538c-4fa9-8018-6b49ca025b34/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/0bc0ada3-c4e9-41a1-9b5c-93c57c3f6ead/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/e72104a6-2b6e-4c89-a97a-b96f8389283a/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/de24f73c-c12f-43e6-ba87-13308ab4980c/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/a96df51c-538c-4fa9-8018-6b49ca025b34/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/0bc0ada3-c4e9-41a1-9b5c-93c57c3f6ead/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/e72104a6-2b6e-4c89-a97a-b96f8389283a/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  }
]
```

**Retrieval:** 9 product/variant pairs, 4 unique products; 4 ProductMatch; 4 card. Semantic ranking có chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

Đối chiếu từng card với backend live:

| Product / SKU | Category / Brand | Giá | RAM | Tồn kho | Card khớp live? |
|---|---|---|---|---|---|
| Dell XPS 13 / DEV-XPS13-SL-512 | Laptop / Dell | 32,990,000 | 16GB | 2 | True |
| Dell Inspiron 15 / DEV-DELL-I15-512 | Laptop / Dell | 15,990,000 | 16GB | 2 | True |
| ASUS ROG Zephyrus G14 / DEV-ROGG14-GR-512 | Laptop / ASUS | 39,990,000 | 16GB | 2 | True |
| MacBook Pro 14 M3 / DEV-MBP14-M3-SL-512 | Laptop / Apple | 44,990,000 | 16GB | 2 | True |

**Chú ý:** khớp dữ liệu thật không đồng nghĩa khớp điều kiện người dùng.

Backend GET đối chiếu đúng intended filter: HTTP 200, 1 candidate pairs.

**GroundedContext:** {"products": 4, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `ollama`; 0 nguồn. Text thực, khớp UI:

> Có 3 sản phẩm laptop Dell có RAM 16GB và giá không quá 20 triệu:
> 
> 1. **DEV-DELL-I15-512**  
>    - Giá: 15.990.000 VNĐ  
>    - RAM: 16GB  
>    - Bộ nhớ: 512GB  
>    - Màu: Bạc  
>    - Tồn kho: 2 chiếc  
> 
> 2. **DEV-XPS13-SL-512**  
>    - Giá: 32.990.000 VNĐ (quá 20 triệu) → **không đáp ứng**  
> 
> 3. **DEV-XPS13-GR-1TB**  
>    - Giá: 36.990.000 VNĐ (quá 20 triệu) → **không đáp ứng**  
> 
> → **Chỉ có sản phẩm DEV-DELL-I15-512** đáp ứng đầy đủ điều kiện: Dell, RAM 16GB, giá không quá 20 triệu.  
> 
> **Kết luận**: Có 1 sản phẩm phù hợp.

**Diagnosis / first fault:** brands=[] và max_price=null; tự thêm min_storage_gb=1. Brand/giá bị đẩy vào semantic text, không thành hard filter. 4 card có 3 card ngoài ngân sách, 2 card không phải Dell; câu văn có kết luận 1 sản phẩm đúng nhưng không sửa được card sai.

**Owner:** PERSON_3. **Severity:** HIGH. **Verdict:** FAIL.

**Trace evidence:** [T08.jsonl](evidence/phase01/T08.jsonl).

### T09 — PASS_WITH_WARNINGS

**Prompt:** Tìm iPhone giá dưới 1 triệu.

**Expected:** Không đề xuất iPhone đắt như thể thỏa dưới 1 triệu.

**Thực thi:** 2026-10-13T13:31:55.051349+07:00; session `fca0b4db-e726-457f-b9a4-4cf534c7b39c`; trace `fa84c8de-70b4-4afa-b3aa-23ececfba7dd`. HTTP 200; UI/API latency 1.908s; pipeline 1.795s. Fresh history=0.

**QueryPlan:**
```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": "iPhone",
    "brands": [],
    "min_price": null,
    "max_price": "1000000",
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "iphone gia duoi 1 trieu",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Effective plan giống plan gốc: {"product_search": {"category": "iPhone", "brands": [], "min_price": null, "max_price": "1000000", "min_ram_gb": null, "min_storage_gb": null, "cpu_keywords": [], "in_stock_only": false, "semantic_query": "iphone gia duoi 1 trieu", "top_k": 5}, "below_minimum": false, "cheaper_followup": false}

**Trace sequence:** query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.cards → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/search/advanced",
    "params": [
      [
        "limit",
        "100"
      ],
      [
        "offset",
        "0"
      ],
      [
        "category",
        "iPhone"
      ],
      [
        "maxPrice",
        "1000000"
      ],
      [
        "warehouseId",
        "d13ea247-ab70-473b-80d6-c8adea60334c"
      ],
      [
        "inStockOnly",
        "false"
      ]
    ]
  }
]
```

**Retrieval:** 0 product/variant pairs, 0 unique products; 0 ProductMatch; 0 card. Semantic ranking không chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

Backend GET đối chiếu đúng intended filter: HTTP 200, 0 candidate pairs.

**GroundedContext:** {"products": 0, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `no_matching_products`; 0 nguồn. Text thực, khớp UI:

> Mình chưa tìm thấy sản phẩm phù hợp với các điều kiện hiện tại.

**Diagnosis / first fault:** max_price=1 triệu, không card/câu quảng cáo iPhone đắt. category=iPhone sai taxonomy (iPhone là dòng sản phẩm, catalog dùng Điện thoại/Apple). Backend query đúng taxonomy + giá cũng 0, nên negative result đạt nhưng không chứng minh ánh xạ đúng.

**Owner:** PERSON_3. **Severity:** LOW. **Verdict:** PASS_WITH_WARNINGS.

**Trace evidence:** [T09.jsonl](evidence/phase01/T09.jsonl).

### T10 — FAIL

**Prompt:** Tìm laptop RAM ít nhất 32GB, giá dưới 20 triệu.

**Expected:** RAM≥32, giá dưới 20 triệu hoặc báo không có kết quả; không card sai điều kiện.

**Thực thi:** 2026-10-13T13:32:07.946502+07:00; session `68191d02-b66a-492b-af8a-874ad051775d`; trace `dcfedb4a-1cf4-4a4c-a3a8-f44f2434ab7d`. HTTP 200; UI/API latency 7.218s; pipeline 7.119s. Fresh history=0.

**QueryPlan:**
```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": 32,
    "min_storage_gb": 1,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "laptop ram 32gb gia duoi 20 trieu",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Effective plan giống plan gốc: {"product_search": {"category": "Laptop", "brands": [], "min_price": null, "max_price": null, "min_ram_gb": 32, "min_storage_gb": 1, "cpu_keywords": [], "in_stock_only": false, "semantic_query": "laptop ram 32gb gia duoi 20 trieu", "top_k": 5}, "below_minimum": false, "cheaper_followup": false}

**Trace sequence:** query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.vector_ranking → backend.product_http → backend.product_http → product.cards → backend.product_http → backend.product_http → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/search/advanced",
    "params": [
      [
        "limit",
        "100"
      ],
      [
        "offset",
        "0"
      ],
      [
        "category",
        "Laptop"
      ],
      [
        "minRamGb",
        "32"
      ],
      [
        "minStorageGb",
        "1"
      ],
      [
        "warehouseId",
        "d13ea247-ab70-473b-80d6-c8adea60334c"
      ],
      [
        "inStockOnly",
        "false"
      ]
    ]
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/0bc0ada3-c4e9-41a1-9b5c-93c57c3f6ead/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/de24f73c-c12f-43e6-ba87-13308ab4980c/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/0bc0ada3-c4e9-41a1-9b5c-93c57c3f6ead/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/de24f73c-c12f-43e6-ba87-13308ab4980c/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  }
]
```

**Retrieval:** 3 product/variant pairs, 2 unique products; 2 ProductMatch; 2 card. Semantic ranking có chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

Đối chiếu từng card với backend live:

| Product / SKU | Category / Brand | Giá | RAM | Tồn kho | Card khớp live? |
|---|---|---|---|---|---|
| ASUS ROG Zephyrus G14 / DEV-ROGG14-WH-1TB | Laptop / ASUS | 49,990,000 | 32GB | 2 | True |
| Dell XPS 13 / DEV-XPS13-SL-1TB | Laptop / Dell | 41,990,000 | 32GB | 2 | True |

**Chú ý:** khớp dữ liệu thật không đồng nghĩa khớp điều kiện người dùng.

Backend GET đối chiếu đúng intended filter: HTTP 200, 0 candidate pairs.

**GroundedContext:** {"products": 2, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `ollama`; 0 nguồn. Text thực, khớp UI:

> Không có sản phẩm nào đáp ứng điều kiện giá dưới 20 triệu đồng với RAM ít nhất 32GB. Tất cả các sản phẩm tìm thấy có giá từ 41.990.000 đến 49.990.000 đồng.

**Diagnosis / first fault:** min_ram=32 đúng nhưng max_price=null, tự thêm storage>=1. 2 card giá 41.990.000 và 49.990.000 vượt 20 triệu; câu văn nói không tìm thấy phù hợp nhưng UI vẫn hiển thị các card như kết quả. Backend intended filters trả 0.

**Owner:** PERSON_3. **Severity:** HIGH. **Verdict:** FAIL.

**Trace evidence:** [T10.jsonl](evidence/phase01/T10.jsonl).

### T11 — FAIL

**Prompt:** Tìm điện thoại Xiaomi dưới 20 triệu.

**Expected:** Điện thoại + Xiaomi + giá dưới 20 triệu.

**Thực thi:** 2026-10-13T13:32:26.312385+07:00; session `1cdc5b46-011c-4d84-8378-dcf579b2cf25`; trace `e0a315e3-6beb-4edf-91e7-79575e95be12`. HTTP 200; UI/API latency 1.920s; pipeline 1.841s. Fresh history=0.

**QueryPlan:**
```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": "Smartphone",
    "brands": [],
    "min_price": null,
    "max_price": "20000000",
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "dien thoai xiaomi duoi 20 trieu",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Effective plan giống plan gốc: {"product_search": {"category": "Smartphone", "brands": [], "min_price": null, "max_price": "20000000", "min_ram_gb": null, "min_storage_gb": null, "cpu_keywords": [], "in_stock_only": false, "semantic_query": "dien thoai xiaomi duoi 20 trieu", "top_k": 5}, "below_minimum": false, "cheaper_followup": false}

**Trace sequence:** query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.cards → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/search/advanced",
    "params": [
      [
        "limit",
        "100"
      ],
      [
        "offset",
        "0"
      ],
      [
        "category",
        "Smartphone"
      ],
      [
        "maxPrice",
        "20000000"
      ],
      [
        "warehouseId",
        "d13ea247-ab70-473b-80d6-c8adea60334c"
      ],
      [
        "inStockOnly",
        "false"
      ]
    ]
  }
]
```

**Retrieval:** 0 product/variant pairs, 0 unique products; 0 ProductMatch; 0 card. Semantic ranking không chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

Backend GET đối chiếu đúng intended filter: HTTP 200, 5 candidate pairs.

**GroundedContext:** {"products": 0, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `no_matching_products`; 0 nguồn. Text thực, khớp UI:

> Mình chưa tìm thấy sản phẩm phù hợp với các điều kiện hiện tại.

**Diagnosis / first fault:** category=Smartphone sai taxonomy Điện thoại, brands=[] bỏ Xiaomi; max_price=20 triệu đúng. 0 candidate trong plan sai; backend intended query Điện thoại + Xiaomi + giá trả 5 variant, nên đây là false negative.

**Owner:** PERSON_3. **Severity:** HIGH. **Verdict:** FAIL.

**Trace evidence:** [T11.jsonl](evidence/phase01/T11.jsonl).

### T12 — FAIL

**Prompt:** Tìm điện thoại RAM tối thiểu 12GB.

**Expected:** Điện thoại RAM≥12, không nhầm storage.

**Thực thi:** 2026-10-13T13:32:39.286606+07:00; session `6943224e-3b48-4697-876c-47b75183f8e6`; trace `321dc3b4-00cb-4ff2-8cdb-3c2efae3fcb3`. HTTP 200; UI/API latency 17.963s; pipeline 17.861s. Fresh history=0.

**QueryPlan:**
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
    "min_ram_gb": 12,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "điện thoại RAM tối thiểu 12GB",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Effective plan giống plan gốc: {"product_search": {"category": null, "brands": [], "min_price": null, "max_price": null, "min_ram_gb": 12, "min_storage_gb": null, "cpu_keywords": [], "in_stock_only": false, "semantic_query": "điện thoại RAM tối thiểu 12GB", "top_k": 5}, "below_minimum": false, "cheaper_followup": false}

**Trace sequence:** query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.vector_ranking → backend.product_http → backend.product_http → backend.product_http → backend.product_http → backend.product_http → product.cards → backend.product_http → backend.product_http → backend.product_http → backend.product_http → backend.product_http → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/search/advanced",
    "params": [
      [
        "limit",
        "100"
      ],
      [
        "offset",
        "0"
      ],
      [
        "minRamGb",
        "12"
      ],
      [
        "warehouseId",
        "d13ea247-ab70-473b-80d6-c8adea60334c"
      ],
      [
        "inStockOnly",
        "false"
      ]
    ]
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/169710dd-d072-4e83-9fd0-e618d706ee26/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/3fc00d9b-72e8-43de-8d5d-a275251aa08f/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/d08f9c12-2196-4fc3-8aab-78d763628ce6/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/6037875f-9598-49ad-92d4-5d48cc48d501/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/0bc0ada3-c4e9-41a1-9b5c-93c57c3f6ead/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/169710dd-d072-4e83-9fd0-e618d706ee26/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/3fc00d9b-72e8-43de-8d5d-a275251aa08f/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/d08f9c12-2196-4fc3-8aab-78d763628ce6/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/6037875f-9598-49ad-92d4-5d48cc48d501/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/0bc0ada3-c4e9-41a1-9b5c-93c57c3f6ead/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  }
]
```

**Retrieval:** 19 product/variant pairs, 8 unique products; 5 ProductMatch; 5 card. Semantic ranking có chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

Đối chiếu từng card với backend live:

| Product / SKU | Category / Brand | Giá | RAM | Tồn kho | Card khớp live? |
|---|---|---|---|---|---|
| Xiaomi 15 / DEV-X15-BLK-256 | Điện thoại / Xiaomi | 18,990,000 | 12GB | 2 | True |
| Xiaomi 14T Pro / DEV-X14TP-BLK-256 | Điện thoại / Xiaomi | 16,990,000 | 12GB | 2 | True |
| Samsung Galaxy A55 / DEV-A55-ICE-256 | Điện thoại / Samsung | 10,490,000 | 12GB | 2 | True |
| Samsung Galaxy Z Flip6 / DEV-ZF6-GRY-256 | Điện thoại / Samsung | 24,990,000 | 12GB | 2 | True |
| ASUS ROG Zephyrus G14 / DEV-ROGG14-GR-512 | Laptop / ASUS | 39,990,000 | 16GB | 2 | True |

**Chú ý:** khớp dữ liệu thật không đồng nghĩa khớp điều kiện người dùng.

Backend GET đối chiếu đúng intended filter: HTTP 200, 10 candidate pairs.

**GroundedContext:** {"products": 5, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `ollama`; 0 nguồn. Text thực, khớp UI:

> Các điện thoại có RAM tối thiểu 12GB được tìm thấy, bao gồm:
> 
> - **DEV-X15**: Đen (256GB), Trắng (512GB), Xanh lá (512GB)  
> - **DEV-X14TP**: Đen (256GB), Xanh (512GB), Xám (512GB)  
> - **DEV-A55**: Xanh băng (256GB)  
> - **DEV-ZF6**: Xám (256GB), Vàng (256GB), Xanh (512GB)  
> - **DEV-ROGG14**: Xám (512GB), Trắng (1TB), Xám (1TB)  
> 
> Tất cả các phiên bản đều có RAM từ 12GB. Số lượng tồn kho cho mỗi phiên bản là 2. Giá và bảo hành được cung cấp trong từng phiên bản.

**Diagnosis / first fault:** min_ram=12 đúng, không nhầm sang storage. category=null khiến lọc cả laptop; 5 card có ASUS ROG RAM16 ngoài yêu cầu điện thoại. Text còn gọi nhóm này là điện thoại. Backend thực hiện đúng plan rộng; lỗi đầu tiên là thiếu category trong QueryPlan.

**Owner:** PERSON_3. **Severity:** HIGH. **Verdict:** FAIL.

**Trace evidence:** [T12.jsonl](evidence/phase01/T12.jsonl).

### T13 — PASS_WITH_WARNINGS

**Prompt:** Tìm laptop RAM 16GB đang còn hàng.

**Expected:** Laptop RAM16 còn hàng; đọc tồn kho thật, không bịa số lượng.

**Thực thi:** 2026-10-13T13:33:08.600872+07:00; session `9df22d69-3103-4c0b-af96-ae93cf0da458`; trace `b13b7e40-7f93-4c15-abf0-d293e8531560`. HTTP 200; UI/API latency 24.523s; pipeline 24.418s. Fresh history=0.

**QueryPlan:**
```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": 16,
    "min_storage_gb": 1,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "laptop RAM 16GB",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Effective plan giống plan gốc: {"product_search": {"category": "Laptop", "brands": [], "min_price": null, "max_price": null, "min_ram_gb": 16, "min_storage_gb": 1, "cpu_keywords": [], "in_stock_only": false, "semantic_query": "laptop RAM 16GB", "top_k": 5}, "below_minimum": false, "cheaper_followup": false}

**Trace sequence:** query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.vector_ranking → backend.product_http → backend.product_http → backend.product_http → backend.product_http → product.cards → backend.product_http → backend.product_http → backend.product_http → backend.product_http → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/search/advanced",
    "params": [
      [
        "limit",
        "100"
      ],
      [
        "offset",
        "0"
      ],
      [
        "category",
        "Laptop"
      ],
      [
        "minRamGb",
        "16"
      ],
      [
        "minStorageGb",
        "1"
      ],
      [
        "warehouseId",
        "d13ea247-ab70-473b-80d6-c8adea60334c"
      ],
      [
        "inStockOnly",
        "false"
      ]
    ]
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/0bc0ada3-c4e9-41a1-9b5c-93c57c3f6ead/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/de24f73c-c12f-43e6-ba87-13308ab4980c/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/a96df51c-538c-4fa9-8018-6b49ca025b34/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/e72104a6-2b6e-4c89-a97a-b96f8389283a/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/0bc0ada3-c4e9-41a1-9b5c-93c57c3f6ead/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/de24f73c-c12f-43e6-ba87-13308ab4980c/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/a96df51c-538c-4fa9-8018-6b49ca025b34/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/e72104a6-2b6e-4c89-a97a-b96f8389283a/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  }
]
```

**Retrieval:** 9 product/variant pairs, 4 unique products; 4 ProductMatch; 4 card. Semantic ranking có chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

Đối chiếu từng card với backend live:

| Product / SKU | Category / Brand | Giá | RAM | Tồn kho | Card khớp live? |
|---|---|---|---|---|---|
| ASUS ROG Zephyrus G14 / DEV-ROGG14-GR-512 | Laptop / ASUS | 39,990,000 | 16GB | 2 | True |
| Dell XPS 13 / DEV-XPS13-SL-512 | Laptop / Dell | 32,990,000 | 16GB | 2 | True |
| Dell Inspiron 15 / DEV-DELL-I15-512 | Laptop / Dell | 15,990,000 | 16GB | 2 | True |
| MacBook Pro 14 M3 / DEV-MBP14-M3-SL-512 | Laptop / Apple | 44,990,000 | 16GB | 2 | True |

**Chú ý:** khớp dữ liệu thật không đồng nghĩa khớp điều kiện người dùng.

Backend GET đối chiếu đúng intended filter: HTTP 200, 9 candidate pairs.

**GroundedContext:** {"products": 4, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `ollama`; 0 nguồn. Text thực, khớp UI:

> Có các sản phẩm laptop RAM 16GB đang còn hàng:
> 
> 1. **DEV-ROGG14-GR-512**  
>    - RAM: 16GB, Bộ nhớ: 512GB, Màu: Xám  
>    - Số lượng còn: 2  
>    - Giá: 39.990.000 VNĐ  
> 
> 2. **DEV-XPS13-SL-512**  
>    - RAM: 16GB, Bộ nhớ: 512GB, Màu: Bạc  
>    - Số lượng còn: 2  
>    - Giá: 32.990.000 VNĐ  
> 
> 3. **DEV-XPS13-GR-1TB**  
>    - RAM: 16GB, Bộ nhớ: 1TB, Màu: Xám  
>    - Số lượng còn: 2  
>    - Giá: 36.990.000 VNĐ  
> 
> 4. **DEV-DELL-I15-512**  
>    - RAM: 16GB, Bộ nhớ: 512GB, Màu: Bạc  
>    - Số lượng còn: 2  
>    - Giá: 15.990.000 VNĐ  
> 
> 5. **DEV-MBP14-M3-SL-512**  
>    - RAM: 16GB, Bộ nhớ: 512GB, Màu: Bạc  
>    - Số lượng còn: 2  
>    - Giá: 44.990.000 VNĐ  
> 
> 6. **DEV-MBP14-M3-SG-1TB**  
>    - RAM: 16GB, Bộ nhớ: 1TB, Màu: Xám không gian  
>    - Số lượng còn: 1  
>    - Giá: 49.990.000 VNĐ  
> 
> Lưu ý: Tất cả sản phẩm đều có RAM từ 16GB và đang còn hàng.

**Diagnosis / first fault:** Các card và số tồn kho trong văn bản khớp backend tại warehouse thật; không bịa số lượng. Nhưng in_stock_only=false và semantic query mất đang còn hàng; không enforce stock filter, tự thêm storage>=1. Dataset hiện tất cả variants được chọn đều có tồn nên chưa thấy false positive sold-out.

**Owner:** PERSON_3. **Severity:** MEDIUM. **Verdict:** PASS_WITH_WARNINGS.

**Trace evidence:** [T13.jsonl](evidence/phase01/T13.jsonl).

### T14 — FAIL

**Prompt:** Tìm laptop giá từ 25 triệu đến 20 triệu.

**Expected:** Nhận ra/giải quyết 25 triệu→20 triệu mâu thuẫn; không trình bày card như kết quả hợp lệ.

**Thực thi:** 2026-10-13T13:33:44.326792+07:00; session `7eb4bcbd-4f22-48df-aee8-4b7a723d092c`; trace `07dad0d1-434c-4ecc-820d-c978f36a9e61`. HTTP 200; UI/API latency 12.133s; pipeline 12.011s. Fresh history=0.

**QueryPlan:**
```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": null,
    "brands": [],
    "min_price": "25000000",
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": 1,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "laptop gia tu 25 trieu den 20 trieu",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Validation:** 1 model attempt; 0 schema error, không repair/fallback. Trace status `ok` chỉ nghĩa không exception, không đánh giá đúng ý định. Effective plan giống plan gốc: {"product_search": {"category": null, "brands": [], "min_price": "25000000", "max_price": null, "min_ram_gb": null, "min_storage_gb": 1, "cpu_keywords": [], "in_stock_only": false, "semantic_query": "laptop gia tu 25 trieu den 20 trieu", "top_k": 5}, "below_minimum": false, "cheaper_followup": false}

**Trace sequence:** query.validation → query.plan.initial → query.plan.effective → product.search → backend.product_http → product.vector_ranking → backend.product_http → backend.product_http → backend.product_http → backend.product_http → backend.product_http → product.cards → backend.product_http → backend.product_http → backend.product_http → backend.product_http → backend.product_http → customer.context → response.grounded_context → response.generate → response.strategy.

**Backend endpoints / query:**
```json
[
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/search/advanced",
    "params": [
      [
        "limit",
        "100"
      ],
      [
        "offset",
        "0"
      ],
      [
        "minPrice",
        "25000000"
      ],
      [
        "minStorageGb",
        "1"
      ],
      [
        "warehouseId",
        "d13ea247-ab70-473b-80d6-c8adea60334c"
      ],
      [
        "inStockOnly",
        "false"
      ]
    ]
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/de24f73c-c12f-43e6-ba87-13308ab4980c/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/0bc0ada3-c4e9-41a1-9b5c-93c57c3f6ead/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/e72104a6-2b6e-4c89-a97a-b96f8389283a/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/6037875f-9598-49ad-92d4-5d48cc48d501/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/f4d2c8ad-f0fd-4f4d-b3c3-bfa601014af5/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/de24f73c-c12f-43e6-ba87-13308ab4980c/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/0bc0ada3-c4e9-41a1-9b5c-93c57c3f6ead/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/e72104a6-2b6e-4c89-a97a-b96f8389283a/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/6037875f-9598-49ad-92d4-5d48cc48d501/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  },
  {
    "name": "backend.product_http",
    "method": "GET",
    "path": "/api/v1/products/f4d2c8ad-f0fd-4f4d-b3c3-bfa601014af5/detail",
    "params": {
      "warehouseId": "d13ea247-ab70-473b-80d6-c8adea60334c"
    }
  }
]
```

**Retrieval:** 11 product/variant pairs, 5 unique products; 5 ProductMatch; 5 card. Semantic ranking có chạy. Knowledge chunks=0, customer orders=0. Với nhánh không gọi, số 0 không phải query đã thực hiện rồi rỗng.

Đối chiếu từng card với backend live:

| Product / SKU | Category / Brand | Giá | RAM | Tồn kho | Card khớp live? |
|---|---|---|---|---|---|
| Dell XPS 13 / DEV-XPS13-SL-512 | Laptop / Dell | 32,990,000 | 16GB | 2 | True |
| ASUS ROG Zephyrus G14 / DEV-ROGG14-GR-512 | Laptop / ASUS | 39,990,000 | 16GB | 2 | True |
| MacBook Pro 14 M3 / DEV-MBP14-M3-SG-512 | Laptop / Apple | 39,990,000 | 8GB | 2 | True |
| Samsung Galaxy Z Flip6 / DEV-ZF6-BLU-512 | Điện thoại / Samsung | 28,990,000 | 12GB | 2 | True |
| iPhone 16 / DEV-IP16-PNK-512 | Điện thoại / Apple | 30,990,000 | 8GB | 2 | True |

**Chú ý:** khớp dữ liệu thật không đồng nghĩa khớp điều kiện người dùng.

Backend GET đối chiếu đúng intended filter: HTTP 400, báo bộ lọc không hợp lệ.

**GroundedContext:** {"products": 5, "knowledge_chunks": 0, "order_count": 0, "cart_present": false}. ProductMatch chứa ID/variants/SKU/giá/RAM/stock/reasons, không trường name/brand/category đầy đủ. Với stock: detail có warehouse; đừng suy ra in_stock_only=true chỉ vì card đang còn hàng.

**Response:** `ollama`; 0 nguồn. Text thực, khớp UI:

> Chưa đủ dữ liệu để xác định laptop có giá từ 25 triệu đến 20 triệu vì giá trị tối thiểu yêu cầu (25 triệu) cao hơn giá trị tối đa yêu cầu (20 triệu). Không có sản phẩm nào đáp ứng điều kiện này.

**Diagnosis / first fault:** min_price=25 triệu nhưng max_price=null và category=null; schema không nhìn thấy mâu thuẫn, không repair. Text nhận ra 25>20 và nói không sản phẩm phù hợp, nhưng pipeline vẫn search và trả 5 card, có cả điện thoại. Đánh FAIL ở output text+cards không nhất quán, không kết luận riêng text không nhận ra mâu thuẫn.

**Owner:** PERSON_3. **Severity:** HIGH. **Verdict:** FAIL.

**Trace evidence:** [T14.jsonl](evidence/phase01/T14.jsonl).

## 4. Confirmed findings, nguyên nhân và đề xuất

| Finding | Cases | FIRST FAULT | Severity / Owner | Chứng cứ và đề xuất (chưa sửa) |
|---|---|---|---|---|
| F01 — HELP thành GREETING | T03 | QUERY_UNDERSTANDING | LOW / Người 3 | Plan GREETING; response vẫn có trợ giúp tối thiểu. Thêm intent examples/eval cho lời hỏi khả năng và test branch HELP |
| F02 — Điều kiện cứng nằm trong semantic query nhưng không vào filter | T08, T10, T12 | QUERY_UNDERSTANDING | HIGH / Người 3 + Shared validation | Mất brand/giá/category, card sai yêu cầu. Chuẩn hóa và kiểm tra coverage các constraint trước retrieval; regression kiểm cả cards và text |
| F03 — Tên taxonomy không hợp catalog | T09, T11 | QUERY_UNDERSTANDING | HIGH tiềm năng; T09 LOW hiện tại / Người 3 | iPhone/Smartphone không phải category Điện thoại. T11 baseline có 5 variants, actual0. Dùng taxonomy/identity chuẩn, phân biệt brand với product family |
| F04 — Stock condition không enforce | T13 | QUERY_UNDERSTANDING | MEDIUM / Người 3; Người 2 regression | in_stock_only=false dù user hỏi còn hàng; dữ liệu hiện đều còn nên outcome đạt có cảnh báo. Test với sold-out ở dataset chuyên biệt đã được phép, không tạo seed trong checkpoint này |
| F05 — Mâu thuẫn bị mất trước validator, text/card bất nhất | T14 | QUERY_UNDERSTANDING | HIGH / Người 3 + Người 1 guard | max_price bị bỏ; model nhận ra muộn trong response, cards vẫn5. Giữ bounds, xác nhận lại khi conflict, cấm thực thi/lập cards trước xử lý conflict |
| F06 — ProductMatch thiếu identity evidence | T07, hỗ trợ giải thích T12 | GROUNDING | LOW / Shared (Người 2/3) | Backend/card biết Dell, model context thiếu brand/name. Bổ sung contract/mapping có kiểm thử gắn product với variant; chưa sửa schema/contract |
| F07 — RAG đưa nguồn không liên quan | T05 | KNOWLEDGE_RETRIEVAL | LOW / Người 3 | Chunk payment vẫn vào đổi trả. Cần eval relevance/ngưỡng/rerank và cách trình bày DEMO; không đặt ngưỡng tùy tiện từ 2 chunk |
| F08 — Thêm storage>=1 không được yêu cầu | T08, T10, T13, T14 | QUERY_UNDERSTANDING | LOW hiện tại / Người 3 | Không gây mất result trong dataset này nhưng là constraint tự sinh. Test field vắng mặt phải giữ null, đặc biệt model thấy minimum=1 trong schema; nguồn gây suy diễn chưa chứng minh |

Không có evidence backend/SQL lọc sai plan nhận được: filter queries và live detail đều khớp. Semantic ranking chỉ xếp similarity, không thể khôi phục điều kiện giá/brand đã thiếu trong plan. Không sửa response prompt đơn thuần để che cards sai.

T04 có text range 8.490.000–28.990.000 trong khi card Z Flip6 chọn variant rẻ nhất 24.990.000: 28.990.000 thuộc variant khác của cùng ProductMatch trong GroundedContext. Đây là khác biệt phạm vi variant/card, không tự động kết luận model bịa giá.

Các claim “giá/RAM bị bịa” không được xác nhận: giá/RAM các card đúng catalog. Sai là chọn ngoài yêu cầu. Chưa chứng minh lỗi seed hoặc index; không có BLOCKED_ENV. Chưa thử nhiều đơn, data rỗng, sold-out, model outage hoặc lặp prompts nhiều sampling trong checkpoint này.

## 5. Kết luận, tests và HANDOFF checkpoint 02

Nhóm greeting và order context đạt trong dữ liệu hiện tại. Bốn constraint flows đặc biệt T08/T10/T11/T12 và conflict T14 cần xử lý trước demo tự do; T07 là ví dụ end-to-end filter tốt nhưng còn thiếu identity trong văn bản. Cả 14 request HTTP200, schema validation pass, nhưng có 5 FAIL nên không công bố tỷ lệ thành công 100%.

Đối chiếu artifact tự động: 14 trace UUID/session duy nhất, events theo offset tăng dần, history0, API = chat.pipeline = text/card names trên UI, mọi giá/RAM/SKU/tồn kho card và eligible variant khớp backend baseline; 0 dropped/truncated. Scan JSON/excerpts không có credential/DSN/contact DTO hoặc embedding vectors.

Tests: preflight tracing/query/product contract **22 pass**; focused cuối gồm test_tracing, test_ai_services, test_ai_chat_flow, test_real_product_advisor, test_query_contracts, test_product_contracts **62 pass**. Một Starlette/httpx deprecation warning; không test fail. Đây là tests mô phỏng/contract hiện có; không thay thế các case runtime thất bại. Không thêm test/code hoặc chạy checkpoint02.

Git cuối: chỉ thư mục `chatbot-service/debug/eval60/` mới, chưa theo dõi; git diff các file tracked trống. Không commit/push raw log hay kết quả checkpoint. Dịch vụ vẫn chạy.

**HANDOFF:** checkpoint02 nhận baseline commit 438227c, result `results/phase01.json`, evidence `evidence/phase01/`, cùng danh sách F01–F08 và trạng thái runtime. Cần bộ prompt/expected của checkpoint02 trước khi chạy; chưa giả định scope T15 trở đi. Giữ trace boundary của từng checkpoint, phiên mới nếu không kiểm thử context, kiểm cả card và text. Nên tái kiểm chứng constraint/taxonomy khi có bản sửa trong task riêng, không silently sửa ở checkpoint02.

# HỌC KIẾN TRÚC CHATBOT — PHẦN 1

## 1. Intent, QueryPlan và evidence

**Intent** là loại việc user muốn làm (GREETING/HELP/PRODUCT_DISCOVERY/ORDER_STATUS/KNOWLEDGE_QA…), dùng để chọn nhánh xử lý. **QueryPlan** là object có schema: intent, requested_fields, references, product_search, semantic_query. Nó không phải SQL; model không tự thực thi truy vấn DB. T07 được model chuyển thành Laptop, max_price=20.000.000, min_ram=16; SQL do Java/Python code kiểm soát.

**Structured filter** loại variant không đạt giá/RAM/category/brand/stock. **Semantic ranking** dùng vector để xếp thứ tự các product đã qua filter; similarity cao không cho phép vượt budget. T08 minh chứng: “Dell/20 triệu” trong semantic query không tương đương brands=[Dell]/max_price=20 triệu.

**ProductCard** được Python build từ backend live detail, chọn variant giá thấp nhất còn eligible cho mỗi product. Không để LLM tạo card/UUID/giá. **GroundedContext** là evidence thực cấp cho model sinh văn bản, không phải toàn bộ DB và không dùng lịch sử làm bằng chứng nghiệp vụ.

## 2. T07: đi từng bước bằng trace thật

T07 trace `31df84ee-6d35-49e5-88d0-6ae9549c8bea`, session `18e27386-8c7c-415a-833c-40df1ea41c66`. Prompt “Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB.”. Một candidate/product/card Dell Inspiron 15 15.990.000đ/RAM16, tồn2 tại kho thực. Các excerpt dưới đều lấy từ trace này hoặc quan sát frontend/API liên kết cùng trace, không tạo trace giả cho tầng chưa được instrument.

### Bước 1 — Frontend nhận câu hỏi

**File/hàm thực:** Fontend/src/features/chatbot/ChatbotLabPage.jsx → send(event)

**Input:** Textarea, sessionId mới, user đã authenticated.

**Output:** Lời gọi chatbotApi.send(sessionId, question, abort.signal, requestTraceId).

**Mục đích:** Trim input, tạo UUID trace, quản lý busy/timeout, lưu response và render cards.

**Trace/quan sát thực T07:**
```json
{
  "trace_id": "31df84ee-6d35-49e5-88d0-6ae9549c8bea",
  "session_id": "18e27386-8c7c-415a-833c-40df1ea41c66",
  "prompt": "Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB.",
  "ui_message_matches_api": true,
  "ui_card_names": [
    "Dell Inspiron 15"
  ]
}
```

**Dấu hiệu lỗi:** Không có POST trong network, UI timeout, text/card không giống response.

Không có event frontend riêng: block này là browser observation cùng UUID, không phải log JavaScript giả.

### Bước 2 — chatbotApi / apiClient / Vite

**File/hàm thực:** Fontend/src/features/chatbot/chatbotApi.js → chatbotApi.send; src/api/apiClient.js → request; vite.config.js → proxy /api/v1/chat

**Input:** {message}, sessionId, signal và UUID header.

**Output:** POST /api/v1/chat/sessions/{sessionId}/messages tới FastAPI; cookie/CSRF flow hiện có.

**Mục đích:** Đóng gói JSON/headers, chuyển từ frontend5173 qua proxy8090; không xử lý AI ở browser.

**Trace/quan sát thực T07:**
```json
{
  "method": "POST",
  "path": "/api/v1/chat/sessions/18e27386-8c7c-415a-833c-40df1ea41c66/messages",
  "body": {
    "message": "Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB."
  },
  "trace_id": "31df84ee-6d35-49e5-88d0-6ae9549c8bea",
  "http_status": 200
}
```

**Dấu hiệu lỗi:** 404 proxy/path sai, 401 auth, network lỗi; có request frontend nhưng không có request.accepted.

Không xuất headers bảo mật/cookie; request structure từ browser, không claim event API riêng.

### Bước 3 — Chat Router

**File/hàm thực:** app/features/chat/router.py → get_authenticated_customer, send_message

**Input:** Session UUID, SendMessageRequest, cookie chỉ dùng để xác thực.

**Output:** AuthenticatedCustomer nội bộ và gọi ChatService.send_message_async; tạo trace/header correlation.

**Mục đích:** GET backend auth/me kiểm tra danh tính trước trace; guard mode development, bind trace owner.

**Trace/quan sát thực T07:**
```json
{
  "name": "request.accepted",
  "output": {
    "message": "Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB.",
    "session_id": "18e27386-8c7c-415a-833c-40df1ea41c66",
    "authentication": "verified_by_backend"
  }
}
```

**Dấu hiệu lỗi:** Auth/validation lỗi trước trace có thể không có trace; request422, 401 hoặc traceID không khớp.

Trace xác nhận authentication=verified_by_backend; không chứa dữ liệu đăng nhập.

### Bước 4 — ChatService chuẩn bị context

**File/hàm thực:** app/features/chat/service.py → ChatService.send_message_async

**Input:** Message, session, authenticated customer và cookies chuyển tiếp.

**Output:** QueryContext chứa current_message và recent_messages; sau đó điều phối search/context/RAG.

**Mục đích:** Lưu user message, lấy history không lặp lại câu hiện tại, chạy sync AI/product qua asyncio.to_thread.

**Trace/quan sát thực T07:**
```json
{
  "name": "conversation.context",
  "current_message": "Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB.",
  "history_count": 0
}
```

**Dấu hiệu lỗi:** History count sai/nhân đôi câu hiện tại, session sai owner, plan đúng nhưng effective filters bị đổi sai.



### Bước 5 — Query Understanding và model

**File/hàm thực:** app/features/query_understanding/ollama_service.py → OllamaQueryUnderstandingService.understand; app/shared/ollama/client.py → OllamaClient.chat / _post

**Input:** QueryContext; system prompt + user JSON; QueryPlan JSON schema; real chat model.

**Output:** Raw JSON model → Pydantic validate → QueryPlan. Lỗi schema có 1 repair attempt; nếu model/schema thất bại thì UNKNOWN fallback.

**Mục đích:** Chuyển ngôn ngữ tự nhiên thành plan máy đọc được; không truy cập DB.

**Trace/quan sát thực T07:**
```json
{
  "name": "query.validation",
  "output": {
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
}
```

**Dấu hiệu lỗi:** query.validation_failed, repair/fallback; hoặc schema hợp lệ nhưng thiếu field như T08/T10. Schema pass không xác nhận semantic đúng.



### Bước 6 — QueryPlan và effective plan

**File/hàm thực:** app/shared/contracts/query.py → QueryPlan / ProductSearchPlan.validate_price_range; ChatService.send_message_async → xử lý top_k/follow-up

**Input:** Plan đã validate.

**Output:** Effective category Laptop, max20triệu, RAM16, top_k5; không cheaper followup.

**Mục đích:** Chặn min>max nếu cả hai bounds có mặt; orchestration có thể giảm top_k hoặc max_price khi follow-up.

**Trace/quan sát thực T07:**
```json
{
  "name": "query.plan.effective",
  "output": {
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
}
```

**Dấu hiệu lỗi:** Giá vẫn chỉ trong semantic query hoặc max=null; T14 mất upper bound nên range validator không thể phát hiện conflict từ câu gốc.



### Bước 7 — Product Advisor real

**File/hàm thực:** app/features/product_advisor/real_service.py → RealProductAdvisorService.search / _eligible / _variant

**Input:** ProductSearchPlan, configured warehouse, BackendProductClient, embedder và semantic index.

**Output:** ProductMatch list sau candidate collection, rank và kiểm eligible lần nữa.

**Mục đích:** Structured eligibility trước ranking, lấy live detail và không lấy toàn catalog nếu paging kết thúc sớm.

**Trace/quan sát thực T07:**
```json
{
  "name": "product.search",
  "output": [
    {
      "product_id": "a96df51c-538c-4fa9-8018-6b49ca025b34",
      "semantic_score": 0.4710991244788898,
      "ranking_score": 0.4710991244788898,
      "eligible_variants": [
        {
          "variant_id": "9ede9a3c-6406-4058-9ce0-414d19b7939f",
          "product_id": "a96df51c-538c-4fa9-8018-6b49ca025b34",
          "sku": "DEV-DELL-I15-512",
          "original_price": "15990000.0",
          "effective_price": "15990000.0",
          "ram": "16GB",
          "storage": "512GB",
          "color": "Bạc",
          "available_quantity": 2,
          "warranty_months": 24
        }
      ],
      "reasons": [
        "Danh mục: Laptop",
        "RAM từ 16GB"
      ]
    }
  ]
}
```

**Dấu hiệu lỗi:** Candidates đúng nhưng match sai điều kiện → xem _eligible; không index có thể thiếu score; dùng plan sai từ tầng trên không phải lỗi advisor.



### Bước 8 — BackendProductClient tạo query parameters

**File/hàm thực:** app/features/product_advisor/backend.py → BackendProductClient.search / _get / detail

**Input:** Python plan và warehouse UUID.

**Output:** GET advanced search với category/maxPrice/minRamGb/inStockOnly/limit/offset; tiếp đó GET detail theo productId.

**Mục đích:** Đổi snake_case plan thành camelCase backend API, chuyển auth an toàn.

**Trace/quan sát thực T07:**
```json
{
  "name": "backend.product_http",
  "input": {
    "path": "/api/v1/products/search/advanced",
    "kwargs": {
      "params": [
        [
          "limit",
          "100"
        ],
        [
          "offset",
          "0"
        ],
        [
          "category",
          "Laptop"
        ],
        [
          "maxPrice",
          "20000000"
        ],
        [
          "minRamGb",
          "16"
        ],
        [
          "warehouseId",
          "d13ea247-ab70-473b-80d6-c8adea60334c"
        ],
        [
          "inStockOnly",
          "false"
        ]
      ]
    }
  }
}
```

**Dấu hiệu lỗi:** Plan có max_price nhưng request không maxPrice → adapter; category Smartphone khiến SQL exact category 0 là sai domain upstream.



### Bước 9 — Spring Boot xử lý structured search

**File/hàm thực:** backend/src/main/java/com/example/backend/product/controller/ProductAdvancedSearchController.java → search; service/ProductAdvancedSearchService.java → search

**Input:** Query params từ adapter, xác thực qua Spring Security hiện có.

**Output:** List<ProductSearchCandidate>: productId/variantId; không phải full ProductCard.

**Mục đích:** Validate params rồi tạo SQL parameterized bằng NamedParameterJdbcTemplate; ACTIVE products/variants, price sau promotion, RAM normalized GB/TB, inventory nếu yêu cầu.

**Trace/quan sát thực T07:**
```json
{
  "name": "backend.product_result",
  "output": {
    "path": "/api/v1/products/search/advanced",
    "status_code": 200,
    "body": [
      {
        "productId": "a96df51c-538c-4fa9-8018-6b49ca025b34",
        "variantId": "9ede9a3c-6406-4058-9ce0-414d19b7939f"
      }
    ]
  }
}
```

**Dấu hiệu lỗi:** HTTP400 bộ lọc vô lý; plan/input đúng nhưng candidates sai → inspect Java SQL. Không có ProductAdvancedSearchRepository: service này query JDBC trực tiếp.

Backend Python trace chỉ ghi HTTP request/response; không có Java span/SQL trace riêng. Hàm Java được nối bằng endpoint + source, không giả định có runtime SQL log.

### Bước 10 — PostgreSQL: structured query và vector ranking

**File/hàm thực:** ProductAdvancedSearchService.search → jdbc.query; app/features/product_advisor/semantic.py → OllamaEmbeddingClient.embed / ProductSemanticIndex.scores

**Input:** Structured SQL parameters; semantic query được embed; candidate IDs cho pgvector.

**Output:** Structured candidate1; score vector cho candidate; Java detail đọc variants/pricing/inventory.

**Mục đích:** DB giữ dữ liệu nghiệp vụ; Python pgvector xếp candidate bằng cosine, cùng embedding model/dimension.

**Trace/quan sát thực T07:**
```json
{
  "name": "product.sql",
  "output": {
    "sql": "SELECT product_id, 1 - (embedding_vector <=> %s::vector) AS score\n                   FROM product_semantic_index\n                   WHERE product_id = ANY(%s) AND vector_dims(embedding_vector) = %s\n                     AND embedding_model = %s",
    "parameters": {
      "product_ids": [
        "a96df51c-538c-4fa9-8018-6b49ca025b34"
      ],
      "dimensions": 1024,
      "model": "qwen3-embedding:0.6b",
      "vector": "omitted"
    }
  },
  "ranked_scores": {
    "a96df51c-538c-4fa9-8018-6b49ca025b34": 0.4710991244788898
  }
}
```

**Dấu hiệu lỗi:** DB connection/SQL error, dimension/model mismatch, score thiếu; chưa thể quy lỗi DB chỉ vì model trả category không tồn tại.

product.sql là SQL Python pgvector thật. Structured Spring SQL không được log ở trace này; candidate HTTP response chứng minh kết quả, source xác nhận cách query. Không đưa vector hoặc DSN vào excerpt.

### Bước 11 — Build ProductCards / live detail

**File/hàm thực:** app/features/product_advisor/real_service.py → build_cards; backend ProductDetailController.getDetail → ProductDetailServiceImpl.getProductDetail(productId, warehouseId)

**Input:** ProductMatch IDs + eligible variants; detail lấy lại live.

**Output:** 1 ProductCard mang name Dell Inspiron15, SKU, giá, RAM, stock2.

**Mục đích:** Chọn variant effectivePrice thấp nhất eligible; backend detail dùng repositories + promotions + warehouse availability. Card không do LLM sinh.

**Trace/quan sát thực T07:**
```json
{
  "name": "product.cards",
  "output": [
    {
      "product_id": "a96df51c-538c-4fa9-8018-6b49ca025b34",
      "product_name": "Dell Inspiron 15",
      "variant_id": "9ede9a3c-6406-4058-9ce0-414d19b7939f",
      "sku": "DEV-DELL-I15-512",
      "original_price": "15990000.0",
      "effective_price": "15990000.0",
      "ram": "16GB",
      "storage": "512GB",
      "color": "Bạc",
      "available_quantity": 2,
      "warranty_months": 24,
      "image_url": "/images/products/dell-inspiron-15/main.jpg",
      "reasons": [
        "Danh mục: Laptop",
        "RAM từ 16GB"
      ]
    }
  ]
}
```

**Dấu hiệu lỗi:** Match đúng/card sai variant/price → build_cards/live update; card đúngcatalog nhưng ngoài user filter → xem plan trước đó, như T08.



### Bước 12 — GroundedContext

**File/hàm thực:** app/features/chat/service.py → tạo GroundedContext; shared/contracts/context.py → GroundedContext

**Input:** QueryPlan, user_query, relevant_history, ProductMatch, knowledge_chunks và CustomerContext.

**Output:** Products1, chunks0; không cart/orders cho discovery.

**Mục đích:** Chỉ cung cấp evidence truy xuất; lịch sử dùng tham chiếu, không làm nguồn giá/trạng thái.

**Trace/quan sát thực T07:**
```json
{
  "name": "response.grounded_context",
  "output": {
    "query_plan": {
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
    },
    "products": [
      {
        "product_id": "a96df51c-538c-4fa9-8018-6b49ca025b34",
        "semantic_score": 0.4710991244788898,
        "ranking_score": 0.4710991244788898,
        "eligible_variants": [
          {
            "variant_id": "9ede9a3c-6406-4058-9ce0-414d19b7939f",
            "product_id": "a96df51c-538c-4fa9-8018-6b49ca025b34",
            "sku": "DEV-DELL-I15-512",
            "original_price": "15990000.0",
            "effective_price": "15990000.0",
            "ram": "16GB",
            "storage": "512GB",
            "color": "Bạc",
            "available_quantity": 2,
            "warranty_months": 24
          }
        ],
        "reasons": [
          "Danh mục: Laptop",
          "RAM từ 16GB"
        ]
      }
    ],
    "knowledge_chunk_count": 0
  }
}
```

**Dấu hiệu lỗi:** Evidence thiếu hoặc dư; backend có brand/name nhưng ProductMatch không mang vào context → missing identity, không phải model tự biết backend.



### Bước 13 — Response Generation

**File/hàm thực:** app/features/response_generation/ollama_service.py → OllamaResponseGenerationService.generate; shared/ollama/client.py → chat

**Input:** GroundedContext; guard deterministic trước, nếu có evidence tạo system/user prompt cho model.

**Output:** Văn bản tiếng Việt dựa evidence; T07 dùng ollama, không phải guard.

**Mục đích:** LLM diễn đạt, không tạo UUID/cards/source hay giao dịch. Greeting/no_products/refusal là nhánh code.

**Trace/quan sát thực T07:**
```json
{
  "name": "response.strategy",
  "strategy": "ollama",
  "response_generated": "Có 1 sản phẩm laptop đáp ứng yêu cầu:  \n- Thương hiệu: Không xác định  \n- Giá: 15.990.000 VNĐ (dưới 20 triệu)  \n- RAM: 16GB  \n- Bộ nhớ lưu trữ: 512GB  \n- Màu sắc: Bạc  \n- Số lượng có sẵn: 2  \n- Thời gian bảo hành: 24 tháng  \n\nSản phẩm có mã SKU: DEV-DELL-I15-512."
}
```

**Dấu hiệu lỗi:** Evidence đúng nhưng text sai → generation; text có brand Không xác định ở T07 do evidence không có brand. Không đổ mọi lỗi cuối cho model khi plan/evidence đã sai.



### Bước 14 — ChatResponse trở về frontend

**File/hàm thực:** app/features/chat/service.py → ChatResponse; shared/contracts/response.py → ChatResponse / ProductCard; ChatbotLabPage.jsx → setMessages / render

**Input:** Message từ generator + cards từ advisor + source refs từ chunks.

**Output:** JSON trả session_id/message/products/sources/suggested_questions; UI render text và card tách biệt.

**Mục đích:** Kết hợp text và dữ liệu có cấu trúc; validate cả hai để phát hiện text nói không có nhưng card vẫn đắt như T10/T14.

**Trace/quan sát thực T07:**
```json
{
  "name": "chat.pipeline",
  "output": {
    "session_id": "18e27386-8c7c-415a-833c-40df1ea41c66",
    "message": "Có 1 sản phẩm laptop đáp ứng yêu cầu:  \n- Thương hiệu: Không xác định  \n- Giá: 15.990.000 VNĐ (dưới 20 triệu)  \n- RAM: 16GB  \n- Bộ nhớ lưu trữ: 512GB  \n- Màu sắc: Bạc  \n- Số lượng có sẵn: 2  \n- Thời gian bảo hành: 24 tháng  \n\nSản phẩm có mã SKU: DEV-DELL-I15-512.",
    "products": [
      {
        "product_id": "a96df51c-538c-4fa9-8018-6b49ca025b34",
        "product_name": "Dell Inspiron 15",
        "variant_id": "9ede9a3c-6406-4058-9ce0-414d19b7939f",
        "sku": "DEV-DELL-I15-512",
        "original_price": "15990000.0",
        "effective_price": "15990000.0",
        "ram": "16GB",
        "storage": "512GB",
        "color": "Bạc",
        "available_quantity": 2,
        "warranty_months": 24,
        "image_url": "/images/products/dell-inspiron-15/main.jpg",
        "reasons": [
          "Danh mục: Laptop",
          "RAM từ 16GB"
        ]
      }
    ],
    "sources": [],
    "suggested_questions": [
      "Bạn muốn tìm sản phẩm nào?"
    ]
  },
  "ui_matches_api": true
}
```

**Dấu hiệu lỗi:** API200 nhưng card vượt budget hoặc thiếuname → FAIL tùyexpectation; HTTPstatus/tracestatus chỉ phát hiện lỗi kỹ thuật.




## 3. Năm câu hỏi ôn tập và đáp án

1. **Intent khác bộ lọc thế nào?** Intent chọn loại workflow; product_search là điều kiện chọn dữ liệu trong workflow. PRODUCT_DISCOVERY không tự đảm bảo giá/RAM đúng.
2. **Tại sao T08 semantic query có Dell/20 triệu nhưng card vẫn sai?** Backend chỉ nhận các field hard-filter. brands=[] và max_price=null làm SQL không enforce brand/budget; similarity chỉ xếp hạng, không có nghĩa hợp lệ.
3. **LLM có tự query DB và tạo ProductCard không?** Không. LLM tạo QueryPlan/văn bản; Python/Java code gọi backend/SQL; card được build từ live detail.
4. **T14 có schema pass thì vì sao vẫn sai?** Model bỏ upper bound, nên validator chỉ thấy min25triệu/maxnull, hợp schema. Câu gốc mâu thuẫn không còn được biểu diễn; response nhận ra muộn không thu hồi cards.
5. **Cần xem gì đầu tiên khi cuối cùng trả sai?** So câu user với QueryPlan, effective params, candidates và GroundedContext theo thứ tự. Nếu brand/budget mất ở plan thì first fault ở Query Understanding; chỉ quy cho generation khi evidence phù hợp nhưng text không được hỗ trợ.
