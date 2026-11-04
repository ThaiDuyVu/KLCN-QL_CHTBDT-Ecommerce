# Chọn Gemini hoặc Ollama cho chatbot

## Cấu hình local

Đặt cấu hình trong **chatbot-service/.env**, không đặt key vào frontend hoặc commit vào Git. Bản `.env.example` chỉ chứa key rỗng.

```dotenv
CHATBOT_AI_ENABLED=true
LLM_PROVIDER=ollama
OLLAMA_CHAT_ENABLED=true
GEMINI_ENABLED=true
GEMINI_API_KEY=<key local của bạn>
GEMINI_MODEL=gemini-3.1-flash-lite
GEMINI_TIMEOUT_SECONDS=30
GEMINI_MAX_OUTPUT_TOKENS=1024
```

- `LLM_PROVIDER=ollama`: giữ model local làm mặc định; trang chatbot khách hàng hiện tại vẫn dùng mặc định này.
- `GEMINI_ENABLED=false`: tắt Gemini và từ chối request chọn Gemini, không âm thầm đổi provider. Ollama vẫn hoạt động nếu đang bật.
- `OLLAMA_CHAT_ENABLED=false`: tắt lựa chọn local **cho chat**, khi đó phải chọn một provider mặc định đang bật. Đây không phải lệnh dừng Ollama vì embedding vẫn cần nó.
- `CHATBOT_AI_ENABLED=false`: công tắc AI chung hiện có; sử dụng chế độ mock hiện có, không gọi LLM.
- Nếu muốn Gemini làm mặc định: đặt `LLM_PROVIDER=gemini`, cần bật Gemini và có key hợp lệ. Thiếu cấu hình sẽ báo lỗi rõ khi khởi động.
- Restart chatbot-service sau khi sửa `.env`; không cần restart Spring để chọn LLM. Không gửi key lên trình duyệt hoặc log.

## Sử dụng

1. Đăng nhập CUSTOMER development, mở `http://localhost:5173/chatbot-lab`.
2. Bấm **Kiểm tra dịch vụ** để cập nhật trạng thái.
3. Trong **Model dùng cho lượt gửi tiếp theo**, chọn Mặc định, Ollama local hoặc Gemini. Option Gemini bị vô hiệu hóa khi tắt/chưa có key; configured không phải bằng chứng quota/model upstream sẵn sàng.
4. Gửi câu hỏi. Mỗi lượt chỉ chọn một provider, không chạy cả hai để đối chiếu tự động.

API Lab nhận trường tùy chọn `provider: "ollama" | "gemini"` trong POST messages; bỏ trường này thì dùng mặc định. Việc chọn theo lượt chỉ cho môi trường development/local/test. Production có thể dùng provider mặc định do vận hành cấu hình, không cho customer đổi provider qua request. Endpoint vẫn giữ xác thực/ownership hiện có.

Gửi prompt cùng lịch sử gần đây tới provider được chọn để hiểu câu hỏi. Price/stock/order evidence vẫn từ backend, code chuẩn hóa/guard vẫn chạy. Combo, so sánh và nhiều câu trả lời sản phẩm tiếp tục được dựng bằng code từ evidence. Do đó provider Gemini được chọn không có nghĩa toàn bộ câu trả lời cuối đều do Gemini viết.

## Kiến trúc và file

- `app/shared/gemini/client.py`: adapter REST generateContent, systemInstruction/messages, JSON Schema, timeout, output cap và token usage. Dùng header x-goog-api-key, không dùng query parameter key; không ghi upstream error body.
- `app/shared/llm.py`: chọn provider qua ContextVar riêng cho từng request, tránh ảnh hưởng request đồng thời; không tự fallback sang model khác. `embed()` luôn gọi client Ollama hiện có.
- `app/features/chat/dependencies.py`: ghép hai client với cùng pipeline, dùng chung history/retrieval/guards.
- `app/features/chat/router.py`: giới hạn chọn theo lượt ở Lab, kiểm tra provider bật trước khi ghi chat history; status trả model/toggle/configured, không trả key.
- `app/shared/config.py`: cấu hình có API key kiểu SecretStr, loại khỏi model_dump/repr, ẩn input trong thông báo lỗi validation.
- `Fontend/src/features/chatbot/ChatbotLabPage.jsx`, `chatbotApi.js`: lựa chọn provider cho từng lượt gửi.

Trace bổ sung: `llm.provider_selected`, `gemini.chat`, `gemini.http_status`, `gemini.usage`. Gemini HTTP/timeout lỗi trả 503 có thông báo an toàn; không hiện raw body/headers. Sai JSON vẫn qua schema validation/repair giới hạn hiện có, nên một lượt chat lỗi schema có thể cần thêm một lần model attempt. Không có network retry tự động ở adapter.

### Phân biệt lỗi upstream

HTTP của API chatbot vẫn là 503 khi provider thất bại; đọc mã `Gemini HTTP ...` trong thông báo hoặc `gemini.http_status` để biết mã thực từ Google. Mã 503 upstream là dịch vụ tạm thời không khả dụng, có thể quá tải; không đủ căn cứ kết luận sai key/hết quota. Mã 429 liên quan giới hạn/quota; 403 liên quan quyền; 404 cần kiểm tra model/quyền truy cập. Không ghi raw error body hoặc header vào trace.

Lượt lỗi thực ngày 03/11/2026 lúc 18:35:08 (UTC+7), trace `886cfabd-6ae1-4cb9-a561-26aac3bd0f68`: chọn `gemini-3.1-flash-lite`, Google trả 503 sau khoảng 2,2 giây ở bước Query Understanding. Chưa tới retrieval hoặc tạo câu trả lời. Trace không lưu body upstream nên nguyên nhân chi tiết là UNKNOWN; không khẳng định có sự cố diện rộng. Đã sửa thông báo phân loại theo mã lỗi và kiểm tra bằng MockTransport: 12 focused tests PASS. Không gửi thêm prompt thật để tái hiện lỗi này, không tự retry hoặc chuyển provider.

## Thử thực tế trong phiên tích hợp

Chỉ một nội dung prompt: **“Tìm laptop dưới 20 triệu.”**; tài khoản synthetic CUSTOMER theo repo. Không benchmark hoặc full test suite.

| Lần thử cùng prompt | Kết quả |
|---|---|
| gemini-2.5-flash-lite | Gemini HTTP 404; API chatbot 503. Chưa xác minh nguyên nhân chi tiết upstream; Google có lưu ý hạn chế model 2.5 với project mới. Không gọi model local để che lỗi |
| gemini-3.1-flash-lite | API chatbot 200; 10.235,42 ms; đúng một Gemini call, không Ollama chat call |

Plan Gemini thành công: PRODUCT_DISCOVERY, category=Laptop, max_price=20000000; normalization giữ cận giá. Trả ASUS Vivobook 15 12.990.000 đ và Dell Inspiron 15 15.990.000 đ, đều dưới ngân sách. Final response dùng `deterministic_product_evidence`; đây là xác minh Gemini ở Query Understanding cùng retrieval thật, chưa kiểm chứng riêng nhánh Gemini sinh văn bản cuối.

- Trace thành công: `a866d977-a474-47a6-942d-af79af1f8c0a`.
- Token usage API báo: input 1592, output 65, total 1657.
- Trace không chứa API key. Evidence local: `.local/gemini-smoke/one-prompt.json`; lần 404 giữ tại `attempt-01-2.5.json`. Không commit raw local evidence/credentials.
- Focused tests: `.venv/bin/python -m pytest tests/test_gemini_provider.py -q` → **8 passed**, exit 0 (0,16 s). Kiểm tra chuyển messages/schema, disabled/no-call, redaction lỗi, request-context isolation, local embeddings, cấu hình key và chặn override trước service. Không gọi Gemini thật trong unit tests.
- Frontend ESLint hai file đổi và build thành công; build còn cảnh báo chunk >500 kB. Chưa browser-test toàn bộ giao diện hoặc benchmark chất lượng hai model.

## Tài liệu API

- https://ai.google.dev/api/generate-content
- https://ai.google.dev/gemini-api/docs/structured-output
- https://ai.google.dev/gemini-api/docs/models/gemini-3.1-flash-lite
- https://ai.google.dev/gemini-api/docs/models/gemini-2.5-flash-lite
