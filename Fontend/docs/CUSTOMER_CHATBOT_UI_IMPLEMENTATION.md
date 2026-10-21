# Giao diện chatbot khách hàng — "Trợ lý AI"

> [C2C] TASK_ID: c2c_customer_chatbot_storefront_ui · ITERATION: 1. Báo cáo do coding agent viết, chưa được review độc lập.
> Phạm vi: frontend dành cho CUSTOMER. Không đổi backend, schema, logic AI. Giao diện hoàn chỉnh **không** đồng nghĩa chất lượng hội thoại AI đã được nghiệm thu.

## 1. Workspace / branch / HEAD

`QL_CHTBDT_Ecommerce` (`KLCN-QL_CHTBDT-Ecommerce-main`), branch `dev/thai-duy-vu`, HEAD `df64381` (Phase 1 commit; `origin/main` có merge `e10669d` cùng tree). Không commit, không push.

## 2. Git status

**Ban đầu** (thay đổi có sẵn, không thuộc nhiệm vụ này, không bị chạm): hotfix giỏ hàng Phase 2 chưa commit (`chatbot-service/app/features/chat/service.py`, `.../customer_context/service.py`, `tests/test_cart_hotfix.py`), báo cáo/evidence Phase 2, và thư mục `chatbot-service/debug/uat-diagnostics/` không do agent tạo. `Fontend/` sạch.

**Cuối:** như trên, cộng các thay đổi frontend ở mục 3. `git diff --check` sạch.

## 3. File thay đổi

| File | Loại | Nội dung |
|---|---|---|
| `src/App.jsx` | sửa (+3) | Lazy `CustomerChatbotPage`, route `chatbot` trong `AppLayout → RequireAuth → RequireRole[CUSTOMER]`, độc lập cờ Chatbot Lab |
| `src/components/layout/AppLayout.jsx` | sửa (+2) | Mục `{ to: '/chatbot', label: 'Trợ lý AI' }` trong `storefrontNavigation` |
| `src/features/chatbot/CustomerChatbotPage.jsx` | mới | Trang, vòng đời session/tin nhắn |
| `src/features/chatbot/CustomerChatParts.jsx` | mới | `CustomerProductCard`, `SourceList`, `MessageBubble` |
| `src/features/chatbot/customerChat.js` | mới | Helper thuần: URL an toàn, nhãn thời gian, ánh xạ lỗi, ghép lịch sử |
| `src/features/chatbot/CustomerChatbotPage.css` | mới | Giao diện theo token storefront |
| `src/features/chatbot/CustomerChatbotPage.test.jsx` | mới | 24 test component |
| `src/features/chatbot/CustomerChatbotRouting.test.jsx` | mới | 8 test routing/điều hướng qua `App` thật |
| `docs/CUSTOMER_CHATBOT_UI_IMPLEMENTATION.md`, `docs/customer-chatbot-evidence/` | mới | Báo cáo, output lệnh, ảnh, script browser |

Không sửa: `chatbotApi.js` (dùng lại nguyên trạng), `ChatbotLabPage.jsx`, `TracePanel`, `apiClient.js`, `vite.config.js`, backend Python/Java, schema. Không thêm thư viện.

## 4. Kiến trúc UI

- `CustomerChatbotPage` lấy `user` từ `useAuth` và render `ChatWorkspace` với `key={user.userId}`: đổi tài khoản là bỏ toàn bộ state trong bộ nhớ.
- `ChatWorkspace` giữ state: phiên đang chọn, danh sách phiên, tin nhắn, bản nháp, trạng thái chờ/đồng bộ/đóng. Mọi cập nhật bất đồng bộ đều kiểm `version` token (cách làm tương tự Lab nhưng viết lại cho trải nghiệm khách hàng).
- Bố cục: cột trái danh sách cuộc trò chuyện; cột phải tiêu đề, transcript (`role="log"`), banner trạng thái, ô nhập. Tái dùng `PriceDisplay`, `ProductImage`, `money`; **không** dùng `StorefrontProductCard` vì thẻ đó có nút thêm giỏ.
- Không có TracePanel, kịch bản kiểm thử, runtime status, QueryPlan, JSON debug, trace ID. Trang không gọi `/status` hay trace endpoint (có test).

## 5. Route và điều hướng

- `/chatbot`, lazy chunk riêng (`CustomerChatbotPage-*.js` 16.6 kB, `.css` 6.0 kB).
- Tab "Trợ lý AI" trong `nav[aria-label="Điều hướng cửa hàng"]`, `NavLink` nên có lớp `active` và `aria-current="page"`; không có `target`, mở trong cùng tab; đi được bằng bàn phím. Ở ≤700px thanh điều hướng storefront sẵn có đã cuộn ngang nên mục mới không làm vỡ layout.
- Khách chưa đăng nhập thấy tab; bấm vào → `/login`, đăng nhập xong quay lại `/chatbot` qua `state.from` hiện có.
- `/chatbot-lab` giữ nguyên điều kiện `DEV || VITE_CHATBOT_LAB_ENABLED`; `/chatbot` không phụ thuộc cờ này.

## 6. Authentication / authorization

- Route bảo vệ bằng `RequireAuth` + `RequireRole roles={[ROLES.CUSTOMER]}`; STAFF/MANAGER/ADMIN vào thẳng `/chatbot` → `/forbidden` và trang không gọi API chat (có test). Nhân viên dùng layout quản trị nên không thấy tab.
- Gọi API qua `apiClient` (cookie `credentials: include`, CSRF cho POST). Không lưu JWT ở storage. Backend vẫn quyết định quyền: lỗi 401/403/404 không hiển thị dữ liệu đã cache.
- Đăng xuất: route guard đưa về login, component bị huỷ, transcript không còn trên trang (có test).

## 7. Tích hợp API

| Thao tác | Endpoint (qua `chatbotApi`) |
|---|---|
| Danh sách phiên | `GET /api/v1/chat/sessions?page=n&size=20` |
| Trạng thái phiên | `GET /api/v1/chat/sessions/{id}` (404 = chưa được tạo) |
| Lịch sử | `GET /api/v1/chat/sessions/{id}/messages[?before=]` |
| Gửi | `POST /api/v1/chat/sessions/{id}/messages` `{message}` |
| Kết thúc | `POST /api/v1/chat/sessions/{id}/close` |

- Proxy Vite giữ thứ tự `/api/v1/chat` → FastAPI trước `/api` → Spring (không đổi).
- Gửi: trim, không gửi rỗng, tối đa 4000 ký tự, chặn double-submit bằng cả trạng thái `busy` và một ref đồng bộ (bắt được hai lần Enter trong cùng tick). `AbortController` riêng với hạn chờ **180 giây** thay cho mặc định 15 giây của `apiClient`, vì Qwen + retrieval có thể chậm hợp lệ.
- Enter gửi, Shift+Enter xuống dòng; không gửi khi đang gõ dấu bằng IME (`isComposing`, quan trọng với Telex/VNI).
- Thông báo lỗi cho khách: 401 (kèm nút "Đăng nhập lại"), 403, 404, 409 (đang xử lý câu khác hoặc đã kết thúc; tải lại trạng thái phiên), 400/422, 5xx (không hiển thị chi tiết kỹ thuật từ backend, không khẳng định đã có câu trả lời), mất mạng, hết thời gian chờ. Bản nháp luôn được giữ khi lỗi.
- Không feedback Đúng/Cần sửa ở trang khách hàng: endpoint feedback hiện chỉ lưu RAM (`feedback_storage: memory`), không nên hứa với khách.

## 8. Nhiều cuộc trò chuyện

- "Cuộc trò chuyện mới": tạo UUID ở client, reset transcript, không xoá phiên cũ, **không** gọi backend. Phiên chỉ được tạo khi gửi tin đầu tiên (hợp đồng hiện tại). ID mới tạo và chưa gửi không bị tra cứu (tránh 404 vô ích; đúng cả dưới React StrictMode).
- Danh sách phiên: `sessions(page)`, nút "Tải thêm cuộc trò chuyện" khi trang đủ 20. Nhãn **chỉ** dựa trên `created_at` thật: "Cuộc trò chuyện · 21/10/2026 14:33"; trạng thái "Đang mở"/"Đã kết thúc". Không phát minh tiêu đề hay API đổi tên.
- Chọn phiên: đổi `sessionId`, tải lịch sử của đúng phiên đó, tăng `version` để mọi phản hồi đến muộn của phiên cũ bị bỏ (không append vào phiên mới). Request cũ vẫn chạy tiếp trên server, chỉ cập nhật UI bị bỏ.
- Kết thúc: hai bước xác nhận → `close`; sau đó không gửi được, vẫn đọc lịch sử, có nút bắt đầu cuộc trò chuyện mới. Không có chức năng xoá.

## 9. Lịch sử / refresh / retry an toàn

- `sessionStorage` chỉ lưu **ID phiên đang chọn**, khoá `storefront-chatbot.session.v1.<userId>` (tách theo user, không dùng localStorage, không lưu nội dung). Refresh đọc lại phiên + lịch sử từ API.
- Lỗi mơ hồ (timeout, mất mạng) → đọc lại lịch sử trước khi cho gửi lại:
  - lịch sử kết thúc bằng đúng câu hỏi + câu trả lời → coi là đã hoàn tất, hiển thị theo lịch sử, xoá bản nháp, **không** gửi lại;
  - ngược lại → khoá ô gửi, hiện "Đồng bộ lịch sử"; chỉ mở lại sau khi đồng bộ thành công.
- Lỗi HTTP có phản hồi rõ ràng + lịch sử 404 → phiên chưa được tạo, bỏ dòng tạm.
- Không bao giờ tự POST lại.

## 10. ProductCard / nguồn / gợi ý

- ProductCard: tên (link), ảnh (chỉ đường dẫn gốc `/…` hoặc `http(s)`; thiếu/lỗi → ô "Chưa có ảnh"), giá VNĐ vi-VN; có giá gốc lớn hơn thì hiển thị gạch ngang + mức giảm; thiếu giá → "Chưa có giá" (không bao giờ "0 ₫"); RAM/bộ nhớ/màu, bảo hành, SKU, lý do; tồn kho `null` → "Chưa xác định", `0` → "hết hàng". Nút "Xem chi tiết" → `/products/{productId}`. Không có nút mua/thêm giỏ.
- Nguồn: chỉ tạo link khi URL là `http`/`https` (mở tab mới, `rel="noopener noreferrer"`); còn lại hiển thị tên.
- Văn bản trợ lý render dạng text thuần (`white-space: pre-wrap`); không dùng `dangerouslySetInnerHTML` (test với `<script>` trong phản hồi).
- `suggested_questions` của backend hiển thị dạng chip "Gợi ý câu hỏi tiếp theo"; câu mở đầu ở trạng thái rỗng là phím tắt giao diện, ghi rõ "Bạn có thể bắt đầu với". Nhấn chip chỉ điền vào ô nhập (không tự gửi), bị khoá khi đang chờ/đã kết thúc.
- Lịch sử chỉ có văn bản: card/nguồn chỉ hiện ở lượt nhận trực tiếp trong lần mở trang; khi đọc lại lịch sử, card chỉ được gắn lại vào đúng câu trả lời cùng nội dung đã nhận trong phiên trang. Không dựng card từ văn bản cũ.

## 11. Mobile / accessibility

- ≤900px: một cột; danh sách phiên ẩn sau nút "Danh sách trò chuyện" (`aria-expanded`, `aria-controls`). ≤600px: transcript theo luồng trang (không khung cao cố định), nút gửi rộng toàn màn hình.
- Không dùng chiều cao cố định trên mobile. Grid product card `minmax(min(220px, 100%), 1fr)`.
- `role="log" aria-live="polite"`, `role="status"` cho đang tải/đang trả lời, `role="alert"` cho lỗi; nhãn ẩn cho ô nhập + mô tả phím tắt; focus-visible theo storefront. Phần đầu trang dùng `div` (không tạo landmark `banner` thứ hai).

## 12. Tests và lệnh chạy

Node 22.23.3 (`.local/tools`), trong `Fontend/`:

| Lệnh | Kết quả | Output |
|---|---|---|
| `npm test` | 9 file, **91 passed**, 0 failed, exit 0 (59 có sẵn + 24 component + 8 routing) | [test.txt](customer-chatbot-evidence/test.txt) |
| `npm run lint` (`--max-warnings 0`) | exit 0 | [lint.txt](customer-chatbot-evidence/lint.txt) |
| `npm run build` | exit 0 | [build.txt](customer-chatbot-evidence/build.txt) |

Bao phủ: tab/active/keyboard/guest login/STAFF·MANAGER·ADMIN bị chặn/logout/Lab tách biệt; empty state; nhiều phiên; chọn phiên; refresh; tạo mới (không gọi backend, kể cả StrictMode); phân trang phiên; tin cũ hơn; đóng phiên; tách tài khoản; gửi 1 lần với double-click và hai Enter cùng tick; thứ tự USER/ASSISTANT; card đầy đủ; dữ liệu thiếu (giá/tồn kho/ảnh); `<script>` không thực thi; nguồn an toàn/không an toàn; gợi ý; rỗng; Enter/Shift+Enter; 401/403/404/409/503; timeout 180s (fake timers); mất mạng đã hoàn tất vs chưa xác minh; Enter không vượt qua bước đồng bộ; phản hồi muộn sau khi đổi phiên; nhãn/live region/nút mobile.

Kiểm tra đột biến (sửa tạm code rồi khôi phục): bỏ kiểm token stale → test phản hồi muộn FAIL; tăng timeout ×10 → test timeout FAIL; bỏ guard `needsSync`/in-flight → hai test FAIL. Hai test mới **phát hiện lỗi thật** trong bản đầu: guard in-flight dùng token `0` (falsy) nên không chặn hai Enter cùng tick; đã sửa thành `!== null`. Lần chạy trình duyệt thứ hai phát hiện initializer `useState` có side effect gây tra cứu thừa dưới StrictMode; đã sửa và thêm test StrictMode.

## 13. Kiểm thử trình duyệt thật

`docs/customer-chatbot-evidence/browser_e2e.py`: Chrome thật → Vite 5173 → Spring 8080 + FastAPI 8090 (Qwen thật, PostgreSQL), tài khoản seed `seed.customer1`. Kết quả **11/11 PASS**, 0 page error ([browser_e2e.json](customer-chatbot-evidence/browser_e2e.json)).

| Bước | Kết quả | Ảnh |
|---|---|---|
| 1–4 Mở site → tab Trợ lý AI → login → quay lại `/chatbot` cùng tab, có header/footer | PASS | `01-chatbot-page.png` |
| 5–6 "Tìm laptop dưới 20 triệu." → HTTP 200, 2 card (ASUS Vivobook 15 12.990.000 ₫, Dell Inspiron 15 15.990.000 ₫), mọi giá ≤ 20M | PASS | `05-laptop-cards.png` |
| 7–8 "Xem chi tiết" → trang chi tiết có đúng tên; quay lại, lịch sử đọc lại từ API | PASS | `07-product-detail.png` |
| 9–10 Cuộc trò chuyện thứ hai: "Giỏ hàng của tôi." → trả giỏ thật; không lẫn tin phiên một | PASS | `09-second-conversation.png` |
| 11 Mở lại cuộc trò chuyện thứ nhất | PASS | `11-first-again.png` |
| 12 Refresh → đúng phiên, lịch sử dạng text (không có card) | PASS | `12-after-refresh.png` |
| 14 Mất mạng có kiểm soát (route abort tại trình duyệt, request không tới server) → khoá gửi, giữ nháp, "Đồng bộ lịch sử" → gửi lại 1 lần thành công | PASS (CONTROLLED_BROWSER_FAULT) | `14-network-failure.png` |
| 13 Kết thúc phiên → ô nhập bị khoá, lịch sử vẫn đọc được | PASS | `13-closed.png` |
| 15 390 / 768 / 1440 px: tràn ngang 0px, ô nhập + nút gửi hiển thị, nút danh sách chỉ ở ≤900px | PASS | `15-*.png` |

HTTP lỗi ghi nhận trong lần chạy cuối: 2× 401 `auth/me`/`auth/refresh` lúc chưa đăng nhập; 3× 404 cho đúng phiên có POST bị chặn có chủ đích (đối chiếu + đồng bộ). Timeout thật 180s không chạy trên trình duyệt (chỉ có test fake timer).

## 14. Runtime dependencies

`bash scripts/start.sh`: PostgreSQL (Docker), Ollama (`qwen3:4b-instruct`, `qwen3-embedding:0.6b`), Spring 8080, FastAPI 8090 (`chatbot-service/.env`: AI thật, Product Advisor real, hội thoại PostgreSQL), Vite 5173. Bản build production cần reverse proxy cùng origin cho `/api/v1/chat` → FastAPI và `/api` → Spring (repo chưa có cấu hình deploy production).

## 15. Giới hạn backend (không thuộc UI)

- Lịch sử chỉ trả văn bản; card/nguồn/gợi ý của lượt cũ không khôi phục được. **BACKEND_CONTRACT_REQUIRED** nếu cần lịch sử có card (đề xuất tách riêng).
- Phiên không có tiêu đề; không có API đổi tên/xoá.
- Không có idempotency key cho POST: sau lỗi mơ hồ, UI chỉ có thể đối chiếu lịch sử, không bảo đảm exactly-once (DR-08).
- Tồn kho/giá trong câu trả lời theo kho cấu hình của chatbot, không theo chi nhánh khách đang chọn (CB-12/DR-07); trang ghi chú điều này.
- Feedback chỉ lưu RAM, nên không đưa lên trang khách hàng.
- Chất lượng hội thoại còn các giới hạn đã nêu ở Phase 2 (F-01 mã đơn không kèm "đơn hàng"; câu giá chờ DR-01/02 được hỏi lại; RAG DEMO; multi-intent). UI không sửa và không che những điểm này.

## 16. Hướng dẫn sử dụng

1. Chạy `bash scripts/start.sh` (hoặc giữ stack dev đang chạy).
2. Mở `http://localhost:5173`, bấm **Trợ lý AI** trên thanh điều hướng, đăng nhập tài khoản CUSTOMER (ví dụ `seed.customer1`; mật khẩu seed trong `chatbot-service/docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md`).
3. Gõ câu hỏi, Enter để gửi. Dùng "Cuộc trò chuyện mới" để tách chủ đề; chọn cuộc trò chuyện cũ ở cột trái (hoặc nút "Danh sách trò chuyện" trên điện thoại).
4. Nếu thấy "Đồng bộ lịch sử", bấm nút đó trước khi hỏi lại.

## 17. Rủi ro còn lại

- Danh sách phiên cập nhật bất đồng bộ sau lượt đầu tiên; trong khoảnh khắc ngắn mục tạm "Cuộc trò chuyện mới" vẫn hiển thị (thấy trong ảnh `05-laptop-cards.png`).
- Nếu server vẫn đang xử lý khi khách bấm "Đồng bộ lịch sử" quá sớm, lịch sử có thể chỉ có câu hỏi; gửi lại cùng phiên khi lượt cũ chưa xong sẽ nhận 409 (lease backend) chứ không tạo trùng, nhưng sau khi lượt cũ xong thì gửi lại là một lượt mới theo chủ ý khách.
- Đổi tài khoản trong cùng tab: state bị huỷ theo `userId`; ID phiên trong `sessionStorage` tách theo user. Chưa kiểm đa tab.
- Kiểm thử trình duyệt chạy trên DB dev (chỉ có tài khoản seed); các lần chạy đã thêm ~15 phiên chat cho `seed.customer1`.
- Chưa kiểm trên bản build production phía sau reverse proxy, Safari/Firefox, hay trình đọc màn hình thật.

## 18. Không thực hiện

Floating chat widget; thêm/sửa giỏ, đặt hàng, thanh toán qua chatbot; feedback trên trang khách hàng; API mới (tiêu đề phiên, xoá phiên, lịch sử có card); thay đổi backend/AI/schema; thay đổi Chatbot Lab; cấu hình reverse proxy production; tuyên bố PRODUCTION_READY.
