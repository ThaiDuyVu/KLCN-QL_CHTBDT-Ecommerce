# Giai đoạn 4 — Chương 3 (3.4–3.7): danh sách thay đổi

Đã sửa trực tiếp trong `BaoCaoKhoaLuan_ChinhSua.txt`. Toàn bộ sơ đồ mới nằm ở `docs/bao-cao/so-do/`. Cả 19 file `.puml` đã qua kiểm tra cú pháp của PlantUML 1.2024.7; một số file đã được render thử để xem bố cục.

## 1. ERD: báo cáo cũ so với hệ thống thật

Nguồn sự thật là schema PostgreSQL đang chạy (Flyway V1–V8), đọc từ `information_schema`: **49 bảng, 312 cột, 75 khóa ngoại**.

| Nhóm | ERD báo cáo cũ | Hệ thống thật (bổ sung / khác) |
|---|---|---|
| 01 Người dùng | User, Role, Permission, UserRole, RolePermission, Employee, Customer | **+ refresh_tokens** (mã băm token, xoay vòng, thu hồi); **+ customer_addresses** (sổ địa chỉ); `customers.loyalty_point` |
| 02 Sản phẩm | Category (cây), Brand, Product, ProductVariant, ProductImage, Specification | Biến thể có thêm **cost_price, tracking_type (NONE / SERIAL / IMEI), warranty_months** |
| 03 Kho | Supplier, Warehouse, GoodsReceipt, GoodsReceiptItem, Inventory, SerialNumber, IMEI | **+ goods_receipt_item_devices, + goods_receipt_item_device_imeis** (ghi Serial/IMEI ngay trên phiếu nhập; tạo SerialNumber khi duyệt phiếu); trạng thái Serial có 5 giá trị |
| 04 Đơn hàng | Cart, CartItem, SalesOrder, OrderItem, Payment, InstallmentPayment, InstallmentProvider, Invoice, Shipment | **carts.warehouse_id, orders.warehouse_id** (mua theo chi nhánh); orders có thêm người nhận, tạm tính, phí giao, ghi chú; order_items có cost_price. **Invoice, Shipment: thiết kế sẵn, chưa dùng** |
| 05 Hậu mãi | ReturnRequest, ReturnItem, Warranty, WarrantyTicket, Promotion, PromotionProduct, Review | Bảo hành **tạo tự động khi giao hàng**; trạng thái phiếu bảo hành. **ReturnRequest / ReturnItem, Review: thiết kế sẵn, chưa dùng** |
| 06 Chatbot | ChatbotQnA, ChatbotScript, ChatSession, ChatHistory, MessageFeedback | Tin nhắn chatbot lưu kèm **ảnh chụp bằng chứng** (phục vụ câu hỏi nối tiếp). **QnA, Script, MessageFeedback, chuyển tiếp nhân viên: thiết kế sẵn** |
| 07 RAG | KnowledgeDocument, KnowledgeChunk, ChatMessageRetrieval, **ChatBusinessRetrieval** | **− ChatBusinessRetrieval** (không tồn tại; dữ liệu nghiệp vụ lấy trực tiếp qua API backend); **+ product_semantic_index** (vector ngữ nghĩa sản phẩm) |

## 2. Thay đổi văn bản

| Mục | Thay đổi |
|---|---|
| 3.4.1–3.4.4 | Chú thích Hình 51–54: "Sơ đồ Use-Case hệ thống" → "Sơ đồ hoạt động chức năng" (sai loại sơ đồ) |
| 3.4.9 | "(UC-11_12)" → "(UC-12)", gồm cả tiêu đề, chú thích và mục lục |
| 3.4.10 | **Bỏ** mục sơ đồ hoạt động đổi trả, kèm Hình 60 và dòng mục lục (D3) |
| 3.5.1 | "Sơ đồ tuần chức năng" → "Sơ đồ tuần tự chức năng" |
| 3.5.7 | Chú thích Hình 70 bị sai nội dung ("Tra cứu sản phẩm") → "Kết nối & Xử lý Đơn hàng Trả góp" |
| 3.6.1 | Thêm đoạn giải thích sơ đồ lớp **phân tích** (khái niệm, thuộc tính chính, bội số; khái niệm chưa cài đặt được đánh dấu) + chỗ chèn hình mới |
| 3.6.2 | Hình 76: "Sơ đồ lớp tổng quát" → **"Mô hình dữ liệu (ERD) tổng quát"**. Thêm đoạn: 49 bảng, 75 khóa ngoại, Flyway V1–V8; liệt kê bảng «thiết kế sẵn» và «mới». Bỏ đổi trả khỏi danh sách nhóm |
| 3.6.2.1–7 | Chú thích Hình 77–81, 67: "Sơ đồ lớp phân hệ…" → **"ERD phân hệ…"**. Bổ sung mô tả và quan hệ của các bảng mới ở mục 1. Ghi «thiết kế sẵn» cho Invoice, Shipment, ReturnRequest / ReturnItem, Review, ChatbotQnA / Script, MessageFeedback. Hình 81 (ERD 05) bị dính chung một dòng với đoạn văn → tách ra |
| 3.6.2.7 | **Bỏ** toàn bộ ChatBusinessRetrieval: đoạn mô tả, 6 dòng bảng tham chiếu, 7 dòng quan hệ. Viết lại đoạn mở đầu và đoạn "Về mặt thiết kế"; thêm đoạn và quan hệ ProductSemanticIndex |
| 3.6.2.4–5, 3.6.2.8 | Bỏ các câu coi đổi trả là nghiệp vụ của hệ thống (6 câu) |
| 3.7.2 | Package "Return & Warranty" → **"Warranty"**; mô tả package Chatbot theo thực tế. Hình 82 → "Sơ đồ lớp **thiết kế** tổng quát" |
| 3.7.3 | checkout() thuộc **OrderService** (không phải Cart); bỏ ReturnRequest; mô tả chatbot theo lớp xử lý thật |
| 3.7.4.1–7 | Cập nhật danh sách lớp theo mã nguồn: thêm RefreshToken, CustomerAddress, ProductImage, Specification, GoodsReceipt*, PromotionProduct, ProductSemanticIndex; đổi Shipping → Shipment, Installment → InstallmentPayment / Provider; bỏ "Embedding" (là thuộc tính, không phải lớp). Thêm các lớp dịch vụ. 3.7.4.4 đổi tên thành "Bảo hành (Warranty)" |
| 3.7.5 | Thay ví dụ phương thức không có trong mã (SalesOrder.calculateTotal(), Inventory.reserve(), Warranty.isValid()) bằng mô tả đúng kiến trúc: entity giữ dữ liệu, hành vi nằm ở service (OrderService.checkout() / status(), CartService.selectWarehouse(), OrderStockService.apply(), GoodsReceiptService.updateGoodsReceiptStatus(), WarrantyProvisioningService.createForDeliveredOrder()) |

## 3. Sơ đồ lớp phân tích – thiết kế: trước và sau

| | Báo cáo cũ | Sau Giai đoạn 4 |
|---|---|---|
| Sơ đồ lớp **phân tích** (3.6.1) | Chỉ có một đoạn văn, **không có hình** | **Đã phác thảo** `lop_pt_tong_quat.puml`: 37 khái niệm, 7 nhóm, bội số; đánh dấu 8 khái niệm chưa cài đặt (đơn đặt hàng NCC, phiếu xuất, kiểm kê, điều chuyển, hóa đơn, giao hàng, đánh giá, kịch bản chatbot) |
| Sơ đồ lớp **thiết kế** tổng quát (Hình 82) | Có hình nhưng gồm lớp không tồn tại | **Đã phác thảo** `lop_tk_00_tong_quat.puml`: 47 lớp, 7 package, quan hệ từ khóa ngoại |
| Sơ đồ lớp thiết kế từng phân hệ (Hình 83–90) | Có hình; còn ReturnRequest, Embedding, Shipping, phương thức không có trong mã | **Đã phác thảo** `lop_tk_01` → `lop_tk_07`: thuộc tính và kiểu lấy từ lớp `@Entity`; phương thức lấy từ interface `*Service`; enum trạng thái thật; bội số từ khóa ngoại. Lớp chưa cài đặt có nền cam. Chatbot (`lop_tk_07`) dựng theo mã chatbot-service |

## 4. Danh sách hình (18 dòng đánh dấu trong bản sao)

Mỗi chỗ cần hình mới trong bản sao có dòng `[CẦN VẼ LẠI – ĐÃ PHÁC THẢO: …]` hoặc `[CẦN CHÈN HÌNH – ĐÃ PHÁC THẢO: …]` ghi tên file `.puml`. Khi đưa sang Word: render file đó, thay ảnh cũ, rồi xóa dòng đánh dấu.

| Hình | File |
|---|---|
| (mới) Sơ đồ lớp phân tích tổng quát | `lop_pt_tong_quat.puml` |
| 76 ERD tổng quát | `hinh76_erd_tong_quat.puml` |
| 77, 78, 79, 80, 81 (ERD 05), 67, 81 (ERD 07) | `erd01` → `erd07_*.puml` |
| 82 Sơ đồ lớp thiết kế tổng quát | `lop_tk_00_tong_quat.puml` |
| 83, 84, 85, 86, 87, 88 | `lop_tk_01` → `lop_tk_06_*.puml` |
| 89, 90 | `lop_tk_07_chatbot_rag.puml` (một sơ đồ chung cho cả hai; có thể gộp hai mục thành một hình) |

**Cách render:** dán vào plantuml.com, hoặc dùng plugin PlantUML của VS Code. Có Graphviz thì bố cục gọn nhất. ERD tổng quát 49 bảng khá lớn, nên đặt ở trang khổ ngang như báo cáo đã định.

**Hình giữ nguyên nhưng cần kiểm tra:**
- Sơ đồ hoạt động và sơ đồ tuần tự (Hình 51–75) vẫn giữ theo D2 (kể cả các chức năng chưa cài đặt).
- Cần kiểm tra **nhãn tác nhân** trong ảnh theo tên mới ở Giai đoạn 3.
- **3.5.12** có nội dung gần trùng 3.5.6 (đều là tìm kiếm sản phẩm). Đề xuất gộp hoặc bỏ 3.5.12; chưa sửa, chờ bạn quyết định.

## 5. Để lại cho giai đoạn sau

- **Giai đoạn 6:**
  - Đánh số lại hình. Hiện còn trùng Hình 67 và 81, hình mới tạm ghi "Hình 3.x".
  - Cập nhật mục lục (3.7.4.4 đổi tên).
  - Viết lại tóm tắt khóa luận.
- **Giai đoạn 5:** 3.8 API và Chương 4 dùng cùng tên lớp / service ở đây để thống nhất.
