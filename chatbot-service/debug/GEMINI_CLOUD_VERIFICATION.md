# Kiểm chứng một lượt gọi Gemini cloud thật

Ngày kiểm chứng: 03/11/2026, 18:46:29 (UTC+7). Branch `dev/vu`, HEAD `371934c1ba7e0b7806c40731687decf547d5893f`, có thay đổi tích hợp Gemini chưa commit. Chỉ chạy một prompt qua API thật, không chạy benchmark hoặc full test suite.

## Request và kết quả

- Prompt: `Tìm laptop dưới 20 triệu.`
- Session mới của synthetic development CUSTOMER; đăng nhập qua backend thật, không dùng mock transport.
- POST `/api/v1/chat/sessions/{session_id}/messages`, body chọn `provider=gemini`.
- Trace ID: `012a130c-5b17-40d2-983a-8a0112d8edfd`.
- Model cấu hình: `gemini-3.1-flash-lite`.
- API chatbot HTTP 200; trace `ok`; tổng thời gian phía client 5116,87 ms.
- Kết quả: ASUS Vivobook 15 giá 12.990.000đ, tồn 3; Dell Inspiron 15 giá 15.990.000đ, tồn 2. Cả hai dưới 20 triệu.

## Bằng chứng gọi ngoài

1. `app/shared/llm.py:38` chọn Gemini adapter theo provider request. Client thật được tạo trong `app/features/chat/dependencies.py`, không inject MockTransport.
2. `app/shared/gemini/client.py:44` tạo URL `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent`, gọi `httpx.Client.post` với key trong header. Trace HTTP status được ghi sau khi nhận response thật.
3. `gemini.http_status`: HTTP 200; `gemini.usage`: input 1592 tokens, output 65 tokens, tổng 1657 tokens. Có đúng 1 Gemini HTTP event và 0 Ollama chat event.
4. Quan sát bằng `lsof` các tiến trình listen cổng 8090 trong khi request chạy: 16 mẫu kết nối TCP 443, peer `172.217.112.4` và `172.217.119.4`. Cả hai trùng kết quả DNS của `generativelanguage.googleapis.com` tại thời điểm đối chiếu. Đây là bằng chứng mạng hỗ trợ trace; không giải mã TLS hoặc khẳng định mọi socket quan sát được đều được tạo riêng cho request này.

Kết luận: bằng chứng source, transport, response và trace nhất quán với gọi Gemini cloud thật; không chỉ đổi nhãn giao diện. Chưa đối chiếu dashboard usage/billing phía Google. Tên model trong trace là model được yêu cầu, không phải chứng thực độc lập nội bộ phục vụ model của Google.

## Luồng xử lý được ghi nhận

| Bước | Event | Quan sát |
| --- | --- | --- |
| Nhận request và xác thực | `request.accepted` | CUSTOMER được backend xác thực |
| Chọn model | `llm.provider_selected` | Gemini, gemini-3.1-flash-lite |
| Tạo QueryContext | `conversation.context` | Session mới |
| Hiểu câu hỏi | `query.model_attempt` → `gemini.chat` | Gọi cloud khoảng 4748,17 ms |
| Nhận JSON model | `gemini.http_status` → `gemini.usage` | HTTP 200; 1657 tokens |
| Validate và chuẩn hóa | `query.validation` → `query.domain_validation` | Thành công |
| Plan thực thi | `query.plan.effective` | category Laptop, max_price 20000000, top_k 5; không thêm RAM/brand |
| Tìm và xếp hạng | `product.search` → `product.embedding` → `product.vector_ranking` | Backend thật, embedding local |
| Tạo cards, làm mới evidence | `product.cards` → `product.evidence_refreshed` | Hai sản phẩm phù hợp |
| Grounding | `response.grounded_context` | Evidence sản phẩm đã truy xuất |
| Tạo câu trả lời | `response.strategy` | `deterministic_product_evidence` |
| Lưu và trả kết quả | `conversation.message_persisted`, `chat.pipeline` | Thành công |

JSON do Gemini trả về:

```json
{
  "intent": "PRODUCT_DISCOVERY",
  "product_search": {
    "category": "Laptop",
    "max_price": 20000000,
    "semantic_query": "laptop dưới 20 triệu"
  }
}
```

Gemini xử lý Query Understanding trong lượt này. Giá/tồn kho đến từ backend; câu trả lời cuối do code dựng từ evidence, không gọi Gemini lần thứ hai để viết văn. Embedding vẫn local theo thiết kế. Vì thế bằng chứng này không được dùng để tuyên bố đã kiểm chứng cloud response generation cho mọi intent.

## Artifact và tái kiểm tra

- `.local/gemini-verification/verify_one.py`: harness chạy một prompt, đồng thời quan sát socket; chạy lại sẽ phát sinh một request chatbot mới.
- `.local/gemini-verification/20261103-184629/evidence.json`: request, ChatResponse, output Gemini, event timeline, token usage và mẫu socket đã lọc; không có key/header/cookie.
- `.local/gemini-verification/20261103-184629/correlated-log.txt`: trích log mới theo trace ID và POST của session kiểm thử.
- `.local/logs/chatbot.log`, `.local/logs/chatbot-traces.jsonl`: log runtime gốc, không đưa vào commit.

Lệnh đã chạy: `chatbot-service/.venv/bin/python .local/gemini-verification/verify_one.py`. Artifact xác nhận HTTP 200 và trace ok. Có thể kiểm tra lại trace trong Chatbot Lab của tài khoản dev đã tạo session. Frontend `http://localhost:5173/chatbot-lab`, chatbot health `/api/v1/health` trả 200. Các dịch vụ có sẵn được giữ chạy; không cần restart. Không sửa application logic trong lần kiểm chứng này, không commit/push.
