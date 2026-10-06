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

Mặc định giữ mock pipeline V3.5. Copy `.env.example` thành `.env` và đặt
`CHATBOT_AI_ENABLED=true` để dùng Query Understanding và Response Generation thật.
Cấu hình model, timeout và địa chỉ Ollama qua các biến trong `.env.example`.
Endpoint chat xác thực customer qua backend bằng cookie; lịch sử và feedback
sử dụng ConversationService với repository in-memory, chưa lưu bền vào PostgreSQL. ProductAdvisor thật cần được inject qua
`build_chat_service(settings, product_advisor=...)`; nếu chưa có thì không trả sản phẩm mẫu.

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
