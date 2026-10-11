# Chatbot: cách hoạt động, phạm vi sử dụng và phân tích hiện trạng

Ngày cập nhật: 06/10/2026. Mã nguồn bắt đầu từ `103551f` trên `dev/thai-duy-vu`,
đã hợp nhất phần Conversation/Customer Context, Product Retrieval và AI Knowledge.
Tài liệu này cập nhật đánh giá ban đầu bằng những sửa chữa và kiểm chứng cho trang Lab.

Tài liệu liên quan:

- [Nền tảng AI Knowledge để học lại](CHATBOT_AI_KNOWLEDGE_HOC_LAI.md).
- [Product Retrieval](PRODUCT_RETRIEVAL_GUIDE.md).
- [Hướng dẫn sử dụng Lab và kết quả kỳ vọng](CHATBOT_LAB_HUONG_DAN_KIEM_THU.md).

## 1. Chatbot giải quyết bài toán gì?

Đây là trợ lý thông tin cho website bán thiết bị điện tử. Nó tiếp nhận câu hỏi
ngôn ngữ tự nhiên, tìm dữ liệu thích hợp và trình bày kết quả bằng tiếng Việt.
Ba nhóm nguồn chính là catalog sản phẩm, tài liệu knowledge và thông tin cá nhân
được backend trả về theo cookie của người dùng đang đăng nhập.

AI không tự sở hữu giá, tồn kho hoặc trạng thái đơn hàng. Các thông tin động này
phải đi qua backend. AI không được sinh SQL để truy cập database nghiệp vụ.
Repository pgvector chỉ phục vụ knowledge và index ngữ nghĩa sản phẩm.

Chatbot hiện là trợ lý đọc và tư vấn. Không có tool đặt hàng, sửa giỏ hàng,
chuyển tiền, hủy đơn hay tạo phiếu bảo hành. Việc model nói một câu giống hành động
không chứng minh hành động đã diễn ra; không được coi câu trả lời là giao dịch.
Prompt response đã nhấn mạnh không khẳng định thực hiện những thao tác này.

## 2. Những gì đã xây dựng

| Phần | Implementation | Ý nghĩa |
| --- | --- | --- |
| API | FastAPI | Nhận câu hỏi, xác thực, trả response và lịch sử |
| Conversation | Service + repository in-memory | Kiểm tra ownership session, lưu message, feedback |
| Query Understanding | Ollama + JSON schema + Pydantic | Chuyển câu hỏi thành QueryPlan đúng contract |
| Product Advisor | Structured backend filter + vector ranking | Tìm ứng viên, kiểm tra variant, tạo card |
| Knowledge RAG | Chunk/index/embed/pgvector search | Tìm tài liệu có thể hỗ trợ câu trả lời |
| Customer Context | Backend adapter + DTO mapping | Đọc giỏ/đơn/payment/bảo hành theo người dùng |
| Response Generation | Ollama nhận GroundedContext | Sinh văn bản từ evidence được cung cấp |
| Sources | Deterministic mapping | UUID/title/source lấy từ repository, không do model nghĩ ra |
| Lab frontend | React tại `/chatbot-lab` | Thử kịch bản, xem evidence, đo thời gian và đánh giá |

Các Protocol và Pydantic shared contracts được giữ nguyên. Thay đổi API phục vụ
Lab gồm status và history; không thêm message_id vào ChatResponse để tránh sửa
contract chung. Lab đọc history sau response để lấy ID message phục vụ feedback.

## 3. Kiến trúc từ trình duyệt đến dữ liệu

```text
Trình duyệt: http://localhost:5173/chatbot-lab
    |
    | /api/auth/*, /api/cart, /api/orders/* ...
    +--> Vite proxy /api --> Spring Boot :8080 --> PostgreSQL
    |
    | /api/v1/chat/*
    +--> Vite proxy riêng --> FastAPI :8090
                                  |
                                  +--> Spring Boot: xác thực cookie và đọc business data
                                  +--> Ollama :11434: chat và embedding
                                  +--> PostgreSQL/pgvector: knowledge và semantic index
                                  +--> RAM: hội thoại và feedback
```

Rule proxy `/api/v1/chat` phải đứng trước `/api`. Nếu đảo thứ tự, câu hỏi chatbot
có thể bị chuyển sang Spring Boot và nhận 404. Vite proxy chỉ phục vụ development;
khi deploy frontend build cần reverse proxy tương ứng ở hosting/server thật.

Trang Lab dùng cookie hiện có, không yêu cầu dán JWT và không lưu token trong
localStorage. Trình duyệt gửi `credentials: include`. apiClient vẫn lấy CSRF token
cho request POST. FastAPI dùng backend `/api/auth/me` để xác thực cookie.

Dùng một hostname nhất quán. Cấu hình local backend cho phép Origin
`http://localhost:5173`; truy cập frontend bằng `127.0.0.1` có thể bị CORS từ chối
ở bước login dù cùng máy. Không cần tắt CSRF hay mở CORS toàn bộ để xử lý tình huống này.

## 4. Một câu hỏi được xử lý như thế nào?

### Bước 1: xác thực và ownership

Router lấy cookie từ request rồi gọi backend. Nếu không có phiên hợp lệ, API
trả 401; nếu backend từ chối quyền truy cập, giữ 403. Lỗi kết nối backend trả 503.

Session ID do Lab tạo bằng UUID. Backend chatbot kiểm tra session thuộc customer
đang đăng nhập trước khi đọc hoặc ghi message. Không cho khách B dùng ID session
của khách A để xem lịch sử hoặc gửi feedback.

Backend `/api/auth/me` hiện cung cấp userId; identity adapter dùng userId làm khóa
ownership nội bộ cho session. Đây chưa phải mapping tới customers.customer_id
trong PostgreSQL. Business data được backend xác định theo JWT/cookie; chatbot
không query customer tables bằng khóa nội bộ này.

### Bước 2: lịch sử và QueryContext

User message được lưu vào repository in-memory. Service đọc cửa sổ lịch sử theo
CHAT_HISTORY_WINDOW, loại message vừa lưu khỏi recent_messages để current_message
không bị lặp trong prompt. User và assistant message cũ được đổi sang ChatMessage
shared contract. Window bằng 0 không gửi lịch sử.

QueryContext chứa current_message, recent_messages và identity/session cho service.
Prompt hiểu câu hỏi chỉ gửi nội dung lịch sử và câu hiện tại, không gửi UUID hoặc
display_name không cần thiết.

### Bước 3: QueryPlan

Query Understanding gửi schema của QueryPlan cho Ollama. Model chọn Intent,
requested_fields, references, product_search hoặc semantic_query.

Ví dụ “laptop dưới 20 triệu, RAM từ 16GB” cần plan với max_price=20000000,
min_ram_gb=16 và category= Laptop. Plan mô tả nhu cầu, không xác nhận có hàng.

Pydantic kiểm tra enum, extra fields, min/max price, top_k và product_search bắt buộc
cho discovery. Output sai được sửa tối đa một lần theo cấu hình mặc định, rồi
fallback UNKNOWN. Ollama offline cũng fallback có kiểm soát.

Intent chỉ là quyết định của model. Ví dụ câu hỏi “bảo hành” có thể được hiểu là
chính sách knowledge hoặc trạng thái cá nhân. Câu hỏi mẫu của Lab cố gắng diễn đạt
rõ “của tôi” hoặc “theo tài liệu DEMO” để kiểm chứng đúng nhánh xử lý.

### Bước 4: thu thập evidence

| Intent | Đường xử lý |
| --- | --- |
| PRODUCT_DISCOVERY / DETAIL / COMPARE | Product Advisor nếu có product_search |
| KNOWLEDGE_QA | Embed semantic_query và retrieve knowledge chunks |
| CART_STATUS | GET /api/cart |
| ORDER_STATUS | GET /api/orders/mine, đọc content của trang |
| PAYMENT_STATUS | Đơn đầu trong danh sách gần đây, rồi GET detail bằng orderId |
| WARRANTY_STATUS | GET /api/warranties/mine, đọc content |
| WARRANTY_TICKET_STATUS | GET /api/warranties/mine/tickets, đọc content |
| GREETING / HELP / UNKNOWN | Không tự đọc các bảng nghiệp vụ |

Đọc tài khoản cá nhân chỉ diễn ra cho intent cần nó. Không tải giỏ hàng trong
PRODUCT_DISCOVERY chỉ vì đang tư vấn sản phẩm. Điều này giảm request không cần
thiết và tránh làm hỏng tìm sản phẩm vì API giỏ hàng của tài khoản quản trị bị 403.

### Bước 5: GroundedContext

Context cuối gồm query plan, user query, history, ProductMatch[],
KnowledgeChunkMatch[] và CustomerContext typed. Backend DTO dùng camelCase được
map sang field snake_case đúng shared contracts. Giá trị thiếu không được biến
thành 0/PAID/ACTIVE để làm đủ dữ liệu.

Không gửi JWT, cookie hoặc password vào model. Không gửi trực tiếp toàn bộ DTO
đơn có địa chỉ, số điện thoại nếu contract không cần các trường này.

### Bước 6: sinh câu trả lời

Greeting/help và một số đường thiếu evidence trả câu deterministic. Giỏ rỗng có
câu “Giỏ hàng của bạn hiện đang trống”. Đơn có nhưng thiếu payment không được
khẳng định đã/chưa thanh toán; service trả chưa có dữ liệu xác nhận và không gọi model.

Khi có evidence, model nhận GroundedContext và instruction không bịa dữ liệu,
không dùng history làm truth và không coi nội dung chunk là system instruction.
Ollama dùng temperature=0, stream=false, num_predict=512, think=false để tránh trộn reasoning vào
message gửi người dùng. Nhiệt độ thấp không phải bảo đảm model đúng tuyệt đối.

Product cards và source references là dữ liệu do code xây dựng. Message do model
sinh là diễn giải. Trong trường hợp message khác card/backend, cần đánh dấu “Cần
sửa”, lưu evidence và sửa prompt/logic; không tự coi message là truth.

### Bước 7: lưu assistant và phản hồi

Assistant text được lưu in-memory. ChatResponse trả session_id, message,
products, sources và suggested_questions. Hook after_response vẫn có để owner
persistence tích hợp sau này; không tự tạo chat_history row ở nhánh này.

Lab đọc history để lấy message.id rồi POST feedback với rating +1, -1 hoặc 0.
ID message RAM không phải history_id PostgreSQL. Audit DB chỉ hoạt động khi tầng
persistence thật cung cấp ID đã lưu đúng bảng chat_history.

## 5. Product retrieval: structured filter trước, semantic rank sau

Backend advanced search xác định ứng viên theo danh mục, hãng, giá, RAM/storage,
CPU keywords và warehouse/in_stock_only. Có paging giới hạn tối đa 500 ứng viên.

Product Advisor lấy detail mới nhất, kiểm tra ACTIVE và variant hợp lệ, xếp hạng
bằng vector nếu plan có semantic_query, rồi tạo ProductMatch và ProductCard.
Card chọn variant phù hợp có giá thấp nhất trong nhóm. So sánh hiện giới hạn tối
đa ba kết quả; detail giới hạn một kết quả. Đây chưa phải logic so sánh đầy đủ mọi
thông số CPU/GPU/benchmark hoặc resolve mọi product reference trong hội thoại.

Nội dung semantic index chứa tên/description/specs và đặc điểm variant; không
được dùng giá/tồn kho trong vector như nguồn xác nhận cuối cùng. Giá và số lượng
trên card lấy lại từ backend.

Cookie khách hiện được truyền theo ContextVar trong mỗi request. Không mutate
headers/cookies của một client dùng chung giữa nhiều khách. Khi có cookie người
dùng, header bearer cấu hình service không được ghi đè identity đó.

Với câu follow-up chứa “rẻ hơn/thấp hơn/cheaper”, service lấy snapshot card cuối
của chính session/customer và ép max_price thấp hơn giá rẻ nhất đã trả 1 VND.
Snapshot typed lưu trong RAM, không parse lại chuỗi giá từ văn bản model. Nếu
không có giá trước, vẫn cần model hiểu tham chiếu; đây chưa phải resolver tổng quát.

Search và build_cards phải chạy trong cùng một worker/context vì Product Advisor
lưu search plan trong ContextVar. Trước sửa, hai asyncio.to_thread tách biệt có
thể khiến bước build_cards không thấy plan để recheck variant. Hiện hai bước được
gom vào một callback và token cookie được reset trong finally.

Nếu yêu cầu “còn hàng” nhưng chưa cấu hình kho, service trả lỗi rõ ràng. Chưa có
product index/embedding/backend thật thì cần sửa cấu hình thay vì đánh giá card
mock như dữ liệu catalog.

## 6. Knowledge RAG: offline indexing và online retrieval

Offline ingestion đọc knowledge_documents theo status được cấu hình. Chunking
mặc định 1000 ký tự, overlap 150, chuẩn hóa line ending và giữ chunk_index ổn định.
Chunk không đổi và đã có embedding được tái sử dụng. Batch embedding tối đa 32;
transaction ghi theo từng document sau khi vectors được validate.

Online retrieval embed câu query, join knowledge_chunks với documents, lọc status,
vector khác null/zero, dimension phù hợp, rồi dùng cosine distance `<=>`.
Similarity là 1 - distance, không phải xác suất câu trả lời đúng. Chưa có reranker
hoặc relevance threshold, nên kết quả gần nhất vẫn có thể không trả lời được câu hỏi.

VECTOR không hardcode dimension. Dimension môi trường này phải lấy từ vector
thật; thay model cùng dimension vẫn cần re-index vì semantic space có thể khác.
Dùng --force sau đổi embedding model và không phục vụ dữ liệu trộn hai model.

SourceReference giữ đúng document_id/chunk_id/title/source. Source xuất hiện có
nghĩa chunk được đưa vào context, không chứng minh từng câu trong output được
model dùng đúng nguồn đó.

Hai tài liệu seed Lab có source demo://chatbot-lab/... và document_type DEMO.
Nội dung ghi rõ là tình huống kiểm thử, không phải chính sách chính thức. Không
nên giữ các tài liệu này trong tập knowledge phục vụ website production.

## 7. Những lỗi đã phát hiện và sửa trong đợt Lab

| Vấn đề | Trước sửa | Sau sửa |
| --- | --- | --- |
| Response phân trang | Không đọc content; cả trang bị coi là bản ghi | Đọc content cho orders/warranties/tickets |
| Mapping customer DTO | Gán dict camelCase vào typed context | Chuyển đổi và validate các model shared |
| Payment | Gán context.payment không tồn tại; ID lấy sai field | Dùng OrderContext.payment, orderId đúng backend |
| Kết nối backend | ConnectError có thể thoát thành 500 | HTTPException 503 với thông báo rõ |
| Backend malformed JSON/schema | Có thể im lặng coi là không có dữ liệu | 502, không đưa dữ liệu lỗi vào model |
| Quyền backend | 403 bị đổi thành 401 | Giữ 401/403 để phân biệt hết phiên và thiếu quyền |
| Product identity | Static bearer không đi theo cookie khách | Cookie theo request, không làm rò giữa khách |
| Search/build context | ContextVar có thể mất giữa hai worker | Cùng callback/thread/context |
| Follow-up rẻ hơn | Model có thể lặp card cùng giá và gọi là rẻ hơn | Snapshot card theo ownership; max_price nhỏ hơn giá rẻ nhất lần trước 1 VND |
| Output QueryPlan | Có lúc model sinh output quá dài, timeout | Ví dụ JSON tối giản, think=false và num_predict=512 |
| Lịch sử | current_message bị lặp, window=0 trả toàn bộ | Loại message hiện tại; window=0 trả rỗng |
| Startup local | Process có thể bị dừng khi shell điều khiển kết thúc | Popen start_new_session, PID/log file, reload chatbot |
| Proxy frontend | /api/v1/chat đi vào backend chung | Proxy riêng sang port 8090 |

Sửa adapter/flow không có nghĩa đã đủ production. Bộ unit test cũ pass vẫn bỏ sót
mismatch DTO; cần luôn bổ sung kiểm tra response thực tế và end-to-end.

## 8. Trang Lab phục vụ việc gì?

Lab là route riêng, không phải widget chat trong storefront. Nó giúp tester:
chọn kịch bản, đọc kỳ vọng, gửi câu hỏi, xem card/source, đánh giá, đo thời gian,
xem response JSON và xuất file evidence.

Lab chỉ có route trong Vite development hoặc build có VITE_CHATBOT_LAB_ENABLED=true.
Không thêm vào navigation cửa hàng. Khi build web chính thức với flag tắt, route
Lab không được mở. Nếu bật cho staging, vẫn yêu cầu đăng nhập.

Status endpoint hiển thị mode, model presence và việc cấu hình DB/kho/index; không
trả DSN, password, token hoặc nội dung prompt. “Đã cấu hình” không đồng nghĩa đã
index thành công. Cần xem kết quả chạy CLI và nguồn/card thực tế.

Reload giữ session UUID theo sessionStorage scoped userId và đọc tối đa 100 message
text từ server. Product cards/source/JSON của câu trả lời không được persist nên
không khôi phục sau reload. Export trước khi đóng/reload nếu muốn giữ evidence.

## 9. Phạm vi có thể dùng và các giới hạn còn lại

Có thể kiểm thử tư vấn catalog, FAQ/chính sách có nguồn và đọc dữ liệu cá nhân
được backend trả về. Các câu trả lời phụ thuộc dữ liệu catalog, seed và tài khoản.
Không có record thì phải nói thiếu dữ liệu, không giả lập record để làm test pass.

Chưa hoàn thành cho production:

- Hội thoại và feedback mất khi restart; nhiều worker sẽ có RAM riêng.
- Audit PostgreSQL chưa được nối với chat_history persistence thật.
- Chưa có full factual evaluation, entailment check hoặc chống prompt injection tuyệt đối.
- Requested references chưa được resolve đầy đủ; payment hiện đọc đơn mới nhất,
  không tự chọn đúng mọi mã đơn được nói trong câu hỏi.
- Listing customer hiện chỉ đọc trang gần đây đầu tiên, không phải toàn bộ lịch sử.
- Feedback dùng history lookup, chưa có assistant message ID trong ChatResponse;
  tránh mở hai tab gửi đồng thời vào cùng một session để đánh giá nhầm message.
- Chưa có streaming, queue/rate limit, request id tracing hay cơ chế job polling khi timeout.
- Model/schema không hiểu yêu cầu vẫn có thể fallback UNKNOWN; cần ghi nhận và cải tiến.
- Giá/stock có thể thay đổi giữa tìm kiếm, tạo card và lúc khách mua; backend checkout
  vẫn cần xác nhận lại theo business logic hiện có.
- Vite proxy không tồn tại trong frontend static build; cần cấu hình reverse proxy
  đúng, cookie/CSRF/TLS phù hợp và routing SPA trước khi deploy.

## 10. Đọc code để học lại theo thứ tự

1. shared/contracts/query.py: model nào cho phép model tạo, invariant nào được validate.
2. query_understanding/ollama_service.py: history, prompt, repair/fallback.
3. chat/service.py: orchestration và chỗ gom search/build trong worker.
4. product_advisor/backend.py + real_service.py: filter, identity, live detail, cards.
5. knowledge/service.py + repository.py: chunk/index/search/audit.
6. customer_context/service.py: camelCase -> typed context, không sửa contracts.
7. response_generation/ollama_service.py: evidence guard và response prompt.
8. chat/router.py + main.py: auth, history/status/feedback, lifespan.
9. Fontend/src/features/chatbot: UI, API, scenarios và frontend tests.
10. tests/test_customer_context_mapping.py: test tái hiện lỗi integration trước sửa.

Khi debug, bắt đầu bằng status và Network tab, kiểm tra intent/evidence thực tế,
sau đó mới sửa prompt. Không chữa lỗi DTO, auth hoặc index bằng cách “nhắc model
trả lời tự tin hơn”. Phân biệt lỗi dịch vụ, không có dữ liệu và model hiểu sai intent.

## 11. Tài liệu upstream

- [Ollama API](https://github.com/ollama/ollama/blob/main/docs/api.md): chat, embed, structured output.
- [Ollama CLI standalone](https://ollama.com/blog/new-app): binary CLI từ releases chính thức.
- [pgvector](https://github.com/pgvector/pgvector): toán tử cosine, vector dimension, indexes.

## 12. Kết quả thực tế để đọc cùng phân tích

Đợt Lab đã chạy Ollama và pgvector thật: 150 test chatbot pass, 46 frontend test
pass; lint/build frontend pass. Index có 16 sản phẩm và 2 chunk DEMO, vector
dimension đo được 1024. 12 kịch bản API trả thành công và được đối chiếu với data
backend. Browser kiểm tra login/chat/feedback/export/history và mobile layout.

Xem bảng kết quả, thời gian, record thiếu và phạm vi chưa được chứng minh trong
[mục kết quả của hướng dẫn Lab](CHATBOT_LAB_HUONG_DAN_KIEM_THU.md#8-kết-quả-kiểm-chứng-của-đợt-này).
