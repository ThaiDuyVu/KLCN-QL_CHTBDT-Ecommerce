# Debug: “2 sản phẩm Laptop” bị nhận nhầm thành yêu cầu thương hiệu

## Kết luận

**CONFIRMED — HIGH / P1 — DOMAIN_NORMALIZATION.** Quy tắc nhận diện thương hiệu đứng trước danh mục đã lấy `phẩm` trong cụm **“sản phẩm Laptop”** làm tên hãng. Sau khi bỏ dấu, token `pham` không nằm trong danh sách từ ngữ cảnh và không thuộc catalog brands; ứng dụng ném `ConstraintError`, biến một yêu cầu tìm laptop hợp lệ thành `UNKNOWN` và trả câu hỏi xác nhận hãng không liên quan.

Lỗi tái hiện trên runtime thật. Lượt gốc không yêu cầu hãng; model đã nhận đúng danh mục và hai cận giá. Lỗi đầu tiên gây chặn nằm trong code kiểm tra domain sau model. Không sửa production logic trong phiên chẩn đoán này.

## Môi trường và phạm vi

- Ngày kiểm chứng: 21/10/2026; branch `dev/thai-duy-vu`, HEAD `df6438156589155510dcd88ba983aab852df87d9`.
- Frontend local 5173, Spring backend thật, FastAPI thật, Qwen qua Ollama; tài khoản CUSTOMER synthetic theo tài liệu repo. Không đưa thông tin đăng nhập vào evidence.
- Graph generation 2026-10-20T16:48:05Z báo `metadata_changed` ở các file được đọc; graph dùng định hướng, kết luận đối chiếu trực tiếp source hiện tại. Xem `graph-evidence.json`.
- Giữ nguyên các chỉnh sửa cart/customer context và tài liệu có sẵn. Hash sáu file ứng dụng được kiểm tra không đổi trong quá trình chạy: `source.json`, `source-unchanged.json`.
- Chỉ đọc trace cũ, catalog và tồn kho; ba lượt chat mới dùng ba session synthetic mới. Không POST vào session trong ảnh; không sửa inventory/business records; không commit/push. Các dịch vụ được giữ chạy.

## Lượt chat gốc trong ảnh

Prompt nguyên văn:

> gợi ý giúp tôi 2 sản phẩm Laptop có mức giá khoản 20-25 tr

- Trace: `ebb3233d-704f-4c6f-a6bd-f9c41d84c3c9`.
- Bắt đầu 2026-10-21T06:43:58.455106Z, tức 13:43:58 Việt Nam; history_count=0.
- Thời lượng trace 6660,47 ms; model HTTP chiếm khoảng 6554 ms. Không có bằng chứng timeout gây ra lỗi này.
- Trace status `ok` chỉ biểu thị request hoàn tất; nội dung trả lời vẫn FAIL. HTTP status của lượt gốc không được ghi trong excerpt nên UNKNOWN; ba lượt mới đều HTTP 200.

Plan model tại `query.validation`:

```json
{
  "intent": "PRODUCT_DISCOVERY",
  "requested_fields": [],
  "references": {},
  "product_search": {
    "category": "Laptop",
    "brands": [],
    "min_price": "20000000",
    "max_price": "25000000",
    "top_k": 5,
    "in_stock_only": false
  }
}
```

Model dùng top_k=5 chưa khớp yêu cầu 2; normalizer có `requested_count` đọc đúng 2 và sửa top_k trong control thành công. Không quy lỗi chặn thương hiệu cho khác biệt top_k này.

Chuỗi sự kiện theo thời gian:

1. `query.validation`: model nhận đúng Laptop, giá 20–25 triệu, không brand.
2. Backend đọc categories, brands, catalog để xác minh domain; chưa phải Product Advisor search.
3. `query.domain_validation`: `constraint_rejected=true`; normalized `intent=UNKNOWN`, `product_search=null`, clarification về thương hiệu.
4. `query.plan.initial`: lưu plan sau normalization, không phải raw model output.
5. `retrieval.summary`: 0 products, 0 cards, 0 knowledge chunks; không Product Advisor search, không semantic ranking.
6. `response.strategy`: `deterministic_guard`, reason=`clarification`.
7. ChatResponse sao chép nguyên clarification: “Thương hiệu hoặc tên sản phẩm được yêu cầu chưa xác minh được trong catalog. Bạn vui lòng xác nhận hãng/tên hoặc SKU; mình sẽ không thay bằng hãng khác.”

Evidence: `original-trace.json`.

## Cơ chế gây lỗi, có kiểm chứng biến cục bộ

Tại `app/features/query_understanding/constraints.py:339`, regex tìm một từ đứng ngay trước alias danh mục:

```python
re.finditer(r'(?<!\w)([\w-]+)\s+' + re.escape(alias) + r'(?!\w)', text)
```

Với `... 2 san pham laptop co muc gia khoan 20-25 tr`, alias=`laptop`, regex bắt **`pham`**. Token này vượt qua kiểm tra `IDENTITY_BOUNDARIES`, được thêm vào `stated`; dòng 348–350 kiểm tra không khớp brand nào và ném lỗi.

Controlled replay dùng đúng model plan gốc và taxonomy/catalog đọc từ backend thật. Python trace hook chỉ quan sát biến cục bộ khi exception; không sửa hàm:

```json
{"token":"pham","stated":["pham"],"name":"pham","known":[]}
```

Replay cũng xác nhận `requested_count=2`, min_price=20000000, max_price=25000000, `unresolved=[]`. Vì vậy typo **“khoản” không gây lỗi từ chối trong case này**. Đổi thành “khoảng” vẫn nhận nhầm `pham`. Cụm “sản phẩm điện thoại” cũng tái hiện cùng lỗi trong controlled replay; chưa thực hiện Qwen E2E riêng cho điện thoại.

`git blame` xác minh vòng lặp thương hiệu trước danh mục ở dòng 335–347 đến từ commit `8b1753f` — `fix(chatbot): validate unknown brands before product types`. Đây là hồi quy từ bộ bảo vệ unknown-brand, không chỉ là chất lượng câu trả lời LLM. Không suy rộng rằng mọi lỗi brand đều có cùng nguyên nhân.

Luồng gọi source hiện tại:

`OllamaQueryUnderstandingService.understand` → `DomainNormalizer.normalize/_normalize` → `validate_brands` → `ConstraintError` → `QueryPlan(UNKNOWN, clarification=...)` → ChatService bỏ qua Product Advisor → ResponseGeneration trả clarification bằng code.

Vị trí chính: `normalization.py:41–48,106`, `constraints.py:305–350`, `query_understanding/ollama_service.py:60–61`, `chat/service.py:185–191`, `response_generation/ollama_service.py:23–25`.

## Đối chứng runtime thật

Expected được lưu vào `reproduction-plan.json` trước khi POST. Mỗi case session mới, history_count=0; kết quả và trace đầy đủ đã chọn lọc trong `live-reproduction.json`.

| Case | Prompt | Expected | Actual | Verdict / first fault |
|---|---|---|---|---|
| original-repeat | gợi ý giúp tôi 2 sản phẩm Laptop có mức giá khoản 20-25 tr | Tìm Laptop 20–25 triệu, tối đa 2 sản phẩm; báo thiếu nếu ít hơn | HTTP 200, UNKNOWN, thông báo thương hiệu, 0 cards | FAIL / DOMAIN_NORMALIZATION |
| spelling-only | gợi ý giúp tôi 2 sản phẩm Laptop có mức giá khoảng 20-25 tr | Cùng phạm vi và số lượng | HTTP 200, UNKNOWN, cùng thông báo, 0 cards | FAIL / DOMAIN_NORMALIZATION |
| remove-generic-noun | gợi ý giúp tôi 2 Laptop có mức giá khoản 20-25 tr | Cùng phạm vi và số lượng; không ép catalog phải đủ hai | HTTP 200, PRODUCT_DISCOVERY, top_k=2, một card MacBook Air M2; báo 1/2 phù hợp | PASS |

Trace IDs và HTTP duration:

- original-repeat: `3d88900a-e39d-43cb-842c-6f827193d417`, 1937,28 ms.
- spelling-only: `4dadcd3f-1028-4d38-a2a2-11385098e04f`, 1993,90 ms.
- remove-generic-noun: `ac7e28f2-74f9-4e06-be02-df1e5303af52`, 3338,93 ms.

Quan sát phụ: model ở spelling-only tự thêm min_ram_gb=16 và thiếu max_price. Đây là sai slot của **lượt đối chứng**, không phải output lượt gốc. Lượt này dừng ở brand guard nên chưa có effective retrieval plan; không khẳng định nó đã gây lọc RAM sai ở backend. Controlled replay cô lập dùng cùng plan gốc để phân biệt lỗi model với lỗi xác định `pham`.

## Đối chiếu backend: câu trả lời hợp lý phải là gì?

Đọc độc lập `/api/v1/products/search/advanced` với `category=Laptop`, minPrice=20000000, maxPrice=25000000, warehouse giống runtime, inStockOnly=false, limit=100, offset=0 trả **1 cặp product/variant**. Catalog danh tính có 16/16 sản phẩm, một trang.

Đọc tiếp `/api/v1/products/{id}/detail?warehouseId=...` xác minh:

- MacBook Air M2, SKU `DEV-MBA-M2-256`, ACTIVE.
- RAM 8GB, storage 256GB, Bạc.
- Effective price **22.990.000 đồng**.
- Available quantity **2 tại kho được chọn**, bảo hành 12 tháng.

Control trả đúng dữ liệu này và mở đầu “Chỉ tìm thấy 1/2 sản phẩm phù hợp…”. Số máy tồn kho 2 không đồng nghĩa có 2 sản phẩm khác nhau. Với dữ liệu hiện tại, phản hồi đúng nên nói chỉ có một mẫu phù hợp trong khoảng giá, rồi hỏi có muốn mở rộng giá/điều kiện; không yêu cầu người dùng xác nhận một thương hiệu mà họ chưa nêu.

Evidence độc lập: `backend-range-verification.json`. `catalog-card-verification.json` là GET product thông thường, chỉ xác minh tên/giá; product GET đó không có warehouse nên tồn kho null. Xác nhận stock phải dùng endpoint detail có warehouse trong evidence độc lập.

## Tests và khoảng trống regression

Các lệnh chạy tại `chatbot-service`, bằng `.venv/bin/python`:

| Lệnh | Exit | Kết quả |
|---|---:|---|
| `-m pytest tests/test_query_integrity.py tests/test_pipeline_tracing.py -q` | 4 | Không chạy test: file test_pipeline_tracing.py không tồn tại ở checkout hiện tại |
| `-m pytest tests/test_query_integrity.py -q` | 0 | 111 passed, 0,24 s |
| `-m pytest tests/test_tracing.py -q` | 0 | 6 passed, 1 warning deprecation Starlette/httpx, 0,15 s |

117 existing tests PASS **không chứng minh case này đúng**. Bộ `test_before_type_known_brand_and_use_cases_do_not_become_unknown_brand` có Dell/gaming/văn phòng/mỏng nhẹ/một chiếc/2 chiếc..., nhưng thiếu “sản phẩm Laptop”. Chưa chạy full suite vì không thay implementation. Xem `test-results.json`.

Harness tái hiện: từ repo root, `chatbot-service/.venv/bin/python chatbot-service/debug/uat-diagnostics/2026-10-21-false-brand-product-phrase/diagnose.py`. Lệnh tạo thêm ba session synthetic mới; cần runtime và tài khoản development theo repo. Backend-range verification được thu bằng đọc độc lập sau harness và không tự chạy lại trong harness.

## Hướng sửa đề xuất, chưa triển khai

Owner: Query Understanding / domain normalization, Người 3; ưu tiên P1 vì chặn yêu cầu discovery rất thông thường trước retrieval.

1. Phân tích vị trí identity theo cụm danh từ; nhận biết “sản phẩm”, “mẫu máy”, từ chỉ số lượng/ngữ cảnh trước category. Không mặc định mọi token liền trước category là brand.
2. Giữ bảo vệ unknown-brand cho yêu cầu thật như “Lenovo laptop”, “laptop Lenovo”, “hãng Acme”. Không vô hiệu hóa toàn bộ guard hoặc đổi thành tìm không có brand khi người dùng có yêu cầu brand.
3. Phân biệt diagnostic reason/token bị từ chối trong trace thay vì chỉ có boolean constraint_rejected; không đưa token giả định thành khẳng định về ý người dùng trong UI. Đây là đề xuất, không sửa tracing trong task này.
4. Regression cần thêm: nguyên văn screenshot, typo-only, chữ hoa/thường, “hai sản phẩm laptop”, “sản phẩm điện thoại”, generic noun phrases, brand hai phía, unknown brand hai phía, exact model, số lượng 2 khi catalog chỉ 1. Kiểm tra cả normalized QueryPlan, việc advisor có được gọi, filters, cards và nội dung phản hồi.

Tác động compatibility chính là giữ phân biệt brand thật và từ ngữ cảnh. Chỉ thêm `pham` vào danh sách bỏ qua có thể chữa câu này nhưng chưa chứng minh xử lý được các cụm danh từ khác; cần bộ positive/negative tests trước khi chọn bản sửa.

## Trạng thái kết thúc

Hoàn tất chẩn đoán có bằng chứng; hai lần tái hiện lỗi trên pipeline thật, một control thành công, năm controlled probes tách riêng khỏi E2E. Không sửa production code, không commit/push, không dừng runtime. Các thay đổi có sẵn trong working tree được giữ nguyên. `summary.json` là kết quả máy đọc được.
