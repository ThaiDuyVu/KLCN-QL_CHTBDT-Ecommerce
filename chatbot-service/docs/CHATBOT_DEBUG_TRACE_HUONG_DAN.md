# Debug chatbot đầu cuối bằng trace

## 1. Mục tiêu và cách mở công cụ

Mỗi lần gửi tin nhắn là một trace với UUID riêng. Trace ghi lại dữ liệu thực sự đi qua các hàm xử lý, thời điểm bắt đầu, thời gian chạy, quan hệ công đoạn cha/con và kết quả/lỗi. Trên trang **http://localhost:5173/chatbot-lab**, kéo xuống **Truy vết từng công đoạn**. Không cần tìm thông tin rải rác trong terminal.

Cấu hình chatbot-service/.env:

```dotenv
APP_ENV=development
CHATBOT_TRACE_ENABLED=true
CHATBOT_TRACE_FILE_ENABLED=true
```

Đã bật hai tùy chọn trace trong môi trường local lúc triển khai. File .env local không commit. Giá trị mặc định trong code và .env.example là false. Khởi động lại chatbot sau khi đổi .env; nút **Kiểm tra dịch vụ** tải lại trạng thái trên UI. File môi trường không thuộc thư mục app được uvicorn theo dõi, nên không tự reload khi chỉ sửa .env.

Các dịch vụ hiện có khởi động bằng `bash scripts/start.sh`, dừng bằng `bash scripts/stop.sh`. Script start không restart tiến trình đang chạy; muốn áp dụng cấu hình mới, dừng rồi khởi động lại. PostgreSQL/backend/frontend/chatbot/Ollama cần sẵn sàng để debug đầy đủ các luồng AI thật.

## 2. Quy trình thao tác

1. Đăng nhập bằng tài khoản kiểm thử, mở Chatbot Lab.
2. Kiểm tra trạng thái có **Đang bật ghi trace** và AI/backend ở chế độ mong muốn.
3. Gửi một câu hỏi, ví dụ “Tìm laptop dưới 20 triệu RAM từ 16GB”.
4. Timeline tự tải khoảng 1,5 giây/lần trong khi đang xử lý. Mở từng sự kiện để xem đầu vào/đầu ra. Sự kiện chứa messages có phần **Prompt system** và **Prompt user**, giữ xuống dòng dễ đọc.
5. Đối chiếu theo thứ tự: câu hỏi → lịch sử → prompt phân loại → output thô → QueryPlan đã validate → bộ lọc thực thi → dữ liệu backend → GroundedContext → prompt trả lời → output cuối.
6. Hỏi tiếp “Có loại rẻ hơn không?”. Bảng **Query đã được điều chỉnh sau khi model trả về** hiển thị các trường khác nhau giữa QueryPlan ban đầu và bộ lọc thật, ví dụ max_price.
7. Bật **Chỉ lỗi / fallback** để tìm validation fail, model lỗi hoặc knowledge không khả dụng. Bộ lọc này không phải công cụ tự đánh giá tính đúng của ngôn ngữ tự nhiên.
8. Dùng ô tìm kiếm theo `SKU`, `max_price`, `PAYMENT_STATUS`, tên event hoặc đoạn prompt. Tìm kiếm xét cả input/output.
9. Chọn lượt chat cũ trong dropdown. **Xuất trace JSON** để lưu một lượt làm bằng chứng trước/sau sửa code. File khác với nút xuất lịch sử chat thông thường.

## 3. Một lượt chat thực sự đi qua những gì?

| Sự kiện | Ý nghĩa / cần đối chiếu |
| --- | --- |
| request.accepted | Câu hỏi đã qua kiểm tra request và xác thực backend; phiên và tài khoản được gắn vào trace |
| conversation.context | Câu hỏi hiện tại và các tin lịch sử được chọn; tìm lịch sử thiếu hoặc tham chiếu sai |
| query.understand | Toàn bộ giai đoạn phân loại; output là QueryPlan sau validate hoặc UNKNOWN fallback |
| query.model_attempt | Prompt system/user, schema và output thô cho từng lần thử; lần repair có thêm yêu cầu sửa JSON |
| ollama.http | Payload thật gửi Ollama: model, messages/input, schema format, think, stream, temperature và num_predict |
| ollama.result | JSON model trả về, bao gồm content, done_reason và các chỉ số token/thời gian nếu Ollama cung cấp; bỏ embedding vector |
| query.validation_failed | Các lỗi schema với vị trí/type/message, không ghi input lỗi nhạy cảm của Pydantic |
| query.plan.initial | QueryPlan ban đầu model tạo |
| query.plan.effective | Bộ lọc sản phẩm thật sau xử lý top_k, hỏi rẻ hơn, giới hạn giá; below_minimum cho biết bỏ qua tìm kiếm vì khoảng giá không hợp lệ |
| backend.product_http / backend.product_result | Đường dẫn API, query parameters, HTTP status và JSON từ backend; không ghi cookie/Authorization |
| product.embedding / embedding.shape | Chuỗi semantic query, model và số chiều; không lưu hàng nghìn giá trị vector |
| product.vector_ranking / product.sql | Candidate product IDs, SQL pgvector thực thi, tham số không phải vector, điểm similarity sau query |
| product.search | Các ProductMatch sau lọc và enrich; đối chiếu với backend.product_result để nhận biết mất trường/thông tin |
| product.cards | Thẻ sản phẩm cuối, có tên sản phẩm, SKU, giá, tồn kho |
| customer.context / backend.customer_http | Intent, API khách hàng, JSON thô, CustomerContext sau mapping; dùng để tìm lỗi camelCase/snake_case, phân trang, thiếu payment |
| knowledge.retrieve / knowledge.vector_search / knowledge.sql | Câu query tìm tài liệu, top_k, trạng thái tài liệu, SQL thực thi, chunk và similarity_score |
| retrieval.summary | Số sản phẩm/card/chunk và nhánh intent; giúp thấy giai đoạn nào được sử dụng |
| response.grounded_context | Toàn bộ evidence, history và query plan được chuyển tới tầng tạo câu trả lời |
| response.strategy | Trả bằng quy tắc hay Ollama; nếu là Ollama có prompt tạo câu trả lời |
| response.generate | Câu trả lời cuối từ service; model có thể không được gọi nếu nhánh quy tắc đã trả lời |
| chat.pipeline | Công đoạn bao trùm, output là response API gồm message/cards/sources; mở dòng này để xem kết quả cuối |

Timeline sắp theo thời điểm bắt đầu. Vì vậy chat.pipeline nằm gần đầu nhưng hoàn tất sau các bước con. Thanh thời gian của công đoạn cha bao gồm thời gian của con: không cộng tất cả duration để suy ra tổng thời gian. Những sự kiện tức thời hiển thị “sự kiện” thay vì thời gian riêng.

SQL hiển thị là SQL Python thực thi ở tầng pgvector. Với Spring Boot, trace ghi API và tham số HTTP thực tế; chưa ghi SQL bên trong Java/JPA. Trace bắt đầu sau xác thực và validation request: lỗi 401/403 từ bước đăng nhập, request 422 sai body và lỗi proxy trước khi tới route không có trace pipeline. Đây là ranh giới của công cụ hiện tại.

## 4. Phân biệt lỗi kỹ thuật và câu trả lời sai

**ok** nghĩa là pipeline không ghi nhận exception hoặc fallback cảnh báo. Nó không khẳng định câu trả lời AI đúng. **warning** nghĩa là có bước bị lỗi nhưng đã được bắt và xử lý dự phòng. **error** nghĩa là exception thoát khỏi pipeline. Có thể nhận HTTP 200 cùng trace warning: trước đây các lỗi này dễ bị che bởi câu trả lời “chưa đủ dữ liệu”.

| Hiện tượng | Nơi xem đầu tiên | Hướng xử lý |
| --- | --- | --- |
| Hỏi thanh toán nhưng trả đơn hàng | query.model_attempt → query.plan.initial | Điều chỉnh prompt phân loại, bổ sung case kiểm thử PAYMENT_STATUS |
| Hỏi rẻ hơn vẫn cùng giá | query.plan.initial → query.plan.effective → backend.product_http | Xem max_price có giảm, backend có nhận tham số, giá live có đúng |
| Trả UNKNOWN bất ngờ | query.validation_failed / query.model_failure / query.fallback | JSON enum sai, schema thiếu, timeout hay giới hạn sinh; sửa đúng tầng thay vì đoán |
| Không có sản phẩm | backend.product_result → product.search → product.cards | API không trả candidate, bộ lọc loại variant, index thiếu hay dữ liệu live đổi |
| Tên/thương hiệu không có trong câu văn | backend.product_result → response.grounded_context → response.strategy | Nếu backend có nhưng GroundedContext không có thì lỗi/giới hạn mapping contract; đổi prompt đơn thuần không bổ sung được evidence |
| Chính sách sai hoặc nguồn không liên quan | knowledge.sql → knowledge.vector_search → response.grounded_context | Query, status, điểm tương đồng, chunk bị thiếu, top_k quá rộng |
| Backend trả đúng nhưng câu văn sai | response.strategy và ollama.result của giai đoạn trả lời | Kiểm tra evidence, chỉ dẫn và output thô; thêm case hồi quy cho phát hiện cụ thể |
| Model bị cắt câu/JSON | ollama.result.done_reason, eval_count, request.options.num_predict | Xem có dừng do length không trước khi chỉnh token limit |
| Trả chậm | duration của query.understand, ollama.http, product.search, knowledge.retrieve | Tách thời gian model, truy vấn backend và truy xuất vector |

Ví dụ đã quan sát trong dự án: product card có tên Dell Inspiron 15 nhưng ProductMatch được đưa vào GroundedContext chủ yếu chứa product_id, eligible_variants, điểm xếp hạng và reasons. Nếu câu văn ghi thương hiệu không xác định, hãy kiểm tra đúng sự chênh lệch này. Công cụ truy vết giúp xác định nguồn thiếu thông tin; lần triển khai này không tự đổi contract sản phẩm hoặc prompt nghiệp vụ để che vấn đề.

## 5. API và file log

Client có thể gửi UUID ở header `X-Chat-Trace-Id` khi POST `/api/v1/chat/sessions/{session_id}/messages`. Chatbot Lab tự tạo header này. Nếu không gửi, server tự tạo khi trace bật. Không tái sử dụng cùng trace ID: server trả 409 để tránh ghi đè lượt cũ.

- `GET /api/v1/chat/sessions/{session_id}/traces`: danh sách trace của tài khoản hiện tại trong phiên.
- `GET /api/v1/chat/sessions/{session_id}/traces/{trace_id}`: snapshot đầy đủ, kể cả khi đang xử lý hoặc pipeline lỗi.
- Các endpoint yêu cầu đăng nhập qua cơ chế backend hiện có. Trace của tài khoản khác trả 404. Response có Cache-Control: no-store.
- Response chat thành công và HTTPException đã xử lý có header X-Chat-Trace-Id. Với lỗi không được xử lý/timeout client, dùng ID đã tạo trước lúc gửi để tải lại.
- Không cần thêm bảng/migration; trace nằm trong RAM riêng với conversation.

Khi `CHATBOT_TRACE_FILE_ENABLED=true`, mỗi lượt hoàn tất được ghi thành một dòng JSON tại **.local/logs/chatbot-traces.jsonl** ở thư mục gốc repo. Xoay file ở 5 MB, giữ 2 bản cũ (.1, .2). File chỉ ghi khi trace đã bật và môi trường được cho phép. Không ghi mỗi token; model hiện dùng stream=false. Có thể theo dõi:

```bash
tail -f .local/logs/chatbot-traces.jsonl
```

Timeline cập nhật trong lúc chạy từ RAM; JSONL là snapshot kết thúc, nên không xuất hiện ngay lúc vừa gửi câu hỏi. UI không đọc lại file JSONL sau restart; dùng file đã xuất/log để phân tích ngoại tuyến.

## 6. Bảo mật, độ chính xác và giới hạn lưu trữ

Mặc định tắt. Chỉ bật trong APP_ENV development/local/test; production từ chối endpoint trace ngay cả khi biến bật là true. Mỗi user chỉ xem trace của chính mình; admin cũng không có endpoint xem toàn bộ người khác trong bản này.

Không thu thập cookie, header Authorization, đối tượng settings, kết nối DB. Các khóa password/token/secret/DSN/email/phone/address thường gặp được che cả trong JSON nhúng trong prompt. **Nội dung gửi model không bị chỉnh sửa bởi tracing**; bản lưu trace được che sau khi sao chép. Vì vậy prompt trên UI chính xác về cấu trúc và nội dung nghiệp vụ sau che, không phải bản sao byte tuyệt đối nếu có trường nhạy cảm. Nội dung tự do vẫn có thể chứa thông tin cá nhân; chỉ dùng dữ liệu kiểm thử khi chia sẻ file.

Giới hạn: 50 trace gần nhất cho toàn process, TTL 1 giờ, 256 sự kiện/trace, khoảng 1 triệu ký tự/trace, 64.000 ký tự/payload. Khi vượt giới hạn, có `truncated` hoặc `dropped_events`; không giả vờ đây là log đầy đủ. Trace cũ/running có thể bị loại khi quá tải hoặc hết TTL. Multi-worker có RAM tách biệt, nên dùng một worker khi debug local. Muốn phục vụ hệ thống nhiều worker cần kho trace dùng chung.

Exception ghi type, HTTP status nếu có, validation và vị trí file/hàm/dòng; không ghi tùy tiện str(exception) hoặc biến local vì chúng có thể chứa thông tin kết nối. Thông tin HTTP status, JSON thô của model và JSON backend được ghi ở bước tương ứng. File JSONL là dữ liệu chẩn đoán trên máy, không phải nhật ký audit giao dịch.

## 7. Mã nguồn và cách mở rộng

- `app/shared/tracing/__init__.py`: ContextVar, span, decorator, che dữ liệu, kho giới hạn, JSONL.
- `app/features/chat/router.py`: tạo trace sau auth, endpoint đọc trace có kiểm tra chủ sở hữu.
- `app/features/chat/service.py`: context hội thoại, QueryPlan đầu/cuối, GroundedContext và summary.
- Query/response Ollama services: các lần thử, validate, repair và fallback.
- Ollama/backend clients: request/response ở biên hệ thống.
- `Fontend/src/features/chatbot/TracePanel.jsx`: timeline, prompt viewer, bộ lọc, bảng thay đổi query và xuất JSON.

Khi thêm một service mới:

```python
from app.shared.tracing import traced, event

@traced('my_feature.lookup', ('query',))
def lookup(query, cookies):
    # cookies không nằm trong allowlist nên không được ghi.
    result = existing_lookup(query, cookies)
    event('my_feature.decision', {'reason': 'matched'})
    return result
```

ContextVar tự truyền qua asyncio.to_thread, vì vậy callback đồng bộ bên trong worker vẫn thuộc đúng trace. Decorator chỉ ghi các đối số allowlist; tránh thêm cookie/token hoặc settings vào đó. Dùng span để nhóm thao tác; dùng event cho quyết định không tốn thời gian đáng kể. Khi trace tắt, decorator gọi trực tiếp hàm nghiệp vụ.

## 8. Kiểm chứng triển khai

Test kiểm tra context không lẫn giữa hai lượt chạy đồng thời, parent/child trong worker, prompt JSON được che mà không đổi request gốc, TTL/giới hạn/ID trùng, repair hai lần và UNKNOWN fallback, lỗi HTTP vẫn tải trace được, tài khoản khác bị từ chối, production không bật trace, và output model bị cắt/thiếu content.

Frontend kiểm tra hiển thị prompt dạng văn bản an toàn, lọc lỗi, trace hết hạn và không gọi API trace khi tắt. API thật đã xác minh ba luồng sản phẩm (33 events), knowledge (27 events), giỏ hàng (22 events), kiểm tra cookie không xuất hiện trong trace và file JSONL được tạo. Số events thay đổi tùy dữ liệu và nhánh xử lý.

Kết quả hoàn tất: **156 test chatbot pass** (bao gồm PostgreSQL local), **49 test frontend pass**, lint và production build pass. Kiểm tra Chrome thật đã pass đăng nhập, gửi câu hỏi, mở prompt, không có lỗi JavaScript và không tràn ngang ở chiều rộng 390px. Ảnh kiểm chứng local nằm trong `.local/chatbot-lab/trace-desktop.png` và `trace-mobile.png`.
