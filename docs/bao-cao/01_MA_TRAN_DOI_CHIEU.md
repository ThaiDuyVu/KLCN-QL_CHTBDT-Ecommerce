# Giai đoạn 1 — Ma trận đối chiếu báo cáo ↔ hệ thống

**Phạm vi đối chiếu:**
- Báo cáo `backend/docs/BaoCaoKhoaLuan.txt` (bản sao để sửa: `docs/bao-cao/BaoCaoKhoaLuan_ChinhSua.txt`).
- Mã nguồn ngày 27/10/2026: backend Spring Boot (32 controller), web React (`Fontend/src/App.jsx`), app Flutter (`flutter/lib/features`), chatbot FastAPI, schema Flyway V1–V8.

**Ký hiệu:**

| Ký hiệu | Ý nghĩa |
|---|---|
| ✅ | Đã làm, khớp báo cáo |
| 🔄 | Có làm nhưng khác mô tả |
| ⚠️ | Làm một phần |
| ❌ | Báo cáo có, hệ thống chưa có |
| ➕ | Hệ thống có, báo cáo chưa nêu |

**Đề xuất xử lý cho mục ❌:**

| Mã | Cách xử lý |
|---|---|
| **B** | Bỏ khỏi phần mô tả hệ thống |
| **HP** | Chuyển sang "Hướng phát triển" (Chương 5) |
| **CN** | Chỉnh nhẹ project để có (cần bạn duyệt) |
| **GS** | Giữ ở phần khảo sát nghiệp vụ thực tế (nghiệp vụ ngoài đời có, hệ thống không bao phủ) |

---

## 1. Tóm tắt

| Nhóm | ✅ | 🔄 / ⚠️ | ❌ | ➕ |
|---|---|---|---|---|
| Use case hệ thống (19 UC-SYS) | 7 | 4 | 8 | 6 UC mới nên thêm |
| Thực thể dữ liệu (ERD 01–07) | đa số nhóm 01–04 | nhóm 06, 07 | 1 thực thể không tồn tại | 6 bảng mới |
| Công nghệ / môi trường | đa số chương 2 | — | 3 lỗi sai công nghệ | — |

**Kết luận:**
- **Phần lõi thương mại khớp và còn phong phú hơn báo cáo:** danh mục, sản phẩm, biến thể, giỏ hàng, đặt hàng, thanh toán, khuyến mãi, nhập kho kèm Serial/IMEI, bảo hành, báo cáo, phân quyền.
- **Lệch lớn nằm ở:**
  1. **Tác nhân / vai trò**: báo cáo có 7 tác nhân, hệ thống có 4 vai trò.
  2. **Nhóm nghiệp vụ kho mở rộng và hậu mãi chưa làm**: đặt hàng NCC, xuất kho, kiểm kê, điều chuyển, đổi trả, giao vận, hóa đơn VAT, hạng thành viên.
  3. **Chatbot**: báo cáo mô tả RAG + Q&A / kịch bản; hệ thống là kiến trúc lai LLM + kiểm chứng tất định, chỉ có trên web.

---

## 2. Vai trò và tác nhân

| Tác nhân trong báo cáo | Vai trò thật trong hệ thống | Ghi chú |
|---|---|---|
| Quản lý / Admin | **ADMIN** (quản trị), **MANAGER** (quản lý) | Báo cáo gộp hai vai trò. Hệ thống tách: ADMIN có thêm quản lý người dùng và phân quyền; ADMIN và MANAGER quản lý sản phẩm, khuyến mãi, trả góp, xem báo cáo, duyệt phiếu nhập |
| Nhân viên bán hàng | **STAFF** | Xử lý đơn hàng, xem khách hàng |
| Thủ kho / Nhân viên kho | **STAFF** | Lập phiếu nhập, xem tồn kho, Serial/IMEI |
| Nhân viên kỹ thuật / bảo hành | **STAFF** | Tra cứu bảo hành, xử lý phiếu bảo hành |
| Nhân viên giao vận | — (không có) | Trạng thái giao (SHIPPED / DELIVERED) do STAFF cập nhật trên đơn |
| Khách hàng | **CUSTOMER** | ✅ |
| Đơn vị tài chính (đối tác trả góp) | Không phải người dùng hệ thống | Hệ thống lưu `installment_providers`; việc duyệt hồ sơ do ADMIN / MANAGER cập nhật trạng thái thủ công |
| Bộ phận kế toán, NCC | Không phải người dùng hệ thống | Chỉ xuất hiện ở nghiệp vụ khảo sát |

Bằng chứng: role seed ADMIN / MANAGER / STAFF / CUSTOMER; guard `RequireRole` trong `Fontend/src/App.jsx`; `@RequireAnyAuthority` ở các controller.

---

## 3. Phạm vi chức năng (mục 1.3.2 và 3.1.1)

### 3.1 Website

| Chức năng trong báo cáo | Trạng thái | Bằng chứng / thực tế | Đề xuất |
|---|---|---|---|
| Quản lý danh mục, loại sản phẩm, thương hiệu | ✅ | `/api/v1/categories` (cây cha–con), `/api/v1/brands` | Giữ |
| Quản lý sản phẩm (biến thể, thông số, hình ảnh) | ✅ | `/api/v1/products`, `/product-variants`, `/specifications`, `/images`, upload ảnh | Giữ |
| Tìm kiếm, xem chi tiết sản phẩm | ✅ | `/api/v1/products/search/advanced` (lọc danh mục / hãng / giá / RAM / bộ nhớ / kho), `/detail` | Giữ |
| Giỏ hàng, đặt hàng, xử lý đơn hàng | ✅ 🔄 | `/api/cart`, `/api/orders/checkout`. Trạng thái PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED / CANCELLED (`OrderTransitions.java`) | Sửa mô tả theo trạng thái thật |
| Thanh toán | 🔄 | COD, **VNPay sandbox** (IPN, return, đối soát QueryDr), **trả góp nội bộ** (`PaymentMethod`: COD, VNPAY, INSTALLMENT). Không có quẹt thẻ, chuyển khoản riêng | Sửa |
| Xuất hóa đơn | ❌ | Có bảng `invoices` nhưng không có mã sử dụng | HP |
| Theo dõi trạng thái đơn hàng | ✅ | `/api/orders/mine`, trang "Đơn hàng của tôi" | Giữ |
| Quản lý giao hàng | ❌ | Có bảng `shipments` nhưng không dùng; giao hàng = trạng thái SHIPPED / DELIVERED của đơn | HP (mô tả lại thành "cập nhật trạng thái giao") |
| Đổi trả hàng | ❌ | Có bảng `return_requests` / `return_items` nhưng không dùng; UC-13 báo cáo đã ghi "Bỏ" | HP |
| Bảo hành | ✅ ➕ | Bảo hành **tự tạo khi đơn DELIVERED** theo Serial/IMEI; khách tạo phiếu yêu cầu; STAFF xử lý (RECEIVED → IN_PROGRESS → COMPLETED / REJECTED) | Giữ, bổ sung chi tiết |
| Khuyến mãi | 🔄 | Theo % hoặc số tiền, theo thời gian, áp cho sản phẩm (`promotion_products`). **Không có mã giảm giá** | Bỏ "mã giảm giá" |
| Thông tin khách hàng, lịch sử mua hàng | ✅ | `/api/customers` (STAFF xem), đơn / bảo hành / trả góp theo khách | Giữ |
| Hạng thành viên, tích điểm | ❌ | Không có | HP |
| Chatbot tư vấn | 🔄 | Trang `/chatbot` cho khách, Chatbot Lab cho dev. Kiến trúc lai: LLM hiểu câu + kiểm chứng tất định + truy xuất dữ liệu thật. RAG tài liệu chỉ có dữ liệu DEMO | Sửa mô tả |
| Thống kê, báo cáo | ✅ ⚠️ | 8 báo cáo: tổng quan, doanh thu, trạng thái đơn, top sản phẩm, theo kho, thanh toán, phiếu nhập, tồn kho thấp (Recharts) | Giữ |
| Xuất báo cáo / biểu mẫu | ❌ | Không có xuất file | B hoặc HP |
| Quản lý người dùng, phân quyền | ✅ | `/api/users`, `/api/roles/{id}/permissions` (ADMIN) | Giữ |
| Quản lý chatbot và nguồn tri thức | ❌ | Không có giao diện; tài liệu nạp bằng script | HP |
| Nhật ký hệ thống (audit log) | ❌ | Không có | HP |
| Cấu hình hệ thống | ❌ | Không có màn hình cấu hình | B |
| Sao lưu / phục hồi dữ liệu (4.2) | ❌ | Không có | B (hoặc nêu: dùng công cụ của PostgreSQL) |

### 3.2 Mobile App (khách hàng)

| Chức năng trong báo cáo | Trạng thái | Thực tế (`flutter/lib/features`) | Đề xuất |
|---|---|---|---|
| Xem danh sách, chi tiết, tìm kiếm sản phẩm | ✅ | `home_screen`, `product_detail_screen` | Giữ |
| Giỏ hàng, đặt hàng, thanh toán | ✅ 🔄 | `cart`, `checkout` (COD, VNPay); chọn chi nhánh (`warehouse`); thêm địa chỉ | Sửa: không có trả góp trên mobile |
| Theo dõi đơn, lịch sử mua hàng | ✅ | `order_list_screen`, `order_detail_screen` | Giữ |
| Áp dụng mã khuyến mãi | 🔄 | Giá khuyến mãi tự áp, không có mã | Sửa |
| Yêu cầu đổi trả | ❌ | Không có | HP |
| Phản hồi, đánh giá sản phẩm | ❌ | Không có (bảng `reviews` không dùng) | HP |
| Quản lý thông tin cá nhân, địa chỉ | ⚠️ | `profile_screen`, `add_address_screen`. Không có API sửa hồ sơ cá nhân | Sửa |
| Bảo hành | ➕ | Danh sách, chi tiết, tra cứu, phiếu bảo hành | Thêm vào báo cáo |
| Chatbot tư vấn | ❌ | Không có (chatbot chỉ có trên web) | HP hoặc CN |

### 3.3 Hệ thống có nhưng báo cáo chưa nêu (➕)

| Chức năng | Bằng chứng |
|---|---|
| **Chọn chi nhánh mua sắm bắt buộc**; giỏ hàng gắn chi nhánh, đổi chi nhánh phải xóa giỏ | `CartServiceImpl.selectWarehouse`, `BranchSelectionGate` (web) |
| **Tồn kho theo chi nhánh**, giữ chỗ khi đặt hàng, trừ khi giao | `inventory.quantity` / `reserved_quantity`, `OrderStockService` |
| **Phiếu nhập kho có duyệt** (DRAFT → CONFIRMED / CANCELLED) kèm **Serial/IMEI từng thiết bị** | `/api/goods-receipts`, `goods_receipt_item_devices`, `goods_receipt_item_device_imeis` (V3) |
| **Kiểu theo dõi biến thể**: NONE / SERIAL / IMEI | `ProductTrackingType` |
| **Vòng đời Serial**: AVAILABLE → RESERVED → SOLD / RETURNED / DEFECTIVE; tra cứu Serial | `/api/serials`, `SerialStatus` |
| **Gán Serial khi xử lý đơn (PROCESSING)**, chuyển SOLD và **tự tạo bảo hành khi DELIVERED** | `OrderServiceImpl` |
| **Trả góp nội bộ**: nhà cung cấp trả góp, trả trước, duyệt / từ chối thủ công | `/api/installments`, `/api/installment-providers` |
| **VNPay sandbox**: tạo URL ký HMAC-SHA512, IPN, return, đối soát QueryDr, hạn 15 phút | `order/payment`, `backend/docs/vnpay-sandbox.md` |
| **Sổ địa chỉ khách hàng** | `/api/customers/me/addresses` (V8) |
| **Bảo mật**: JWT trong cookie HttpOnly, refresh token xoay vòng, CSRF | `SecurityConfig`, `AuthCookieService` |
| **Chatbot**: so sánh có bảng thông số, combo theo ngân sách, ngữ cảnh nhiều lượt, tra cứu giỏ / đơn / bảo hành của chính khách, từ chối giao dịch; benchmark 205 case (84,5/100) | `chatbot-service/UPDATECHATBOTLOG.md` |
| **Chỉ mục ngữ nghĩa sản phẩm** (pgvector) | V7 `product_semantic_index` |

---

## 4. Use case hệ thống (3.3.2) — UC-SYS-01 → 19

| UC | Tên trong báo cáo | Trạng thái | Thực tế | Đề xuất |
|---|---|---|---|---|
| SYS-01 | Quản lý danh mục sản phẩm và mã Serial/IMEI | ✅ 🔄 | Danh mục / sản phẩm / biến thể: ADMIN, MANAGER. Serial / IMEI được **tạo khi xác nhận phiếu nhập**, không nhập tay ở danh mục | Sửa luồng |
| SYS-02 | Lập và duyệt phiếu nhập kho | ✅ | STAFF lập (DRAFT), ADMIN / MANAGER xác nhận (CONFIRMED) → cộng tồn, tạo Serial / IMEI | Giữ, sửa tác nhân |
| SYS-03 | Xuất kho (xuất bán, xuất bảo hành) | ❌ | Không có phiếu xuất. Xuất bán diễn ra ngầm theo trạng thái đơn (giữ chỗ → gán Serial → trừ tồn khi giao) | B, thay bằng mô tả trong UC xử lý đơn |
| SYS-04 | Thực hiện kiểm kê kho | ❌ | Không có | HP |
| SYS-05 | Cấu hình cảnh báo tồn kho | ⚠️ | Chỉ có báo cáo "tồn kho thấp" với ngưỡng mặc định 5, không có màn hình cấu hình | Sửa thành "Xem cảnh báo tồn kho thấp" hoặc HP |
| SYS-06 | Tra cứu và tìm kiếm sản phẩm | ✅ | Tìm kiếm nâng cao, lọc theo chi nhánh | Giữ |
| SYS-07 | Kết nối & xử lý đơn trả góp | 🔄 | Không kết nối đối tác. Khách chọn nhà cung cấp và số tiền trả trước; ADMIN / MANAGER duyệt / từ chối (PENDING → PROCESSING → APPROVED / REJECTED) | Sửa |
| SYS-08 | In hóa đơn VAT & phiếu giao hàng – lắp đặt | ❌ | Không có | HP |
| SYS-09 | Cập nhật trạng thái giao hàng & lắp đặt | 🔄 | STAFF chuyển đơn SHIPPED → DELIVERED; không có phiếu giao, lắp đặt | Sửa thành "Cập nhật trạng thái đơn hàng" |
| SYS-10 | Quản lý thông tin & hạng thành viên KH | ⚠️ | Xem danh sách / chi tiết khách, đơn, bảo hành, trả góp. Không có hạng thành viên | Sửa (bỏ hạng thành viên → HP) |
| SYS-11 | Tra cứu thông tin bảo hành | ✅ | STAFF tra theo Serial / IMEI; khách xem bảo hành của mình | Giữ |
| SYS-12 | Lập phiếu tiếp nhận & cập nhật trạng thái bảo hành | ✅ 🔄 | **Khách** tạo phiếu (web / mobile), STAFF cập nhật trạng thái | Sửa tác nhân |
| SYS-13 | Xử lý đổi trả / bồi hoàn | ❌ | Không có | B (UC-13 nghiệp vụ đã ghi "Bỏ") |
| SYS-14 | Xem báo cáo quản trị | ✅ 🔄 | 8 báo cáo (ADMIN / MANAGER). Báo cáo ghi "tỷ lệ bảo hành": không có | Sửa danh sách báo cáo |
| SYS-15 | Quản lý người dùng | ✅ | ADMIN: danh sách, sửa, khóa / mở, gán vai trò, sửa quyền của vai trò | Giữ |
| SYS-16 | Đặt hàng online | ✅ 🔄 | Chọn chi nhánh → giỏ → địa chỉ → phương thức thanh toán → đơn PENDING (giữ chỗ tồn kho) | Sửa luồng |
| SYS-17 | Tra cứu lịch sử đơn & trạng thái giao hàng | ✅ 🔄 | Có lịch sử đơn và trạng thái hiện tại; **không có timeline** (báo cáo dựa vào UC-SYS-09) | Sửa |
| SYS-18 | Tương tác với chatbot tư vấn | ✅ 🔄 | Web; luồng thật xem mục 5 | Sửa |
| SYS-19 | Quản lý kịch bản / dữ liệu chatbot | ❌ | Không có giao diện; bảng `chatbot_qna`, `chatbot_scripts` không dùng | HP |

**UC hệ thống nên thêm (➕):**
- Đăng ký / đăng nhập / làm mới phiên.
- Chọn chi nhánh mua sắm.
- Quản lý giỏ hàng.
- Thanh toán VNPay (kèm đối soát).
- Quản lý khuyến mãi.
- Quản lý nhà cung cấp trả góp.
- Quản lý sổ địa chỉ.
- Khách tạo phiếu bảo hành.

Có thể gộp các UC nhỏ vào UC lớn, sẽ chốt ở Giai đoạn 3.

**UC nghiệp vụ (3.3.1, UC-01 → 18):** mô tả hoạt động thực tế của cửa hàng nên đề xuất **GS** (giữ). Các UC sau nằm ngoài phạm vi hệ thống: đặt hàng NCC (UC-01), xuất kho (UC-02), kiểm kê (UC-03), điều chuyển (UC-04), lập lịch giao – lắp đặt (UC-08), hạng thành viên (UC-09 một phần), đổi trả (UC-13), sửa chữa / chuyển bảo hành hãng (UC-14). Phần 3.3.2 sẽ thêm bảng **"UC nghiệp vụ → UC hệ thống hỗ trợ"** để thể hiện ranh giới.

---

## 5. Chatbot (mục 2.5, 3.6.2.6–7, 3.7.4.6–7, 4.3.4)

| Báo cáo mô tả | Thực tế | Trạng thái |
|---|---|---|
| Chatbot RAG trả lời dựa trên nguồn tri thức | Pipeline: guard chỉ-đọc → LLM (`qwen3:4b-instruct`) phân tích thành `QueryPlan` theo JSON Schema → **kiểm chứng tất định theo catalog thật** → truy xuất sản phẩm (lọc có cấu trúc + xếp hạng pgvector), dữ liệu khách (giỏ / đơn / bảo hành), tài liệu (RAG) → câu trả lời **tất định từ bằng chứng**, LLM chỉ dự phòng | 🔄 |
| Quản lý Q&A, kịch bản (`ChatbotQnA`, `ChatbotScript`) | Bảng có, không dùng | ❌ |
| Chuyển tiếp hội thoại cho nhân viên (`employee_id`) | Không có | ❌ |
| Phản hồi tin nhắn (`MessageFeedback`) | Có endpoint đánh giá nhưng chỉ lưu trong bộ nhớ; bảng `message_feedback` không dùng | ⚠️ |
| `ChatMessageRetrieval` | **Có dùng** (dịch vụ chatbot ghi khi truy xuất tài liệu) | ✅ |
| `ChatBusinessRetrieval` | **Không có trong CSDL**; truy xuất dữ liệu nghiệp vụ đi qua API backend và được ghi vào trace dev | ❌ (sửa ERD) |
| Chatbot trên mobile | Không có | ❌ |
| Chroma (5.1.1) | PostgreSQL + pgvector; chương 2 đã viết đúng | Lỗi, đã sửa |
| ➕ Chỉ mục ngữ nghĩa sản phẩm, so sánh, combo, ngữ cảnh nhiều lượt, benchmark | Có | ➕ |

---

## 6. Dữ liệu (ERD 3.6.2.1–7) — đối chiếu với schema Flyway (49 bảng)

| ERD | Thực thể dùng thật | Có bảng nhưng chưa có chức năng | Không có trong CSDL | Bảng có thật nhưng ERD chưa có |
|---|---|---|---|---|
| 01 Người dùng & phân quyền | users, roles, permissions, user_roles, role_permissions, employees, customers | — | — | `refresh_tokens` (V2), `customer_addresses` (V8) |
| 02 Danh mục & sản phẩm | categories, brands, products, product_variants, product_images, specifications | — | — | Cột `tracking_type` của biến thể (V3) |
| 03 NCC & kho | suppliers, warehouses, goods_receipts, goods_receipt_items, inventory, serial_numbers, imei | — | — | `goods_receipt_item_devices`, `goods_receipt_item_device_imeis` (V3) |
| 04 Giỏ hàng & đơn hàng | carts (gắn chi nhánh, V4), cart_items, orders, order_items, payments, installment_payments, installment_providers | **invoices, shipments** | — | — |
| 05 Hậu mãi | warranties, warranty_tickets, promotions, promotion_products | **return_requests, return_items, reviews** | — | — |
| 06 Chatbot & hội thoại | chat_sessions, chat_history | **chatbot_qna, chatbot_scripts, message_feedback** | — | — |
| 07 RAG | knowledge_documents, knowledge_chunks, chat_message_retrievals | — | **ChatBusinessRetrieval** | `product_semantic_index` (V7) |

Tên bảng cụ thể sẽ được đối chiếu từng cột ở Giai đoạn 4. Báo cáo dùng tên lớp (SalesOrder…), CSDL dùng tên bảng (orders…).

---

## 7. Công nghệ, môi trường và các mục khác

| Vị trí | Báo cáo ghi | Thực tế | Xử lý |
|---|---|---|---|
| 3.6.2.8 | "triển khai cơ sở dữ liệu quan hệ trên **SQL Server**" | PostgreSQL 16 + pgvector | **Đã sửa trong bản sao** |
| 5.1.1 | "cơ sở dữ liệu vector **Chroma**" | PostgreSQL + pgvector | **Đã sửa trong bản sao** |
| 4.1 | "(ví dụ: **.NET 8**, Flutter 3.x)" | Spring Boot 4.1.1 / Java 21, React 19 / Vite 8, Flutter (Dart 3.10), Python 3.12 / FastAPI, Ollama | **Đã sửa trong bản sao** (bỏ ví dụ sai); viết đầy đủ ở GĐ5 |
| 3.8 | "access token kèm vai trò… middleware kiểm tra token" | JWT trong cookie HttpOnly + refresh token xoay vòng + CSRF; Spring Security filter | GĐ5 |
| 4.1 | Công cụ: Postman | Không có collection trong repo; kiểm thử tự động bằng JUnit / Testcontainers, Vitest, pytest, flutter_test, benchmark chatbot | GĐ5 |
| 4.5 | Triển khai lên máy chủ thử nghiệm, domain | Chỉ có môi trường local (Docker Compose cho PostgreSQL, script chạy) | GĐ5 (ghi đúng thực tế) |
| 1.3.2, 5.1.2 | "thời gian thực hiện 12 tuần" | Không xác định từ mã | Bạn xác nhận |
| TLTK | Sách "OOP using C#" | Hệ thống không dùng C# | GĐ6 |

---

## 8. Lỗi trình bày (xử lý ở các giai đoạn tương ứng)

- **UC-13** ghi "( Bỏ USE CASE Này )" nhưng vẫn còn: sơ đồ UC hệ thống (Hình 44), sơ đồ hoạt động 3.4.10 (Hình 60). Mục lục 3.4.9 ghi "UC-11_12".
- **Trùng số hình**: Hình 67 (sơ đồ tuần tự kiểm kê và ERD chatbot), Hình 81 (ERD hậu mãi và ERD RAG). Chú thích Hình 70 ghi "Tra cứu và tìm kiếm sản phẩm" nhưng mục là "Trả góp". 3.4.1–3.4.4 chú thích "Sơ đồ Use-Case" thay vì "Sơ đồ hoạt động".
- **Hình ERD 3.6.2** đặt tên "Sơ đồ lớp…" (Hình 76–81).
- **Danh mục hình / bảng trống.** "Bảng 3.x", "Bảng 3.4", "Bảng 4.1", "Hình 4.x" chưa có.
- **Còn nhiều chỗ "[Bổ sung…]"**: 1.4.1, 3.8, 4.1, 4.3.x, 4.4.2, 4.5.
- **Ảnh biểu mẫu 1.4.4** còn chú thích copy từ web ("Mẫu Phiếu Bảo Hành File Excel… 2021", "Cogover Blog").
- **3.5.12** trùng nội dung với 3.5.6.

---

## 9. Quyết định cần bạn chốt (áp dụng cho các giai đoạn sau)

| # | Quyết định | Khuyến nghị |
|---|---|---|
| **D1** | Tác nhân trong UC **hệ thống** | Dùng 4 vai trò thật (Admin, Quản lý, Nhân viên, Khách hàng) + bảng ánh xạ từ tác nhân nghiệp vụ. UC nghiệp vụ giữ tác nhân thực tế |
| **D2** | Các chức năng ❌ lớn: đặt hàng NCC, xuất kho, kiểm kê, điều chuyển, hóa đơn VAT, giao vận – lắp đặt, đổi trả, đánh giá, hạng thành viên, audit log, quản trị chatbot | **HP**: bỏ khỏi phạm vi / yêu cầu / UC hệ thống, đưa vào Hướng phát triển. Giữ nghiệp vụ ở 3.2 / 3.3.1 làm khảo sát (GS) |
| **D3** | UC-13 đổi trả (đã ghi "Bỏ") | Bỏ hẳn UC hệ thống, sơ đồ hoạt động và sơ đồ tuần tự liên quan; giữ mô tả nghiệp vụ đổi trả ở 3.2.4 |
| **D4** | Bảng có trong CSDL nhưng chưa có chức năng (invoices, shipments, returns, reviews, chatbot_qna / scripts, message_feedback) | **Giữ trong ERD** vì schema thật có, nhưng ghi rõ "thiết kế sẵn cho giai đoạn mở rộng". Lựa chọn khác: lược khỏi ERD |
| **D5** | Chatbot trên mobile | **HP** (báo cáo sửa thành chỉ có trên web). Lựa chọn **CN**: thêm màn hình chat vào app gọi API chatbot sẵn có, khối lượng vừa |
| **D6** | "Xuất báo cáo / biểu mẫu", "Cấu hình hệ thống", "Sao lưu / phục hồi" | **B**: bỏ khỏi yêu cầu và 4.2 (không đưa vào hướng phát triển nếu không cần) |
| **D7** | "Cảnh báo tồn kho" (SYS-05) | Sửa thành "Xem báo cáo tồn kho thấp" (đã có), bỏ phần "cấu hình" |
| **D8** | Các chức năng ➕ chưa có trong báo cáo | **Thêm tất cả** vào phạm vi, yêu cầu, UC, sơ đồ. Đây là điểm mạnh của đề tài |
| **D9** | Thời gian thực hiện "12 tuần" | Bạn xác nhận số tuần thực tế |

Sau khi bạn chốt D1–D9, Giai đoạn 2 sẽ sửa Chương 1 trực tiếp trong bản sao.

### Quyết định đã chốt (27/10/2026)

| # | Quyết định |
|---|---|
| **D2 (đã thay khuyến nghị)** | **Giữ trong báo cáo** các chức năng đã phân tích, thiết kế nhưng chưa xây dựng (theo ý kiến giáo viên hướng dẫn: khi được hỏi, trả lời là chưa đủ thời gian triển khai đầy đủ). Chương 3 giữ nguyên phạm vi phân tích và thiết kế. **Chương 4 và 5 phải ghi rõ mức độ hoàn thành**: chức năng nào đã cài đặt, chức năng nào thuộc thiết kế nhưng chưa cài đặt. Như vậy báo cáo không khẳng định sai là "đã làm" |
| **D3** | **Bỏ hẳn đổi trả** (UC-13, UC-SYS-13, sơ đồ hoạt động 3.4.10 và các phần liên quan), vì nghiệp vụ sau bán đã có bảo hành |
| **D4** | Theo D2: giữ các bảng thiết kế sẵn trong ERD |
| **D6** | **Xuất báo cáo**: ghi vào backlog để xây dựng (BL-01). **Cấu hình hệ thống, sao lưu / phục hồi**: bỏ khỏi báo cáo |
| **D7** | **Cảnh báo tồn kho**: giữ UC-SYS-05 trong báo cáo, ghi vào backlog việc sửa hệ thống cho khớp (BL-02) |
| **D1** | **Giữ tác nhân theo báo cáo** (phương án B ở `01b`). Báo cáo chỉ sửa các mâu thuẫn nội bộ về tên gọi; **hệ thống sẽ được sửa** để có các vai trò tương ứng (S2 trong `KE_HOACH_SUA_HE_THONG.md`) |
| **D5** | **Giữ chatbot trên mobile** trong báo cáo; hệ thống bổ sung (S5.1) |
| **D8** | Chức năng hệ thống có thêm: **nhỏ thì không đưa vào báo cáo**; **lớn (cả một nghiệp vụ) thì ghi chú trên bản sao** bằng dòng `[GHI CHÚ – CHỨC NĂNG HỆ THỐNG CHƯA CÓ TRONG BÁO CÁO: …]` |
| **D9** | Số tuần thực hiện: không quan trọng, giữ nguyên |

Việc sửa hệ thống được theo dõi ở `BACKLOG_CHINH_SUA_HE_THONG.md`.

---

## 10. Thay đổi đã áp vào bản sao ở Giai đoạn 1

Chỉ sửa các lỗi công nghệ sai rõ ràng, không phụ thuộc quyết định. Đúng 4 dòng thay đổi so với bản gốc (giữ nguyên định dạng UTF-8 BOM, CRLF):
1. Tóm tắt khóa luận (dòng 130) và 5.1.1 (dòng 2657): "cơ sở dữ liệu vector Chroma" → "PostgreSQL với extension pgvector".
2. 3.6.2.8 (dòng 2463): "SQL Server" → "PostgreSQL".
3. 4.1 (dòng 2621): bỏ ví dụ "(ví dụ: .NET 8, Flutter 3.x)" trong ghi chú bổ sung.

Các chỗ nhắc Chroma ở chương 2 là **phương án so sánh không dùng**, đúng ý, nên giữ nguyên.

**Bổ sung vào danh sách cần rà:** phần **"TÓM TẮT KHÓA LUẬN"** (dòng 128–131) cũng liệt kê chức năng chưa có (xuất hóa đơn, quản lý giao hàng, đổi trả…). Phần này sẽ viết lại ở Giai đoạn 6, sau khi nội dung các chương đã chốt.
