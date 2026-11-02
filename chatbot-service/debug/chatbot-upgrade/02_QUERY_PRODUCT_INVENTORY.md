# QUERY UNDERSTANDING V2 — PRODUCT IDENTITY & INVENTORY

Task `c2c_chatbot_upgrade_02`, iteration 0. Kết quả bản cuối: **24 runtime requests đã chạy: 23 PASS, 1 PASS_WITH_WARNINGS, 0 FAIL**. Trong đó 21 prompts T07–T22/T51–T55 và ba case đọc thêm R01–R03. Đây là targeted regression, không phải đánh giá lại toàn bộ 60 case hay chứng nhận production.

## 1. Approved design và phạm vi

User đã phê duyệt bằng “duyệt” sau khi được trình bày blueprint mục8 của [audit](01_SCHEMA_CONTRACT_AUDIT.md). Giữ một ProductCard đại diện/product; text mô tả từng eligible variant. Canonical taxonomy dùng catalog thực; category trực tiếp, không thêm descendant search. SKU equality theo DB; fuzzy/typo chưa chắc chắn phải hỏi rõ. Multi-intent, durable conversation, feedback và schema changes chưa triển khai.

Workspace `/Users/vu/Documents/Vu/HUIT-Year4/KLTN/KLCN-QL_CHTBDT-Ecommerce-main`; branch `dev/thai-duy-vu`, HEAD `d235df5cc4411d12accae8489930a31d281abbf8`. Working tree đầu checkpoint chỉ có tài liệu audit chưa tracked. Không merge/fetch/checkout, không sửa migration/seed/config/auth/business mutation flow; không commit/push. Backend [AGENTS.md](../../../backend/AGENTS.md) schema LOCKED được giữ nguyên.

## 2. Files changed

| File | Thay đổi |
|---|---|
| app/shared/contracts/query.py | ProductSelector với đúng một identity; exact RAM/storage/color; targets optional ở ProductSearchPlan; clarification optional ở QueryPlan |
| app/shared/contracts/product.py | ProductMatch thêm tên, brand/category và specifications optional để grounding có identity |
| app/features/query_understanding/normalization.py (mới) | DomainNormalizer: live taxonomy/catalog, lexical slots/units/conflicts, exact targets, conservative clarification |
| app/features/query_understanding/ollama_service.py | Prompt ngắn sửa phân loại detail/warranty/compare; normalize sau schema validation và cả fallback |
| app/features/product_advisor/backend.py | GET taxonomy, bounded catalog names, bounded variant keyword candidates, variant UUID; reuse APIs hiện có |
| app/features/product_advisor/real_service.py | Exact identity resolution, exact variant filters, stock evidence, domain resolution errors, discovery enrichment |
| app/features/chat/dependencies.py | Inject DomainNormalizer ở AI + RealProductAdvisor |
| app/features/chat/service.py | Scope backend cookie context cho QU thread; resolution failure→clarification; backend/coverage failure→503 |
| app/features/response_generation/ollama_service.py | Clarification guard; deterministic product text từ eligible variants đã xác minh, giữ cents khi giá có phần lẻ |
| tests/test_query_product_v2.py (mới) | 44 regressions bằng isolated fixtures/HTTP fake, không đổi dữ liệu business |

Paths trong bảng tính từ `chatbot-service/`. **Không có file Java/backend/migration/frontend được sửa**: API đọc hiện có đủ phục vụ resolver trong phạm vi bounded catalog. Java tests được chạy để xác minh compatibility của backend hiện tại.

## 3. QueryPlan before/after

Trước: model tạo plan hợp JSON nhưng có category Smartphone, mất brands/max_price, SKU chỉ trong semantic text, named product dùng discovery/top5 và comparison names thành CPU.

Sau: model vẫn phân loại ban đầu bằng Qwen/schema; DomainNormalizer xác minh và dựng execution plan từ current_message và catalog thật. Filters không được giữ chỉ vì model tự thêm chúng. Exact identity có typed target; Stock vẫn ở requested_fields, detail không dùng stock-only để loại zero evidence. Một JSON schema hợp lệ chưa đồng nghĩa đủ domain validation.

```text
Current message + recent messages
  → Qwen structured JSON → Pydantic QueryPlan
  → DomainNormalizer (live taxonomy + literal slots + conflict/ambiguity checks)
  → normalized QueryPlan / clarification
  → exact resolver hoặc structured discovery
  → live ProductMatch/EligibleVariant + card đại diện
  → GroundedContext → deterministic product evidence text / clarification
```

Examples thực T15/T17/T22 nằm ở mục8 với QueryPlan trước và sau. Clarification từ model bị loại trước domain validation; chỉ application được đặt clarification, tránh bypass grounding bằng một field văn bản tự do. Nếu model offline/sai schema, explicit product request còn có thể được xác minh bằng deterministic catalog path; trace vẫn ghi model failure/fallback, không giả rằng Qwen parse thành công.

## 4. Canonical mapping

- Smartphone/Phone/điện thoại/iPhone family → `Điện thoại` khi category ấy có thật; iPhone family gắn brand Apple, không tạo category iPhone.
- Laptop → `Laptop`; brand dựa tên canonical trong API brands, không tên tự đặt từ model.
- Product name đọc từ paginated backend catalog; exact resolver kiểm tên bằng equality và xử lý duplicate names bằng AMBIGUOUS, không tự lấy top semantic score.
- Giá: VND, triệu/tr/million, nghìn/k; decimal comma và dấu phân cách hàng nghìn được normalize. Giá tối thiểu khác RAM tối thiểu. Range min>max hỏi lại.
- RAM/storage GB/TB (TB×1024); minimum filters khác exact selector. Unsupported upper/range capacity, MB/KB hoặc zero yêu cầu clarification.
- CPU keywords chỉ trích các pattern CPU được hỗ trợ; brand/model không thành cpu_keywords.
- Màu là selector khi có identity, gồm màu catalog development/alias phổ biến. Phải giữ dấu khi cần: `Tìm` không được hiểu thành màu `Tím`.

Đây là grammar có phạm vi, không phải semantic validator hoàn hảo cho mọi ngôn ngữ tự nhiên; limitations tại mục10. Không đổi tên category hoặc business data để làm test pass.

## 5. Product identity resolution

ProductSelector nhận đúng một trong product_id, variant_id, product_name hoặc sku; ram_gb/storage_gb/color là optional exact constraints.

- product_id → detail UUID hiện có.
- variant_id → existing variant UUID endpoint rồi detail product và chỉ giữ variant ấy.
- product_name → bounded catalog candidates rồi equality. Không cần variant tồn tại mới xác định product existence.
- sku → paginated keyword candidate API, sau đó equality theo SKU. Keyword LIKE chỉ tạo ứng viên, không phải xác nhận identity.
- variant configs → capacity equality/color normalization trên variants detail; không chọn variant rẻ nhất nếu SKU/config chỉ định một variant khác.
- comparison → targets riêng biệt, resolve từng target; không đưa names vào CPU. Nếu target không resolve đủ, hỏi rõ và không gửi card nửa bộ như thể comparison hoàn thành.

Discovery vẫn dùng advanced SQL hard filters trước semantic ranking. Exact path không chạy semantic ranking và không dùng top_k=1 như bằng chứng identity. Card chỉ lấy variant đại diện thuộc eligible set; text nêu đầy đủ eligible variants. R01 chứng minh SKU512 vẫn chọn card512 dù BLK256 rẻ hơn.

## 6. Inventory result semantics

| Tình huống | Xử lý bản mới | Evidence/test |
|---|---|---|
| Product/SKU/UUID không tồn tại | Internal NOT_FOUND + clarification, cards rỗng | R03; isolated missing SKU/pipeline |
| Product có thật, không variant ACTIVE phù hợp cấu hình | VARIANT_NOT_FOUND; không gọi product nonexistent hoặc sold out | isolated empty variants/config mismatch |
| Tất cả eligible variants có available=0 | Giữ variants/cards; text hết hàng **tại kho được chọn**; KNOWN_ZERO trace | isolated sold-out fixture |
| Một variant0, một variant>0 | Text riêng mỗi SKU; không biến tồn của một variant thành tồn cả product | isolated mixed stock |
| Stock ở kho đã cấu hình | Detail warehouseId, available_quantity từ backend | T15/T17/R01/R02 |
| Không chọn warehouse cho detail | quantity=null; UNKNOWN_WAREHOUSE; text chưa xác định tồn | isolated test |
| Không có warehouse cho discovery stock-only | Reject execution; 503 config/data unavailable; không nói hết hàng | existing warehouse-required regression |
| Backend timeout/connect/HTTP failure | 503, không no-match/sold-out | pipeline outage regression |
| Missing inventory row tại kho | Backend detail hiện quy available=0; không xác minh physical count độc lập | Giữ policy API hiện tại; limitation rõ |

available_quantity = quantity−reserved_quantity do Java đọc theo warehouse; Python không suy từ embedding. Internal result names trong trace/domain exceptions không phải status mới của business tables. Những case stock0/missing warehouse/ambiguous dùng isolated fixtures, **không giả là đã tạo hoặc quan sát live stock0**. Business snapshots before/after kiểm inventory/orders/payments/variants, excludes auth token tables; hashes giống nhau.

## 7. Test matrix

Runtime thật: frontend proxy localhost5173 → chatbot8090 → Spring8080; PostgreSQL Docker và Ollama hiện có. AI real, qwen3:4b-instruct, embeddings qwen3-embedding:0.6b; warehouse đã cấu hình như baseline. Chỉ account CUSTOMER development `seed.customer1`, không lưu password/cookie/token. 24 requests dùng sessions mới, capture trace ID riêng, kiểm ChatResponse qua API proxy và đối chiếu backend.

Không chạy lại browser rendering trong checkpoint này; API response tương thích fields frontend hiện tại. Không thay runtime configuration, không restart các project; uvicorn8090 tự reload code. Server8091 tạm dùng để kiểm startup đã dừng, dịch vụ gốc vẫn chạy.

| Case | Prompt | Intent sau normalization | Cards | Verdict | Trace ID |
|---|---|---|---:|---|---|
| T07 | Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB. | PRODUCT_DISCOVERY | 1 | PASS | `237adaa5-c5d2-4101-8c7f-09e08bc75c82` |
| T08 | Tìm laptop Dell có RAM 16GB, giá không quá 20 triệu. | PRODUCT_DISCOVERY | 1 | PASS | `17ed0b25-936e-4c9a-8e5e-22411b8039cb` |
| T09 | Tìm iPhone giá dưới 1 triệu. | PRODUCT_DISCOVERY | 0 | PASS | `1842f627-d965-470a-8424-976761e13aa5` |
| T10 | Tìm laptop RAM ít nhất 32GB, giá dưới 20 triệu. | PRODUCT_DISCOVERY | 0 | PASS | `18e88583-c6b9-483c-b733-8645c5b1de4c` |
| T11 | Tìm điện thoại Xiaomi dưới 20 triệu. | PRODUCT_DISCOVERY | 3 | PASS | `40f40ec3-24d4-491e-a3f2-d6d3faf71e93` |
| T12 | Tìm điện thoại RAM tối thiểu 12GB. | PRODUCT_DISCOVERY | 4 | PASS | `07c0ccb5-7390-448e-b9ce-403882d17bd4` |
| T13 | Tìm laptop RAM 16GB đang còn hàng. | PRODUCT_DISCOVERY | 4 | PASS | `9bec98f2-70a8-4747-8a05-2e9fbb2b7bb8` |
| T14 | Tìm laptop giá từ 25 triệu đến 20 triệu. | UNKNOWN | 0 | PASS | `716a58ba-a495-462e-9535-30853dc69fc3` |
| T15 | Xiaomi 15 còn hàng không? | PRODUCT_DETAIL | 1 | PASS | `9bbbab93-76e9-450d-9d11-ab60ac8dda80` |
| T16 | Xiaomi 15 hiện có giá bao nhiêu? | PRODUCT_DETAIL | 1 | PASS | `76428f2b-dbe6-416a-9a9a-2b7774ff054c` |
| T17 | Xiaomi 15 bản RAM 16GB, dung lượng 512GB còn hàng không? | PRODUCT_DETAIL | 1 | PASS | `38929472-3db7-4b53-922f-4b2b0b350e78` |
| T18 | Xiaomi 15 có những màu nào? | PRODUCT_DETAIL | 1 | PASS | `54cddb35-8617-4679-ab93-075045cd4f42` |
| T19 | iPhone 15 còn hàng không? | PRODUCT_DETAIL | 1 | PASS | `82fffe57-283d-430a-b758-2f97b2d158d1` |
| T20 | Samsung Galaxy A55 được bảo hành bao nhiêu tháng? | PRODUCT_DETAIL | 1 | PASS | `53fbf96f-cf8b-4cf2-8dd1-ec500b7639e8` |
| T21 | SKU DEV-X15-BLK-256 hiện giá bao nhiêu và còn bao nhiêu máy? | PRODUCT_DETAIL | 1 | PASS | `eea2b666-17a6-4bf3-b41d-1f8b33b30870` |
| T22 | So sánh Dell Inspiron 15 và Dell XPS 13 về giá và RAM. | PRODUCT_COMPARE | 2 | PASS | `a4c942ec-bf7d-470c-a771-ce6feab3b7f3` |
| T51 | Xiaomii 15 còn hàng hong? | UNKNOWN | 0 | PASS_WITH_WARNINGS | `a640d002-4cbc-4b23-a625-56b36ad47ed6` |
| T52 | Is Xiaomi 15 in stock? | PRODUCT_DETAIL | 1 | PASS | `6392662b-c932-4e64-bc4d-98b7c9c1ac6d` |
| T53 | Smartphone Xiaomi nào còn hàng? | PRODUCT_DISCOVERY | 3 | PASS | `0e6bbcfc-15ad-4f9e-a402-5b14d10c1589` |
| T54 | Tôi muốn máy vừa rẻ vừa mạnh, tư vấn giúp. | UNKNOWN | 0 | PASS | `8425f063-9d40-4215-a5cf-126d5b0ca78f` |
| T55 | Tìm laptop Dell dưới 20tr, ram >=16, ssd >=512. | PRODUCT_DISCOVERY | 1 | PASS | `e3a61e6f-12f7-4d76-a7ec-c740390c7be1` |
| R01 | SKU DEV-X15-GRN-512 hiện giá bao nhiêu và còn bao nhiêu máy? | PRODUCT_DETAIL | 1 | PASS | `e29f11ce-a763-4f1b-a9a1-cc8d51525ff9` |
| R02 | Xiaomi 15 bản màu Trắng còn hàng không? | PRODUCT_DETAIL | 1 | PASS | `78ef4dd9-5d46-4bf3-8a6d-0c9a592f3c03` |
| R03 | SKU DEV-UPGRADE-NOTFOUND hiện còn hàng không? | PRODUCT_DETAIL | 0 | PASS | `fb55686e-ccc9-4020-98a7-532f4c808512` |

### Unit/integration results

- Python full: `.venv/bin/python -m pytest tests -q` → **199 passed, 1 skipped**, 1 Starlette/httpx deprecation warning. Optional Knowledge PostgreSQL integration skip do chưa cấp KNOWLEDGE_TEST_DATABASE_URL; không tính PASS cho test ấy.
- Python focused: tracing + query_product_v2 + real_product_advisor + ai_services → **80 passed**; file mới riêng gồm **44 tests**.
- Java unit ProductServiceImplTest + ProductControllerTest → **38 passed**.
- Java integration ProductPersistenceIntegrationTest + GoodsReceiptInventoryIntegrationTest → **28 passed, 0 skipped**, PostgreSQL Testcontainers riêng. Lần đầu21 tests integration bị skip do Docker environment không được nhận; đã chạy lại thành công, không báo skip đó là pass.

Java dùng JDK21 đã có tại `.local/tools/…/Contents/Home`. Lệnh integration chạy lại:

```sh
# Từ thư mục backend, với JAVA_HOME/PATH trỏ JDK21 của workspace
DOCKER_HOST=unix:///Users/vu/.docker/run/docker.sock ./mvnw -q -Dapi.version=1.44 \
  -Dtest=ProductPersistenceIntegrationTest,GoodsReceiptInventoryIntegrationTest test
```

Không đổi pom/config hoặc migration để làm tests pass. Các migrations chỉ được test infrastructure áp vào container test riêng. Kết quả máy đọc tại [verification-summary.json](checkpoint02/verification-summary.json).

## 8. Expected vs Actual

QueryPlan “before” lấy từ eval60 commit438227c; “after” là query.plan.initial sau DomainNormalizer của runtime mới. R03 có initial target hợp lệ, resolver tiếp theo NOT_FOUND rồi đặt clarification trong final GroundedContext; không nhầm initial plan với final execution outcome. Không regrade file eval60 cũ.

### T07 — PASS

**Prompt:** Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB.

**Expected:** Laptop, max20 triệu, RAM>=16; card/text đạt filters.

**QueryPlan baseline:**

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

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [
    "PRICE",
    "SPECIFICATIONS"
  ],
  "references": {},
  "product_search": {
    "targets": [],
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": "20000000",
    "min_ram_gb": 16,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB.",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Actual response:**

Dell Inspiron 15
- DEV-DELL-I15-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 15,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T07](checkpoint02/T07.jsonl), trace ID `237adaa5-c5d2-4101-8c7f-09e08bc75c82`; latency 7112.3ms.


### T08 — PASS

**Prompt:** Tìm laptop Dell có RAM 16GB, giá không quá 20 triệu.

**Expected:** Giữ Dell + Laptop + RAM16 + max20 triệu; không tự thêm storage.

**QueryPlan baseline:**

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

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [
    "PRICE",
    "SPECIFICATIONS"
  ],
  "references": {},
  "product_search": {
    "targets": [],
    "category": "Laptop",
    "brands": [
      "Dell"
    ],
    "min_price": null,
    "max_price": "20000000",
    "min_ram_gb": 16,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Tìm laptop Dell có RAM 16GB, giá không quá 20 triệu.",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Actual response:**

Dell Inspiron 15
- DEV-DELL-I15-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 15,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T08](checkpoint02/T08.jsonl), trace ID `17ed0b25-936e-4c9a-8e5e-22411b8039cb`; latency 1911.28ms.


### T09 — PASS

**Prompt:** Tìm iPhone giá dưới 1 triệu.

**Expected:** Điện thoại/Apple + max1 triệu; không tìm thấy là hợp lý, không category iPhone.

**QueryPlan baseline:**

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

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [
    "PRICE"
  ],
  "references": {},
  "product_search": {
    "targets": [],
    "category": "Điện thoại",
    "brands": [
      "Apple"
    ],
    "min_price": null,
    "max_price": "1000000",
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Tìm iPhone giá dưới 1 triệu.",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Actual response:**

Mình chưa tìm thấy sản phẩm phù hợp với các điều kiện hiện tại.

**Cards / evidence:** 0 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T09](checkpoint02/T09.jsonl), trace ID `1842f627-d965-470a-8424-976761e13aa5`; latency 1329.47ms.


### T10 — PASS

**Prompt:** Tìm laptop RAM ít nhất 32GB, giá dưới 20 triệu.

**Expected:** Laptop RAM>=32 dưới20 triệu; dữ liệu hiện tại không có candidate phù hợp.

**QueryPlan baseline:**

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

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [
    "PRICE",
    "SPECIFICATIONS"
  ],
  "references": {},
  "product_search": {
    "targets": [],
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": "20000000",
    "min_ram_gb": 32,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Tìm laptop RAM ít nhất 32GB, giá dưới 20 triệu.",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Actual response:**

Mình chưa tìm thấy sản phẩm phù hợp với các điều kiện hiện tại.

**Cards / evidence:** 0 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T10](checkpoint02/T10.jsonl), trace ID `18e88583-c6b9-483c-b733-8645c5b1de4c`; latency 1777.91ms.


### T11 — PASS

**Prompt:** Tìm điện thoại Xiaomi dưới 20 triệu.

**Expected:** Điện thoại/Xiaomi + max20 triệu, không Smartphone.

**QueryPlan baseline:**

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

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "targets": [],
    "category": "Điện thoại",
    "brands": [
      "Xiaomi"
    ],
    "min_price": null,
    "max_price": "20000000",
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Tìm điện thoại Xiaomi dưới 20 triệu.",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Actual response:**

Xiaomi 15
- DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
Xiaomi 14T Pro
- DEV-X14TP-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 16,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X14TP-BLU-512: RAM 12GB, bộ nhớ 512GB, màu Xanh; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X14TP-GRY-512: RAM 16GB, bộ nhớ 512GB, màu Xám; giá 19,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
Xiaomi Redmi Note 13
- DEV-RN13-BLK-128: RAM 6GB, bộ nhớ 128GB, màu Đen; giá 4,490,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 3 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T11](checkpoint02/T11.jsonl), trace ID `40f40ec3-24d4-491e-a3f2-d6d3faf71e93`; latency 1694.17ms.


### T12 — PASS

**Prompt:** Tìm điện thoại RAM tối thiểu 12GB.

**Expected:** Điện thoại RAM>=12; không có min_price hoặc laptop ngoài scope.

**QueryPlan baseline:**

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

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [
    "SPECIFICATIONS"
  ],
  "references": {},
  "product_search": {
    "targets": [],
    "category": "Điện thoại",
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": 12,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Tìm điện thoại RAM tối thiểu 12GB.",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Actual response:**

Xiaomi 15
- DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
Samsung Galaxy A55
- DEV-A55-ICE-256: RAM 12GB, bộ nhớ 256GB, màu Xanh băng; giá 10,490,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
Xiaomi 14T Pro
- DEV-X14TP-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 16,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X14TP-BLU-512: RAM 12GB, bộ nhớ 512GB, màu Xanh; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X14TP-GRY-512: RAM 16GB, bộ nhớ 512GB, màu Xám; giá 19,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
Samsung Galaxy Z Flip6
- DEV-ZF6-GRY-256: RAM 12GB, bộ nhớ 256GB, màu Xám; giá 24,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-ZF6-YLW-256: RAM 12GB, bộ nhớ 256GB, màu Vàng; giá 24,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-ZF6-BLU-512: RAM 12GB, bộ nhớ 512GB, màu Xanh; giá 28,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 4 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T12](checkpoint02/T12.jsonl), trace ID `07c0ccb5-7390-448e-b9ce-403882d17bd4`; latency 1394.96ms.


### T13 — PASS

**Prompt:** Tìm laptop RAM 16GB đang còn hàng.

**Expected:** Laptop RAM>=16 + in_stock_only=true; không storage tự sinh.

**QueryPlan baseline:**

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

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [
    "STOCK",
    "SPECIFICATIONS"
  ],
  "references": {},
  "product_search": {
    "targets": [],
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": 16,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": true,
    "semantic_query": "Tìm laptop RAM 16GB đang còn hàng.",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Actual response:**

Dell Inspiron 15
- DEV-DELL-I15-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 15,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.
ASUS ROG Zephyrus G14
- DEV-ROGG14-GR-512: RAM 16GB, bộ nhớ 512GB, màu Xám; giá 39,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.
- DEV-ROGG14-WH-1TB: RAM 32GB, bộ nhớ 1TB, màu Trắng; giá 49,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.
- DEV-ROGG14-GR-1TB: RAM 32GB, bộ nhớ 1TB, màu Xám; giá 52,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.
Dell XPS 13
- DEV-XPS13-SL-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 32,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.
- DEV-XPS13-GR-1TB: RAM 16GB, bộ nhớ 1TB, màu Xám; giá 36,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.
- DEV-XPS13-SL-1TB: RAM 32GB, bộ nhớ 1TB, màu Bạc; giá 41,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.
MacBook Pro 14 M3
- DEV-MBP14-M3-SL-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 44,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-MBP14-M3-SG-1TB: RAM 16GB, bộ nhớ 1TB, màu Xám không gian; giá 49,990,000 VND; còn 1 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 4 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T13](checkpoint02/T13.jsonl), trace ID `9bec98f2-70a8-4747-8a05-2e9fbb2b7bb8`; latency 1907.35ms.


### T14 — PASS

**Prompt:** Tìm laptop giá từ 25 triệu đến 20 triệu.

**Expected:** Giữ phát hiện mâu thuẫn giá25>20; clarification, không retrieval/cards.

**QueryPlan baseline:**

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

**QueryPlan mới:**

```json
{
  "clarification": "Khoảng giá bị mâu thuẫn. Bạn muốn giá tối thiểu và tối đa bao nhiêu?",
  "intent": "UNKNOWN",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```

**Actual response:**

Khoảng giá bị mâu thuẫn. Bạn muốn giá tối thiểu và tối đa bao nhiêu?

**Cards / evidence:** 0 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T14](checkpoint02/T14.jsonl), trace ID `716a58ba-a495-462e-9535-30853dc69fc3`; latency 2113.93ms.


### T15 — PASS

**Prompt:** Xiaomi 15 còn hàng không?

**Expected:** Exact Xiaomi15, ba variants và tồn kho từ backend; không sản phẩm khác.

**QueryPlan baseline:**

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

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DETAIL",
  "requested_fields": [
    "STOCK"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": "Xiaomi 15",
        "sku": null,
        "ram_gb": null,
        "storage_gb": null,
        "color": null
      }
    ],
    "category": null,
    "brands": [
      "Xiaomi"
    ],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Xiaomi 15 còn hàng không?",
    "top_k": 1
  },
  "semantic_query": null
}
```

**Actual response:**

Xiaomi 15
- DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T15](checkpoint02/T15.jsonl), trace ID `9bbbab93-76e9-450d-9d11-ab60ac8dda80`; latency 1544.04ms.


### T16 — PASS

**Prompt:** Xiaomi 15 hiện có giá bao nhiêu?

**Expected:** Giá riêng ba variants Xiaomi15, card đại diện rõ một variant.

**QueryPlan baseline:**

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
    "semantic_query": "Xiaomi 15",
    "top_k": 5
  },
  "semantic_query": null
}
```

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DETAIL",
  "requested_fields": [
    "PRICE"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": "Xiaomi 15",
        "sku": null,
        "ram_gb": null,
        "storage_gb": null,
        "color": null
      }
    ],
    "category": null,
    "brands": [
      "Xiaomi"
    ],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Xiaomi 15 hiện có giá bao nhiêu?",
    "top_k": 1
  },
  "semantic_query": null
}
```

**Actual response:**

Xiaomi 15
- DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T16](checkpoint02/T16.jsonl), trace ID `76428f2b-dbe6-416a-9a9a-2b7774ff054c`; latency 1068.74ms.


### T17 — PASS

**Prompt:** Xiaomi 15 bản RAM 16GB, dung lượng 512GB còn hàng không?

**Expected:** Exact variant RAM16/512GB; không min semantics thay cho exact.

**QueryPlan baseline:**

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
    "semantic_query": "Xiaomi 15 bản RAM 16GB, dung lượng 512GB",
    "top_k": 5
  },
  "semantic_query": null
}
```

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DETAIL",
  "requested_fields": [
    "STOCK",
    "SPECIFICATIONS"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": "Xiaomi 15",
        "sku": null,
        "ram_gb": 16,
        "storage_gb": 512,
        "color": null
      }
    ],
    "category": null,
    "brands": [
      "Xiaomi"
    ],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Xiaomi 15 bản RAM 16GB, dung lượng 512GB còn hàng không?",
    "top_k": 1
  },
  "semantic_query": null
}
```

**Actual response:**

Xiaomi 15
- DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T17](checkpoint02/T17.jsonl), trace ID `38929472-3db7-4b53-922f-4b2b0b350e78`; latency 2362.69ms.


### T18 — PASS

**Prompt:** Xiaomi 15 có những màu nào?

**Expected:** Màu các variants thật: Đen, Trắng, Xanh lá.

**QueryPlan baseline:**

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
    "semantic_query": "Xiaomi 15 có những màu nào",
    "top_k": 5
  },
  "semantic_query": null
}
```

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DETAIL",
  "requested_fields": [
    "SPECIFICATIONS"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": "Xiaomi 15",
        "sku": null,
        "ram_gb": null,
        "storage_gb": null,
        "color": null
      }
    ],
    "category": null,
    "brands": [
      "Xiaomi"
    ],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Xiaomi 15 có những màu nào?",
    "top_k": 1
  },
  "semantic_query": null
}
```

**Actual response:**

Xiaomi 15
- DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T18](checkpoint02/T18.jsonl), trace ID `54cddb35-8617-4679-ab93-075045cd4f42`; latency 1486.31ms.


### T19 — PASS

**Prompt:** iPhone 15 còn hàng không?

**Expected:** Exact iPhone15, hai variants, không điện thoại khác.

**QueryPlan baseline:**

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
    "semantic_query": "iPhone 15 còn hàng không",
    "top_k": 5
  },
  "semantic_query": null
}
```

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DETAIL",
  "requested_fields": [
    "STOCK"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": "iPhone 15",
        "sku": null,
        "ram_gb": null,
        "storage_gb": null,
        "color": null
      }
    ],
    "category": "Điện thoại",
    "brands": [
      "Apple"
    ],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "iPhone 15 còn hàng không?",
    "top_k": 1
  },
  "semantic_query": null
}
```

**Actual response:**

iPhone 15
- DEV-IP15-BLK-128: RAM 6GB, bộ nhớ 128GB, màu Đen; giá 17,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-IP15-BLU-256: RAM 6GB, bộ nhớ 256GB, màu Xanh dương; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T19](checkpoint02/T19.jsonl), trace ID `82fffe57-283d-430a-b758-2f97b2d158d1`; latency 1027.99ms.


### T20 — PASS

**Prompt:** Samsung Galaxy A55 được bảo hành bao nhiêu tháng?

**Expected:** Catalog warranty A55 =12 tháng, không personal warranty route.

**QueryPlan baseline:**

```json
{
  "intent": "WARRANTY_STATUS",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": "Samsung Galaxy A55 được bảo hành bao nhiêu tháng?"
}
```

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DETAIL",
  "requested_fields": [
    "WARRANTY"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": "Samsung Galaxy A55",
        "sku": null,
        "ram_gb": null,
        "storage_gb": null,
        "color": null
      }
    ],
    "category": null,
    "brands": [
      "Samsung"
    ],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Samsung Galaxy A55 được bảo hành bao nhiêu tháng?",
    "top_k": 1
  },
  "semantic_query": null
}
```

**Actual response:**

Samsung Galaxy A55
- DEV-A55-NVY-128: RAM 8GB, bộ nhớ 128GB, màu Xanh đen; giá 8,490,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-A55-LIL-256: RAM 8GB, bộ nhớ 256GB, màu Tím; giá 9,490,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-A55-ICE-256: RAM 12GB, bộ nhớ 256GB, màu Xanh băng; giá 10,490,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T20](checkpoint02/T20.jsonl), trace ID `53fbf96f-cf8b-4cf2-8dd1-ec500b7639e8`; latency 1319.92ms.


### T21 — PASS

**Prompt:** SKU DEV-X15-BLK-256 hiện giá bao nhiêu và còn bao nhiêu máy?

**Expected:** Exact SKU BLK256; chỉ đúng variant/product.

**QueryPlan baseline:**

```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [
    "PRICE",
    "STOCK"
  ],
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
    "semantic_query": "SKU DEV-X15-BLK-256",
    "top_k": 5
  },
  "semantic_query": null
}
```

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DETAIL",
  "requested_fields": [
    "PRICE",
    "STOCK"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": null,
        "sku": "DEV-X15-BLK-256",
        "ram_gb": null,
        "storage_gb": null,
        "color": null
      }
    ],
    "category": null,
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "SKU DEV-X15-BLK-256 hiện giá bao nhiêu và còn bao nhiêu máy?",
    "top_k": 1
  },
  "semantic_query": null
}
```

**Actual response:**

Xiaomi 15
- DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T21](checkpoint02/T21.jsonl), trace ID `eea2b666-17a6-4bf3-b41d-1f8b33b30870`; latency 1365.7ms.


### T22 — PASS

**Prompt:** So sánh Dell Inspiron 15 và Dell XPS 13 về giá và RAM.

**Expected:** Hai target Dell Inspiron15/XPS13; giá/RAM từng variant, không names→CPU.

**QueryPlan baseline:**

```json
{
  "intent": "PRODUCT_COMPARE",
  "requested_fields": [
    "PRICE",
    "STOCK",
    "PROMOTION",
    "SPECIFICATIONS"
  ],
  "references": {},
  "product_search": {
    "category": "Laptop",
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [
      "Dell",
      "Inspiron",
      "XPS"
    ],
    "in_stock_only": false,
    "semantic_query": "so sánh Dell Inspiron 15 và Dell XPS 13 về giá và RAM",
    "top_k": 5
  },
  "semantic_query": null
}
```

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_COMPARE",
  "requested_fields": [
    "PRICE",
    "SPECIFICATIONS"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": "Dell XPS 13",
        "sku": null,
        "ram_gb": null,
        "storage_gb": null,
        "color": null
      },
      {
        "product_id": null,
        "variant_id": null,
        "product_name": "Dell Inspiron 15",
        "sku": null,
        "ram_gb": null,
        "storage_gb": null,
        "color": null
      }
    ],
    "category": null,
    "brands": [
      "Dell"
    ],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "So sánh Dell Inspiron 15 và Dell XPS 13 về giá và RAM.",
    "top_k": 2
  },
  "semantic_query": null
}
```

**Actual response:**

Dell XPS 13
- DEV-XPS13-SL-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 32,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.
- DEV-XPS13-GR-1TB: RAM 16GB, bộ nhớ 1TB, màu Xám; giá 36,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.
- DEV-XPS13-SL-1TB: RAM 32GB, bộ nhớ 1TB, màu Bạc; giá 41,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.
Dell Inspiron 15
- DEV-DELL-I15-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 15,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.

**Cards / evidence:** 2 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T22](checkpoint02/T22.jsonl), trace ID `a4c942ec-bf7d-470c-a771-ce6feab3b7f3`; latency 2148.85ms.


### T51 — PASS_WITH_WARNINGS

**Prompt:** Xiaomii 15 còn hàng hong?

**Expected:** Typo không được tự confirm identity; safe clarification theo blueprint. Auto typo resolution vẫn chưa hỗ trợ.

**QueryPlan baseline:**

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
    "semantic_query": "Xiaomii 15",
    "top_k": 5
  },
  "semantic_query": null
}
```

**QueryPlan mới:**

```json
{
  "clarification": "Mình chưa xác định chính xác model trong catalog. Bạn vui lòng xác nhận tên sản phẩm hoặc SKU (ví dụ Xiaomi 15).",
  "intent": "UNKNOWN",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```

**Actual response:**

Mình chưa xác định chính xác model trong catalog. Bạn vui lòng xác nhận tên sản phẩm hoặc SKU (ví dụ Xiaomi 15).

**Cards / evidence:** 0 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T51](checkpoint02/T51.jsonl), trace ID `a640d002-4cbc-4b23-a625-56b36ad47ed6`; latency 1095.69ms.

**Remaining gap:** chưa tự sửa typo rồi confirm Xiaomi15. Hỏi rõ là hành vi conservative đã duyệt, nhưng chưa đạt automatic exact identity resolution, nên giữ warning.

### T52 — PASS

**Prompt:** Is Xiaomi 15 in stock?

**Expected:** Exact Xiaomi15 khi hỏi tiếng Anh, không unrelated cards.

**QueryPlan baseline:**

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
    "semantic_query": "Xiaomi 15",
    "top_k": 5
  },
  "semantic_query": null
}
```

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DETAIL",
  "requested_fields": [
    "STOCK"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": "Xiaomi 15",
        "sku": null,
        "ram_gb": null,
        "storage_gb": null,
        "color": null
      }
    ],
    "category": null,
    "brands": [
      "Xiaomi"
    ],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Is Xiaomi 15 in stock?",
    "top_k": 1
  },
  "semantic_query": null
}
```

**Actual response:**

Xiaomi 15
- DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T52](checkpoint02/T52.jsonl), trace ID `6392662b-c932-4e64-bc4d-98b7c9c1ac6d`; latency 1538.46ms.


### T53 — PASS

**Prompt:** Smartphone Xiaomi nào còn hàng?

**Expected:** Smartphone→Điện thoại, Xiaomi brand, in_stock_only=true.

**QueryPlan baseline:**

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
    "semantic_query": "smartphone xiaomi",
    "top_k": 5
  },
  "semantic_query": null
}
```

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [
    "STOCK"
  ],
  "references": {},
  "product_search": {
    "targets": [],
    "category": "Điện thoại",
    "brands": [
      "Xiaomi"
    ],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": true,
    "semantic_query": "Smartphone Xiaomi nào còn hàng?",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Actual response:**

Xiaomi 15
- DEV-X15-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
Xiaomi 14T Pro
- DEV-X14TP-BLK-256: RAM 12GB, bộ nhớ 256GB, màu Đen; giá 16,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X14TP-BLU-512: RAM 12GB, bộ nhớ 512GB, màu Xanh; giá 18,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
- DEV-X14TP-GRY-512: RAM 16GB, bộ nhớ 512GB, màu Xám; giá 19,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.
Xiaomi Redmi Note 13
- DEV-RN13-BLK-128: RAM 6GB, bộ nhớ 128GB, màu Đen; giá 4,490,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 3 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T53](checkpoint02/T53.jsonl), trace ID `0e6bbcfc-15ad-4f9e-a402-5b14d10c1589`; latency 1125.43ms.


### T54 — PASS

**Prompt:** Tôi muốn máy vừa rẻ vừa mạnh, tư vấn giúp.

**Expected:** Hỏi loại máy/ngân sách/nhu cầu thay vì tự coi máy39triệu là rẻ.

**QueryPlan baseline:**

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
    "semantic_query": "máy vừa rẻ vừa mạnh",
    "top_k": 5
  },
  "semantic_query": null
}
```

**QueryPlan mới:**

```json
{
  "clarification": "Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?",
  "intent": "UNKNOWN",
  "requested_fields": [],
  "references": {},
  "product_search": null,
  "semantic_query": null
}
```

**Actual response:**

Bạn muốn tìm loại máy nào, ngân sách bao nhiêu và dùng cho nhu cầu gì?

**Cards / evidence:** 0 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T54](checkpoint02/T54.jsonl), trace ID `8425f063-9d40-4215-a5cf-126d5b0ca78f`; latency 1087.7ms.


### T55 — PASS

**Prompt:** Tìm laptop Dell dưới 20tr, ram >=16, ssd >=512.

**Expected:** Dell + Laptop + max20triệu + RAM>=16 + SSD>=512.

**QueryPlan baseline:**

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
    "min_storage_gb": 512,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "laptop dell duoi 20tr, ram >=16, ssd >=512",
    "top_k": 5
  },
  "semantic_query": null
}
```

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [
    "SPECIFICATIONS"
  ],
  "references": {},
  "product_search": {
    "targets": [],
    "category": "Laptop",
    "brands": [
      "Dell"
    ],
    "min_price": null,
    "max_price": "20000000",
    "min_ram_gb": 16,
    "min_storage_gb": 512,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Tìm laptop Dell dưới 20tr, ram >=16, ssd >=512.",
    "top_k": 5
  },
  "semantic_query": null
}
```

**Actual response:**

Dell Inspiron 15
- DEV-DELL-I15-512: RAM 16GB, bộ nhớ 512GB, màu Bạc; giá 15,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 24 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace T55](checkpoint02/T55.jsonl), trace ID `e3a61e6f-12f7-4d76-a7ec-c740390c7be1`; latency 2247.3ms.


### R01 — PASS

**Prompt:** SKU DEV-X15-GRN-512 hiện giá bao nhiêu và còn bao nhiêu máy?

**Expected:** SKU GRN512 exact, giá21.990.000/tồn2; card không chọn BLK256 rẻ nhất.

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DETAIL",
  "requested_fields": [
    "PRICE",
    "STOCK"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": null,
        "sku": "DEV-X15-GRN-512",
        "ram_gb": null,
        "storage_gb": null,
        "color": null
      }
    ],
    "category": null,
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "SKU DEV-X15-GRN-512 hiện giá bao nhiêu và còn bao nhiêu máy?",
    "top_k": 1
  },
  "semantic_query": null
}
```

**Actual response:**

Xiaomi 15
- DEV-X15-GRN-512: RAM 16GB, bộ nhớ 512GB, màu Xanh lá; giá 21,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace R01](checkpoint02/R01.jsonl), trace ID `e29f11ce-a763-4f1b-a9a1-cc8d51525ff9`; latency 1387.77ms.


### R02 — PASS

**Prompt:** Xiaomi 15 bản màu Trắng còn hàng không?

**Expected:** Xiaomi15 màuTrắng exact; SKU WHT512, giá20.990.000/tồn2.

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DETAIL",
  "requested_fields": [
    "STOCK",
    "SPECIFICATIONS"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": "Xiaomi 15",
        "sku": null,
        "ram_gb": null,
        "storage_gb": null,
        "color": "Trắng"
      }
    ],
    "category": null,
    "brands": [
      "Xiaomi"
    ],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "Xiaomi 15 bản màu Trắng còn hàng không?",
    "top_k": 1
  },
  "semantic_query": null
}
```

**Actual response:**

Xiaomi 15
- DEV-X15-WHT-512: RAM 12GB, bộ nhớ 512GB, màu Trắng; giá 20,990,000 VND; còn 2 máy tại kho được chọn; bảo hành 12 tháng.

**Cards / evidence:** 1 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace R02](checkpoint02/R02.jsonl), trace ID `78ef4dd9-5d46-4bf3-8a6d-0c9a592f3c03`; latency 3891.42ms.


### R03 — PASS

**Prompt:** SKU DEV-UPGRADE-NOTFOUND hiện còn hàng không?

**Expected:** SKU không tồn tại → NOT_FOUND clarification; không diễn giải thành hết hàng.

**QueryPlan mới:**

```json
{
  "clarification": null,
  "intent": "PRODUCT_DETAIL",
  "requested_fields": [
    "STOCK"
  ],
  "references": {},
  "product_search": {
    "targets": [
      {
        "product_id": null,
        "variant_id": null,
        "product_name": null,
        "sku": "DEV-UPGRADE-NOTFOUND",
        "ram_gb": null,
        "storage_gb": null,
        "color": null
      }
    ],
    "category": null,
    "brands": [],
    "min_price": null,
    "max_price": null,
    "min_ram_gb": null,
    "min_storage_gb": null,
    "cpu_keywords": [],
    "in_stock_only": false,
    "semantic_query": "SKU DEV-UPGRADE-NOTFOUND hiện còn hàng không?",
    "top_k": 1
  },
  "semantic_query": null
}
```

**Actual response:**

Không tìm thấy sản phẩm hoặc SKU này trong catalog; đây không phải kết luận hết hàng.

**Cards / evidence:** 0 cards; mọi card đã đối chiếu variant/price/stock/RAM/storage/color/warranty với backend. [Trace R03](checkpoint02/R03.jsonl), trace ID `fb55686e-ccc9-4020-98a7-532f4c808512`; latency 1595.99ms.


## 9. Trace evidence

[Runtime results](checkpoint02/runtime-results.json) lưu prompt/session/trace ID, HTTP status, latency, normalized QueryPlan, strategy, ChatResponse và backend card checks. Có 24 JSONL snapshots riêng, giữ chronological offsets/correlation; không chứa cookies/JWT/Authorization/DB URL/full personal DTO/embedding vectors. Lưu run boundary trước lượt cuối; chỉ excerpts của trace ID thuộc lượt cuối được đưa vào checkpoint.

Events quan trọng: query.domain_validation (model plan vs normalized), query.plan.initial/effective, product.identity_resolution, product.stock_evidence, product.resolution_failed, product.search/cards và response.strategy. Structured/semantic events chỉ xuất hiện khi path thực sự chạy; không tạo event giả cho exact path.

Đã xác minh 24 unique case/trace/session IDs, dropped_events=0, timestamps/offset order, và **59 eligible variant rows** với live detail. Cả card lẫn mỗi SKU/giá trong deterministic text được kiểm. Business snapshots [before](checkpoint02/business-before.json)/[after](checkpoint02/business-after.json) có hash giống nhau; chỉ lưu counts/hash, không raw business DTO.

Initial verification phát hiện hai lỗi nội bộ parser (RAM minimum bị hiểu thành min_price; bỏ dấu làm Tìm giống Tím). Đã sửa và thêm regression trước lượt cuối. Một số model discovery text ban đầu trộn specification RAM ở cấp product vào các eligible variants, nên product responses mới được dựng trực tiếp từ eligible evidence. Evidence cuối không trộn các lượt thử trung gian; raw runtime logs vẫn ở .local ignored.

## 10. Remaining limitations

1. T51 typo chưa auto resolve; yêu cầu xác nhận tên/SKU. Unknown/fuzzy name không tự khẳng định dựa embedding.
2. Domain grammar chưa bao phủ mọi paraphrase/brand aliases/CPU family. Taxonomy brand lookup hiện đọc page100, catalog/candidate coverage cap500; catalog quá cap từ chối execution, không báo product nonexistent. Không tuyên bố hỗ trợ mọi catalog lớn.
3. Color discovery không có identity và capacity upper/ranges chưa có execution contract; hỏi rõ thay vì bỏ điều kiện. Một số cấu trúc phủ định/phức tạp ngoài grammar vẫn cần eval thêm.
4. Category parent→descendants chưa có. Multi-intent chỉ hỏi chọn nhóm trước.
5. General product references/order-code resolver, customer identity, durable history và feedback mapping không sửa trong checkpoint này; chỉ giữ cheaper heuristic cũ và conservative clarification cho reference khác.
6. Multiple API reads có thể thấy giá/tồn thay đổi giữa search/detail/cards; chưa có atomic snapshot xuyên HTTP. Đây không phải stock reservation hoặc cam kết đặt hàng.
7. Business data live đều không có zero-stock target trong test; zero/mixed/unknown/error được test bằng isolated fixtures. Không chứng nhận warranty/ticket customer positive paths hoặc payment states mới.
8. Product responses bản mới là deterministic evidence text, không có lời tư vấn dài/phân tích CPU/REVIEWS hoặc prose comparison tổng hợp. Data được trả đúng, nhưng nội dung tư vấn nâng cao cần checkpoint sau theo phạm vi duyệt.
9. DB không UNIQUE product_name; ambiguous duplicate names được hỏi SKU/ID. Specifications projection dictionary chưa biểu diễn nhiều row cùng spec_key; không dùng nó làm resolver identity.

## 11. Compatibility risks

ProductSearchPlan.targets=[], QueryPlan.clarification=null và ProductMatch identity fields optional giữ các constructors/consumers cũ trong Python chạy được; full suite cũ vẫn pass. JSON schema có field mới, strict external consumer có thể cần cập nhật trước rollout. ChatResponse/card fields không thay đổi.

Giữ min_ram/min_storage là minimum, exact fields ở selector riêng. Semantic ranking không dùng exact identity. Response product formatting thay đổi sang fact list theo SKU để không hallucinate; API vẫn trả message/products/sources. Template giữ precision của effective_price, không làm tròn mất cents do promotion.

Resolver reuse GET hiện có; Java business status/price/inventory/ownership không đổi. Backend request cookies chỉ forward trong scoped ContextVar, không thay authentication. CustomerContext/auth mapping user_id=customer_id còn là vấn đề audit cho checkpoint sau; không persist khóa sai vào DB trong bản này.

## 12. HANDOFF checkpoint 03

Checkpoint02 đã hoàn thành implementation, tests và targeted runtime verification. Ưu tiên review contract extensions, output formatting, cap catalog/brand coverage và warning T51. Checkpoint03 có thể xử lý general reference/order selection hoặc phần RAG/customer được user giao, cùng policy persistence/identity sau khi được duyệt. Không tự chạy Prompt03.

Git cuối: 8 tracked Python files modified; thêm normalization.py và test_query_product_v2.py; thêm tài liệu/evidence dưới chatbot-upgrade (audit trước vẫn giữ). Không sửa Java/frontend/schema/migrations/seed/config. git diff --check đạt; không stage/commit/push. Services gốc vẫn chạy.
