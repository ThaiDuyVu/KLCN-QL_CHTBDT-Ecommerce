# Chatbot service

Python 3.12+, FastAPI, Pydantic, httpx và PostgreSQL/pgvector.

## Cài đặt và kiểm thử

```bash
cd chatbot-service
python3.12 -m venv .venv
.venv/bin/python -m ensurepip
.venv/bin/python -m pip install -e '.[dev]'
.venv/bin/python -m pytest tests -q
.venv/bin/python -m uvicorn app.main:app --port 8090
```

Copy `.env.example` thành `.env`. Endpoint chat xác thực customer qua backend bằng cookie.

## Chế độ chạy

Mỗi thành phần được chọn độc lập; mặc định là cấu hình phát triển.

| Thành phần | Mặc định (dev) | Thật | Biến |
|---|---|---|---|
| Query Understanding + Response Generation | tắt (mock, không gọi Ollama) | Qwen qua Ollama | `CHATBOT_AI_ENABLED=true`, `OLLAMA_*` |
| Product Advisor | `mock` (sản phẩm mẫu) | backend Spring + pgvector index | `PRODUCT_ADVISOR_MODE=real`, `PRODUCT_INDEX_DATABASE_URL`, tùy chọn `CHATBOT_WAREHOUSE_ID` |
| Hội thoại, snapshot | `memory` (mất khi restart) | PostgreSQL bảng `chat_sessions`/`chat_history` | `CONVERSATION_STORAGE=postgres`, `CONVERSATION_DATABASE_URL` |
| Feedback, trace | RAM của process | chưa có lưu bền | `CHATBOT_TRACE_ENABLED` chỉ có hiệu lực ở môi trường dev |

Không có kho (`CHATBOT_WAREHOUSE_ID` rỗng) vẫn hợp lệ: câu trả lời ghi "chưa xác định tồn kho
vì chưa chọn kho", còn tìm "còn hàng" sẽ báo không khả dụng. Chatbot dùng một kho chung,
chưa dùng kho khách chọn trên storefront.

### Kiểm tra cấu hình khi khởi động

`APP_ENV` thuộc `development`, `local`, `test` là môi trường dev. Với mọi giá trị khác
(ví dụ `production`, `staging`), service **từ chối khởi động** nếu thiếu một trong các điều kiện:
`CHATBOT_AI_ENABLED=true`, `PRODUCT_ADVISOR_MODE=real`, `CONVERSATION_STORAGE=postgres`,
`CONVERSATION_DATABASE_URL`, `PRODUCT_INDEX_DATABASE_URL`, và `CHATBOT_TRACE_ENABLED`/`CHATBOT_TRACE_FILE_ENABLED`
đều tắt. Thông báo lỗi chỉ nêu tên biến, không in giá trị.

Ở mọi môi trường, khi đang chạy AI tắt, Product Advisor mock, hội thoại memory, thiếu kho, hoặc
`APP_ENV` không được đặt tường minh, service ghi một log WARNING `chatbot.runtime_configuration`
(logger `chatbot.runtime`) gồm `app_env`, `app_env_explicit` và danh sách cảnh báo.

Giới hạn cần biết khi triển khai:

- Mặc định `APP_ENV=development`. **Nếu quên đặt `APP_ENV`, kiểm tra trên bị bỏ qua** và mock/memory
  vẫn chạy; khi đó chỉ còn log WARNING. Môi trường triển khai phải đặt tường minh `APP_ENV=production`,
  `PRODUCT_ADVISOR_MODE=real`, `CONVERSATION_STORAGE=postgres`, `CHATBOT_AI_ENABLED=true` và lấy URL/secret từ
  secret store. Repo chưa có cấu hình deploy production để tự bảo đảm điều này.
- `GET /api/v1/health` chỉ là liveness (process còn sống), không kiểm tra database, backend hay Ollama.
  Chưa có endpoint readiness (`/api/v1/ready` đang chờ quyết định DR-05a).
- `GET /api/v1/chat/status` là trang chẩn đoán của Chatbot Lab, trả 404 ngoài môi trường dev.

### Hành vi đáng chú ý

- Điều kiện giá được đọc theo quan hệ: "dưới/không quá/tối đa X", "trên/hơn/đắt hơn/tối thiểu X",
  "X trở xuống/đổ lại", "X trở lên", "từ A đến B", "A–B triệu". Số tiền mà chatbot chưa hiểu chắc
  ("tầm/khoảng/cỡ X", "ngân sách X", "rẻ hơn/thấp hơn X", "giá X") **không bị bỏ qua**: chatbot hỏi lại
  thay vì tìm kiếm thiếu điều kiện giá. Ngữ nghĩa của các dạng này đang chờ quyết định DR-01/DR-02.
- Tham chiếu ("máy đầu tiên", "cái thứ hai", "máy vừa gợi ý", "có loại rẻ hơn không?") chỉ dùng thẻ đã
  hiển thị trong phiên của chính khách. Nếu câu nêu loại/hãng mới, chatbot không áp phạm vi cũ.
- Khi embedding hoặc pgvector index lỗi, kết quả lọc có cấu trúc vẫn được trả theo thứ tự cố định và
  trace ghi `product.ranking_degraded`; lỗi backend tìm kiếm/chi tiết sản phẩm vẫn trả 503.
- Khách có danh sách đơn rỗng (backend trả 200) nhận "Tài khoản của bạn chưa có đơn hàng nào."; lỗi
  backend không bị diễn giải thành "chưa có đơn". Mã đơn so khớp không phân biệt hoa thường.

### Chưa làm trong đợt này

Phân trang catalog/brand vượt 500 sản phẩm/100 brand (vẫn trả 503 an toàn, DR-04); thông báo
"tìm quá rộng" (DR-13b); xử lý sản phẩm có promotion chồng lấn (vẫn 503, DR-13a); giới hạn số đơn hiển thị
(DR-06); readiness endpoint (DR-05a); widget chatbot trên storefront (DR-03); feedback/idempotency lưu bền
(DR-08); kho theo khách (DR-07); câu trả lời giỏ hàng khi khách chưa từng có giỏ (backend trả `cartId: null`,
chatbot hiện trả 502); Knowledge RAG chính thức, multi-intent, cross-session memory.

## Knowledge RAG

Cần PostgreSQL có pgvector và schema V1 của backend đã được migrate. Đặt
`KNOWLEDGE_DATABASE_URL` và `KNOWLEDGE_DOCUMENT_STATUS` theo status được nhóm thống nhất.
`PUBLISHED` hiện là mặc định tạm, không phải enum đã được schema xác nhận.

```bash
.venv/bin/python -m app.features.knowledge.ingest
# Sau khi đổi model embedding, chủ động tái index:
.venv/bin/python -m app.features.knowledge.ingest --force
```

CLI đọc tài liệu đủ status từ `knowledge_documents`; không đọc file DOCX và không
truy cập bảng nghiệp vụ ecommerce. Không suy đoán dimension của model hoặc tạo vector index.

Test DB thật là opt-in, dùng database kiểm thử có extension `vector` sẵn:

```bash
KNOWLEDGE_TEST_DATABASE_URL='postgresql://user:password@localhost:5432/test_db' \
  .venv/bin/python -m pytest tests/test_knowledge_postgres_integration.py -q
```

Test tạo rồi xóa schema riêng. Tầng hội thoại có thể inject `after_response` để
persist message, lấy `history_id`, sau đó gọi `record_retrievals(history_id, matches)`.
Không dùng `session_id` thay cho `history_id`. Message ID trong repository
in-memory hiện chưa phải history_id đã lưu ở PostgreSQL để ghi retrieval audit.

Xem [tài liệu phân tích và học lại](docs/CHATBOT_AI_KNOWLEDGE_HOC_LAI.md) để hiểu
luồng dữ liệu, quyết định thiết kế, cách debug và các điểm cần phối hợp.

## Product retrieval

Hướng dẫn Product Advisor: [PRODUCT_RETRIEVAL_GUIDE.md](docs/PRODUCT_RETRIEVAL_GUIDE.md).

## Phòng thử frontend riêng

Mở http://localhost:5173/chatbot-lab sau khi chạy `bash scripts/start.sh` tại root.
Trang dùng đăng nhập/cookie hiện có, hiển thị kịch bản/kỳ vọng, cards, nguồn,
response JSON, thời gian và feedback. Route chỉ bật trong dev hoặc khi build với
`VITE_CHATBOT_LAB_ENABLED=true`; chưa tích hợp widget vào storefront chính thức.

- [Giải thích hoạt động và phân tích hiện trạng](docs/CHATBOT_HOAT_DONG_VA_HIEN_TRANG.md).
- [Hướng dẫn thao tác và kết quả kỳ vọng](docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md).

### Debug prompt và query đầu cuối

Xem [hướng dẫn Trace trong Chatbot Lab](docs/CHATBOT_DEBUG_TRACE_HUONG_DAN.md): timeline, prompt thật, QueryPlan trước/sau điều chỉnh, backend/SQL, evidence và fallback. Bật `CHATBOT_TRACE_ENABLED=true` trong môi trường phát triển; tùy chọn `CHATBOT_TRACE_FILE_ENABLED=true` ghi JSONL xoay vòng vào `.local/logs/` ở gốc repo.
