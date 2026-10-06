# Học cách truy xuất sản phẩm cho chatbot

Tài liệu này giải thích phần Product Advisor đã triển khai từ skeleton V3.5. Mục tiêu là tìm **variant thực sự thỏa điều kiện**, dùng embedding để xếp hạng mức liên quan, sau đó lấy lại giá, tồn kho và bảo hành hiện tại từ backend trước khi trả `ProductCard`. Giá và tồn kho trong vector index không phải nguồn sự thật.

## Tóm tắt kiến thức

| Khái niệm | Vai trò trong hệ thống |
| --- | --- |
| `Protocol` | Ranh giới giữa `MockChatService` và implementation Product Advisor. Mock vẫn dùng mặc định. |
| Hard filter | Backend PostgreSQL quyết định category, brand, giá sau khuyến mãi, RAM, storage, CPU và stock theo warehouse. |
| Semantic ranking | Embedding chỉ sắp thứ tự các product **đã qua** hard filter; không thể đưa product sai điều kiện vào kết quả. |
| Enrichment | `GET /api/v1/products/{id}/detail` cung cấp tên, ảnh, variant, giá khuyến mãi, tồn kho và warranty mới nhất. |
| Content hash | Nếu nội dung mô tả kỹ thuật không đổi và embedding model không đổi thì manual indexer bỏ qua gọi Ollama. |
| Typed query | Chatbot gửi tham số HTTP cụ thể; text người dùng không trở thành SQL. Backend bind mọi giá trị bằng `NamedParameterJdbcTemplate`. |

## Các file cốt lõi

- `app/features/product_advisor/backend.py`: client tìm candidate và lấy product detail từ backend.
- `app/features/product_advisor/real_service.py`: lọc lại dữ liệu động, xếp hạng, tạo `ProductMatch` và `ProductCard`.
- `app/features/product_advisor/normalization.py`: chuẩn hóa `16GB`, `512GB`, `1TB`; chuỗi không hợp lệ trả `None`.
- `app/features/product_advisor/semantic.py`: sinh embedding qua Ollama, tính hash, upsert và tìm điểm cosine trong PostgreSQL.
- `app/features/product_advisor/index_products.py`: job index thủ công, không chạy nền.
- `backend/.../product/service/ProductAdvancedSearchService.java`: query variant có hard filters tại DB.
- `backend/.../db/migration/V7__add_product_semantic_index.sql`: bảng index không khóa kích thước vector; lưu cả tên embedding model.

## Luồng xử lý

```mermaid
flowchart LR
    A[Query Understanding] --> B[ProductSearchPlan]
    B --> C[Backend advanced search]
    C --> D[Candidate variant IDs đã qua hard filters]
    B --> E[Ollama query embedding]
    D --> F[pgvector scoring trong candidate set]
    E --> F
    F --> G[Rank và giới hạn top K]
    G --> H[Backend Product Detail theo warehouse]
    H --> I[Kiểm tra lại hard filters và tạo ProductMatch]
    I --> J[Refresh detail và tạo ProductCard]
    J --> K[ChatResponse.products]
    I --> L[GroundedContext]
    L --> M[Response Generation tạo message]
```

`PRODUCT_DISCOVERY`, `PRODUCT_DETAIL` và `PRODUCT_COMPARE` dùng cùng Product Advisor khi Query Understanding cung cấp `ProductSearchPlan`. Detail giới hạn một product; compare giới hạn ba product. Mock Query Understanding hiện chỉ nhận diện laptop discovery; nhận diện câu hỏi detail/compare tự nhiên thuộc phần Query Understanding về sau.

## Các quy tắc quan trọng

1. Backend chỉ trả `productId` và `variantId` của các variant hợp lệ; query dùng `LIMIT` tối đa 100 mỗi trang. Product Advisor đọc tối đa 500 candidate variant trong một lần tìm để kiểm soát tải.
2. Price filter dùng **effective price hiện tại** theo promotion, cùng nguyên tắc tính giá của Product API. Variant inactive và product inactive luôn bị loại.
3. Stock filter cần `warehouseId`; không cộng stock các kho. Nếu `in_stock_only=true` mà chưa cấu hình warehouse, service báo lỗi rõ ràng.
4. RAM/storage chỉ chấp nhận số kèm `GB` hoặc `TB`. Dữ liệu thiếu hoặc sai format không thỏa minimum filter. CPU keyword tìm trong specification CPU/processor, không đoán từ category.
5. ProductMatch chỉ chứa variant của chính product đó. `build_cards` đọc detail lần nữa; nếu giá/stock/status đổi và không còn phù hợp, card bị loại.
6. Index chỉ lưu mô tả tương đối ổn định (tên, category, brand, specification, variant attributes). Giá, promotion, stock không nằm trong nội dung embedding. Đổi embedding model sẽ kích hoạt index lại dù content hash không đổi.
7. Khi không có candidate, trả `[]`; Response Generation hiện có sẽ nói không tìm thấy sản phẩm.

## Cách chạy phần liên quan

Backend hiện yêu cầu đăng nhập cho Product API. Hãy dùng JWT của một tài khoản được phép đọc Product API qua `BACKEND_BEARER_TOKEN`; không ghi token vào Git. `CHATBOT_WAREHOUSE_ID` là UUID của warehouse dùng để kiểm tra tồn kho. Skeleton chưa truyền shopping context riêng của từng customer vào Product Advisor, nên cấu hình này mới là warehouse dùng chung cho môi trường tích hợp.

1. Chạy PostgreSQL, backend và Ollama khi cần kiểm tra end-to-end; không cần frontend cho job lập chỉ mục. Flyway tự áp dụng V7 khi backend khởi động.
2. Trong `chatbot-service`, cài dependencies bằng `.venv/bin/python -m pip install -e .`.
3. Sao chép `.env.example` thành `.env` và cấu hình `BACKEND_BASE_URL`, `BACKEND_BEARER_TOKEN`, `PRODUCT_INDEX_DATABASE_URL`, `OLLAMA_BASE_URL`, `OLLAMA_EMBEDDING_MODEL`, `CHATBOT_WAREHOUSE_ID`. `PRODUCT_INDEX_DATABASE_URL` dùng URI kiểu `postgresql://user:password@host:5432/database`, không dùng JDBC URL.
4. Sau khi backend và Ollama sẵn sàng, chạy job một lần: `.venv/bin/python -m app.features.product_advisor.index_products`. Chạy lại chỉ embed những product có nội dung/model thay đổi.
5. Đặt `PRODUCT_ADVISOR_MODE=real`, khởi động chatbot-service rồi gọi endpoint chat hiện có. Nếu không đặt, mock V3.5 vẫn là mặc định.

Ví dụ request trực tiếp tới backend để kiểm tra hard filter (cần Bearer token):

```text
GET /api/v1/products/search/advanced?category=Laptop&brands=Dell&minRamGb=16&minStorageGb=512&inStockOnly=true&warehouseId=<UUID>&limit=20&offset=0
```

API này trả cặp `productId`/`variantId`, không trả toàn bộ entity. `minPrice`, `maxPrice` là VND và áp dụng trên giá sau promotion. `cpuKeywords` có thể lặp lại; mọi từ khóa đều phải khớp. Danh sách có giới hạn và có offset.

## Kiểm tra và giới hạn hiện tại

- Pytest: 68 tests pass, gồm mapping tham số, capacity, hard-filter precedence, live refresh, empty result, hash idempotency và reuse advisor cho detail/compare.
- Backend compile pass; focused integration test với Testcontainers áp dụng Flyway V7 và kiểm tra SQL lọc variant, kho, CPU, RAM/storage, promotion và tham số có ký tự SQL.
- Chưa chạy end-to-end bằng tài khoản/backend/Ollama thật. Cần token hợp lệ và cấu hình model/DB của môi trường triển khai để làm bước đó.
- Index được cập nhật **thủ công**, chưa có event hoặc lịch chạy. Product mới/chỉnh mô tả sẽ chưa có semantic score đến khi index lại; hard filters và kết quả catalog vẫn hoạt động.
- Giới hạn 500 candidate giúp tránh quét không giới hạn, nhưng với catalog lớn hơn có thể bỏ lỡ product liên quan nằm sau cửa sổ này. Nếu cần scale tiếp, chuyển sang tìm kiếm vector có filter server-side hoặc cursor-based streaming có ngân sách rõ ràng.
- Chưa có customer warehouse context và auth/session forwarding trong shared contracts V3.5. Việc dùng warehouse/token cấu hình chỉ phù hợp bước tích hợp hiện tại; không coi đó là shopping context cá nhân.
- Named product reference cho so sánh/chi tiết chưa được mock Query Understanding trích xuất. Adapter đã dùng chung retrieval khi nhận `ProductSearchPlan`, nhưng bước nhận diện và phân giải tên sản phẩm cần được bổ sung ở phần Query Understanding sau này.
