# Đánh giá hệ thống theo Đề cương CNTT-KLCN054

- **Ngày đánh giá:** 06/11/2026
- **Nhánh:** `feature/customer-registration-checkout-ux` (HEAD `2bf8dbe`)
- **Phương pháp:** đọc tĩnh mã nguồn: Flyway migration V1–V9, backend Spring Boot, web React (`Fontend/`), Flutter (`flutter/`), `chatbot-service/`. Không chạy ứng dụng, nên điểm phản ánh những gì **có trong mã**, chưa kiểm tra chất lượng giao diện khi chạy.
- **Nguyên tắc chấm:**
  - Chỉ chấm những gì đề cương yêu cầu. Phần làm thêm ngoài đề cương **không được cộng điểm**.
  - Nghiệp vụ còn lủng hoặc còn nghi ngờ thì **trừ điểm**.
  - Tầng CSDL được tính trọng số cao vì là nền tảng của toàn hệ thống.
- **Phạm vi:** chỉ chấm phần sản phẩm (CSDL + chức năng). Các mục báo cáo, slide, thái độ (mục 7–10 của thang điểm) không chấm ở đây.

---

## Chấm lại lần 2: sau P0–P3 (06/11/2026)

**Phạm vi:** commit `005ce81` (P0–P2) cộng phần P3 chưa commit (V10–V13). Dùng cùng thang và trọng số với lần 1.

**Lưu ý về độ tin cậy:**
- Phần lớn thay đổi do chính người chấm thực hiện, nên lần này chấm khắt khe hơn. Phần chưa kiểm chứng được (mobile) vẫn bị trừ.
- Bằng chứng: backend 336/336 test, web 206/206 test, lint sạch, build thành công. Mọi test backend chạy trên PostgreSQL 16 thật.
- Flutter **chưa được biên dịch hay chạy** lần nào.

| # | Hạng mục | Trọng số | Lần 1 | **Lần 2** |
|---|---|---:|---:|---:|
| A | Tầng CSDL | 2.00 | 0.65 | **1.55** |
| B | CN01 – Loại SP/SP, Khách hàng, Khuyến mãi | 1.50 | 1.10 | **1.32** |
| C | CN02 – Đặt/hủy, Thanh toán/Hóa đơn, Giao hàng/Đổi trả | 2.50 | 1.00 | **2.15** |
| D | CN03 – Lịch sử mua, Bảo hành, Chatbot | 1.50 | 1.20 | **1.30** |
| E | CN04 – Biểu mẫu, Thống kê | 0.75 | 0.40 | **0.70** |
| F | Cơ bản – Đăng nhập, Phân quyền, Backup & Restore | 0.75 | 0.40 | **0.71** |
| G | Ứng dụng di động | 1.00 | 0.55 | **0.70** |
| | **Tổng** | **10.00** | **5.30** | **8.43** |

> **Cập nhật 06/11/2026: đã bổ sung đổi hàng (V14).** Lỗ hổng "chỉ có trả hàng hoàn tiền" ở mục C đã được khắc phục. Mục C tăng từ 2.15 lên khoảng **2.25**, tổng tăng từ 8.43 lên khoảng **8.53**.

> Mobile (G) được chấm thấp vì **chưa chạy được lần nào**: nếu có lỗi biên dịch thì cả app không demo được. Khi `flutter analyze` và `flutter test` đạt, G lên khoảng 0.92 và **tổng lên khoảng 8.65**.

### A. CSDL: 1.55 / 2.00 (trừ 0.45)

| # | Lần 1 | Hiện trạng | Còn trừ |
|---|---|---|---:|
| A1 | 7 bảng không dùng | `shipments`, `invoices`, `return_requests`, `return_items`, `reviews` đã dùng. **`chatbot_qna`, `chatbot_scripts` vẫn chưa dùng** | 0.05 |
| A2 | Thiếu CHECK trạng thái | Đã có ở V10 (trừ `carts.status` và bảng chatbot, có lý do ghi trong migration) | 0 |
| A3 | Không có lịch sử trạng thái | Đã có ở V11 | 0 |
| A4 | Không bán cho khách vãng lai | Đã có ở V13 | 0 |
| A5 | Nhân viên không thuộc chi nhánh | Đã có ở V12. **Phiếu nhập kho và phiếu bảo hành chưa lọc theo chi nhánh**. Chi nhánh vẫn dùng chung bảng `warehouses` | 0.03 |
| A6 | Hoàn tiền không mô hình hóa | Đã có `REFUNDED`. **Chưa có bảng giao dịch hoàn tiền** (ngày, hình thức, người hoàn); hoàn một phần chỉ nằm ở `return_requests.refund_amount` | 0.05 |
| A7 | Khuyến mãi đơn giản | Mới chặn giảm % > 100. **Chưa có voucher, đơn tối thiểu, trần tiền giảm** (V14 chưa làm) | 0.08 |
| A8 | `user_roles` N–N nhưng UNIQUE | Không đổi | 0.05 |
| A9 | Bảo hành bắt buộc serial | Không đổi: hàng không quản lý serial không bảo hành được | 0.05 |
| A10 | Hóa đơn không có VAT | Không đổi | 0.05 |
| A11 | Tên danh mục không unique, sản phẩm không có mã, giỏ không unique, điểm tích lũy bỏ trống | Không đổi | 0.10 |
| | | | **0.46 ≈ 0.45** |

### B. Chức năng 01: 1.32 / 1.50

- **Loại SP, sản phẩm: 0.45/0.5.** Không đổi.
- **Khách hàng: 0.47/0.5.** Nhân viên tạo, sửa khách và khách vãng lai đã có. Trừ nhẹ vì điểm tích lũy vẫn không hoạt động.
- **Khuyến mãi: 0.40/0.5.** Mobile đã hiển thị. Vẫn trừ vì mô hình khuyến mãi quá đơn giản (xem A7).

### C. Chức năng 02: 2.15 / 2.50

- **Đặt, hủy đơn: 0.47/0.5.**
  - Đã có: tự hủy đơn VNPAY quá hạn, hủy đơn đã thanh toán (bắt buộc lý do), lịch sử trạng thái.
- **Mua hàng, thanh toán, hóa đơn: 0.88/1.0.**
  - Đã có: COD chuyển `PAID` khi giao, hóa đơn có trang in, bán tại quầy thu tiền mặt.
  - Còn trừ:
    - hóa đơn không có VAT;
    - đơn đã giao trước khi có chức năng hóa đơn không có hóa đơn;
    - hoàn tiền một phần không phản ánh trên `payments`.
- **Trạng thái đơn, giao hàng, đổi trả: 0.80/1.0.**
  - Đã có: giao hàng có nhân viên, mã vận đơn, ghi nhận giao thất bại; đổi trả đủ 4 bước.
  - Còn trừ:
    - **Chỉ có trả hàng hoàn tiền, chưa có đổi sang sản phẩm khác.** Đề cương ghi "đổi trả".
    - **Phí giao hàng luôn bằng 0** ([OrderServiceImpl.java:113](../../backend/src/main/java/com/example/backend/order/service/OrderServiceImpl.java#L113)).
    - Đơn online vẫn để hệ thống tự chọn serial ở bước "Đang xử lý"; thủ kho không quét serial. Riêng bán tại quầy thì đã quét được.

### D. Chức năng 03: 1.30 / 1.50

- **Lịch sử mua hàng: 0.36/0.375.**
- **Bảo hành: 0.32/0.375.**
  - Đã có: nhân viên lập phiếu tại quầy; bảo hành chuyển `VOIDED` khi thiết bị bị trả lại.
  - Còn trừ: hàng không có serial không bảo hành được; phiếu bảo hành không có chi phí sửa chữa.
- **Chatbot: 0.62/0.75.**
  - Đã có: chatbot trên mobile, nhưng chưa chạy thử và cần trỏ app qua gateway.
  - Còn trừ: chưa có màn hình quản trị nội dung tư vấn (`chatbot_qna`).

### E. Chức năng 04: 0.70 / 0.75

- **Biểu mẫu: 0.34/0.375.** Có 6 biểu mẫu in A4. Trừ nhẹ vì chưa đối chiếu với các biểu mẫu nhóm thu thập ở mục 1 của đề cương.
- **Thống kê: 0.36/0.375.** Đã có thống kê theo loại sản phẩm, doanh thu đã trừ phần hoàn tiền, và xuất CSV.

### F. Chức năng cơ bản: 0.71 / 0.75

- **Đăng nhập, đăng xuất: 0.25.**
- **Phân quyền: 0.23.** Đã có tạo nhân viên và phạm vi chi nhánh. Trừ vì trang quản lý người dùng chỉ Admin vào được, và phiếu nhập/bảo hành chưa lọc theo chi nhánh.
- **Backup & Restore: 0.23.** Đã khôi phục thật thành công trong test. Trừ vì phải cấu hình chạy trong container CSDL mới tránh được lệch phiên bản `pg_dump`.

### G. Mobile: 0.70 / 1.00

Có đủ các yêu cầu ở mục 6 đề cương: xem và tìm sản phẩm, đặt hàng, thanh toán, trạng thái đơn, khuyến mãi, đổi trả, đánh giá, lịch sử mua, cùng chatbot. Nhưng toàn bộ phần sửa ở P0–P3 **chưa được biên dịch hay chạy** (máy không có Flutter SDK).

### Việc nên làm để lấy thêm điểm (theo thứ tự lợi ích)

1. **Chạy `flutter analyze` và `flutter test`, sửa lỗi nếu có:** khoảng +0.22. Đây cũng là rủi ro lớn nhất khi demo.
2. **Đổi hàng** (đổi sang sản phẩm hoặc biến thể khác trong luồng đổi trả): khoảng +0.10.
3. **Phí giao hàng** theo chi nhánh hoặc khu vực: khoảng +0.05.
4. **V14 khuyến mãi:** trần tiền giảm, đơn tối thiểu: khoảng +0.08.
5. **Lọc theo chi nhánh cho phiếu nhập kho và phiếu bảo hành:** khoảng +0.05.
6. **P4:** đồng bộ ERD và báo cáo với hệ thống hiện tại. Không cộng điểm phần mềm, nhưng tránh bị trừ ở phần báo cáo.

---

## Lần 1 (06/11/2026, trước khi sửa)

### 0. Kết quả tổng hợp

| # | Hạng mục (theo đề cương) | Trọng số | Điểm | Tỷ lệ |
|---|---|---:|---:|---:|
| A | Tầng Cơ sở dữ liệu | 2.00 | **0.65** | 33% |
| B | Chức năng 01 – Loại SP/SP, Khách hàng, Khuyến mãi | 1.50 | **1.10** | 73% |
| C | Chức năng 02 – Đặt/hủy, Mua/Thanh toán/Hóa đơn, Trạng thái/Giao hàng/Đổi trả | 2.50 | **1.00** | 40% |
| D | Chức năng 03 – Lịch sử mua hàng, Bảo hành, Chatbot | 1.50 | **1.20** | 80% |
| E | Chức năng 04 – Kết xuất biểu mẫu, Thống kê báo cáo | 0.75 | **0.40** | 53% |
| F | Chức năng cơ bản – Đăng nhập/đăng xuất, Phân quyền, Backup & Restore | 0.75 | **0.40** | 53% |
| G | Ứng dụng di động (phạm vi mục 6 đề cương) | 1.00 | **0.55** | 55% |
| | **Tổng** | **10.00** | **5.30** | |

Trọng số được suy ra từ thang điểm mục 9 của đề cương:

- Chức năng 01: 0.25 × 3.
- Chức năng 02: 0.25 + 0.5 + 0.5.
- Chức năng 03: 0.25 + 0.25 + 0.5.
- Chức năng 04: 0.25 + 0.25.
- Chức năng cơ bản: 0.5.

Sau đó quy về thang 10. Riêng CSDL được nâng lên 2.0 theo yêu cầu đánh giá.

> **Môi trường thực hiện (mục 7 đề cương):** sinh viên được phép đề xuất công nghệ, và bộ công nghệ hiện tại (Java/Spring Boot, React, Flutter, PostgreSQL + pgvector, chatbot Python) **đã được GVHD thông qua**. Không trừ điểm. Nên ghi rõ việc GVHD đã duyệt trong Chương 1 hoặc Chương 2 của báo cáo để hội đồng không hỏi lại.

> **Lịch sử chỉnh sửa:**
> - 06/11/2026: bỏ cảnh báo công nghệ (đã được GVHD duyệt).
> - 06/11/2026: chấm lại mục E. Lần đầu tôi bỏ sót chức năng xuất báo cáo CSV đã có ở [reportExport.js](../../Fontend/src/features/reports/reportExport.js) và [ReportsPage.jsx:65](../../Fontend/src/features/reports/pages/ReportsPage.jsx#L65). Điểm tổng tăng từ 5.15 lên 5.30.
> - 06/11/2026 (sau P1): mục G ghi “Khuyến mãi: ✘” là **sai một phần**. App đã hiển thị giá gốc gạch ngang và giá sau giảm từ trước; chỉ thiếu tên chương trình và mục khuyến mãi riêng (đã bổ sung ở P1.5). Điểm của bản đánh giá này chưa được chấm lại; việc chấm lại thuộc P4.
> - Kế hoạch sửa chi tiết nằm ở [KE_HOACH_BAM_DE_CUONG.md](KE_HOACH_BAM_DE_CUONG.md).

---

## 1. Năm lỗ hổng nghiêm trọng nhất (ưu tiên sửa)

1. **Đổi trả hàng chưa có gì.** Đề cương yêu cầu ở cả web (mục 6) và mobile.
   - Bảng `return_requests` và `return_items` có trong CSDL, nhưng **không có entity, API hay giao diện nào**.
   - `OrderStatus` không có trạng thái trả hàng. `PaymentStatus` không có `REFUNDED`.
   - Lưu ý: nhóm từng quyết định **bỏ đổi trả khỏi báo cáo** (D3 trong [KE_HOACH_SUA_HE_THONG.md](KE_HOACH_SUA_HE_THONG.md)). Đề cương ghi đổi trả ở **3 chỗ** (mục 6 web, mục 6 mobile, mục 9 Chức năng 02), nên bỏ đi sẽ bị trừ điểm trực tiếp. Cần **đảo lại quyết định D3**.
2. **Hóa đơn chưa có gì.** Đề cương: "mua hàng, thanh toán, **hóa đơn**".
   - Bảng `invoices` không được dùng ở bất kỳ đâu. Không có chức năng lập hay in hóa đơn.
3. **Giao hàng chỉ là đổi trạng thái đơn.**
   - Bảng `shipments` không được dùng. Không có bước phân công nhân viên giao, mã vận đơn, ngày giao.
   - Phí ship luôn bằng 0 ([OrderServiceImpl.java:103](../../backend/src/main/java/com/example/backend/order/service/OrderServiceImpl.java#L103)).
4. **Biểu mẫu nghiệp vụ và Backup & Restore chưa có.**
   - Đã có xuất báo cáo thống kê ra CSV.
   - Chưa có **biểu mẫu nghiệp vụ** nào in hoặc xuất được: hóa đơn, phiếu nhập, phiếu bảo hành, phiếu đổi trả.
   - Không có chức năng backup/restore (đề cương ghi rõ ở mục 9 và tuần 10).
5. **Đánh giá và phản hồi sản phẩm chưa có gì** (mobile, mục 6). Bảng `reviews` không được dùng.

Ngoài ra còn ba lỗi nghiệp vụ trong phần **đã làm**:

- Đơn COD không bao giờ chuyển sang trạng thái thanh toán `PAID`.
- Đơn đã thanh toán không hủy được.
- Đơn VNPAY quá hạn giữ tồn kho mãi mãi.

Chi tiết ở mục C.

---

## A. Tầng Cơ sở dữ liệu — 0.65 / 2.00

### Điểm làm tốt (đã tính vào điểm gốc)

- Có Flyway versioning và `ddl-auto: validate`, nên schema là nguồn sự thật duy nhất.
- Khóa ngoại đầy đủ (67 FK ở V1), xóa kiểu `ON DELETE RESTRICT`.
- Các CHECK tiền ≥ 0. Có ràng buộc `discount_amount <= subtotal`.
- Bảng `inventory` tách `quantity` và `reserved_quantity`, có CHECK `reserved <= quantity`.
- `order_items` lưu snapshot giá (`unit_price`, `cost_price`, `final_unit_price`).
- Mô hình serial/IMEI tách bảng. Có ràng buộc `serial_id IS NULL OR quantity = 1`.
- Có partial unique index cho ảnh chính và cho địa chỉ mặc định.

### Lỗi và lủng nghiệp vụ (trừ điểm)

| # | Vấn đề | Bằng chứng | Hậu quả nghiệp vụ | Trừ |
|---|---|---|---|---:|
| A1 | **7 bảng thiết kế nhưng không có mã nào dùng:** `shipments`, `invoices`, `return_requests`, `return_items`, `reviews`, `chatbot_qna`, `chatbot_scripts` | Đã grep toàn bộ `backend/src/main/java` và `chatbot-service/app`: 0 kết quả | ERD và báo cáo mô tả các chức năng mà hệ thống không có. Hội đồng hỏi "bảng này dùng ở màn hình nào?" thì không trả lời được | −0.30 |
| A2 | **Không có CHECK cho trạng thái/enum ở các bảng cốt lõi:** `orders.status`, `payments.status`, `payments.payment_method`, `shipments.status`, `return_requests.status`, `invoices.status`, `promotions.discount_type/status`, `products.status`, `carts.status` | Chỉ `users`, `serial_numbers`, `warranties`, `warranty_tickets` có CHECK status (V1:70, V3, V6) | Tính toàn vẹn chỉ dựa vào Java. Ghi tay hoặc import SQL sai giá trị thì báo cáo sai mà không có lỗi nào báo ra. Không nhất quán: bảng phụ có CHECK, bảng lõi lại không | −0.15 |
| A3 | **Không có lịch sử trạng thái đơn hàng.** `orders` không có `cancel_reason`, `cancelled_at`, `confirmed_by`, … | `orders` ở V1; [OrderServiceImpl.java:212](../../backend/src/main/java/com/example/backend/order/service/OrderServiceImpl.java#L212) chỉ ghi đè `status` | Đề cương yêu cầu "quản lý trạng thái đơn hàng", nhưng không biết **ai** xác nhận, **khi nào** giao, **vì sao** hủy. Không truy vết được tranh chấp | −0.15 |
| A4 | **Không bán được cho khách vãng lai tại quầy.** `customers.user_id NOT NULL`, `orders.customer_id NOT NULL`, không có cột kênh bán (online/tại quầy) | V1 `customers`, `orders` | Đề tài là **siêu thị điện máy** ("bán hàng" là nghiệp vụ riêng trong đề cương). Khách mua trực tiếp buộc phải có tài khoản đăng nhập. Không có quy trình POS | −0.20 |
| A5 | **Chi nhánh bị gộp với kho, nhân viên không thuộc chi nhánh nào.** Không có bảng `branches`. `employees` không có `warehouse_id` | V1 `warehouses`, `employees` | Không phân quyền theo chi nhánh: nhân viên chi nhánh A vẫn xem và xử lý đơn chi nhánh B ([OrderServiceImpl.java:149](../../backend/src/main/java/com/example/backend/order/service/OrderServiceImpl.java#L149) dùng `findAll`). Tài liệu dùng lẫn "kho" và "chi nhánh" | −0.10 |
| A6 | **Hoàn tiền không được mô hình hóa.** `PaymentStatus` chỉ có `PENDING/PAID/FAILED`. `return_requests.refund_amount` không liên kết với `payments` | [PaymentStatus.java](../../backend/src/main/java/com/example/backend/order/entity/PaymentStatus.java) | Không xử lý được hủy đơn đã thanh toán và đổi trả có hoàn tiền | −0.10 |
| A7 | **Khuyến mãi quá đơn giản so với nghiệp vụ siêu thị:** không mã voucher, không đơn tối thiểu, không giới hạn số tiền giảm tối đa khi giảm theo %, không giới hạn lượt dùng, chỉ gắn ở mức sản phẩm (không theo biến thể hay danh mục) | V1 `promotions`, `promotion_products` | Giảm 10% cho TV 50 triệu là giảm 5 triệu, không có trần. Không làm được "giảm 500k cho đơn từ 10 triệu" | −0.10 |
| A8 | `user_roles` là bảng N–N nhưng có `UNIQUE(user_id)` | V1 `uq_user_roles_user_id` | Thiết kế tự mâu thuẫn: mỗi user chỉ có 1 vai trò thì bảng trung gian không cần thiết | −0.05 |
| A9 | `warranties.serial_id NOT NULL` | V1 `warranties` | Hàng không quản lý serial (phụ kiện, gia dụng nhỏ) không bảo hành được, kể cả khi `warranty_months > 0` ([WarrantyProvisioningServiceImpl](../../backend/src/main/java/com/example/backend/warranty/service/WarrantyProvisioningServiceImpl.java) bỏ qua item không có serial) | −0.05 |
| A10 | `invoices` không có trường thuế: VAT, mã số thuế người mua, tên đơn vị | V1 `invoices` | Hóa đơn bán hàng điện máy ở Việt Nam thường cần VAT | −0.05 |
| A11 | Còn sót: `categories` không unique tên; `products` không có mã sản phẩm; `carts` không có unique "1 giỏ ACTIVE / khách" (mã phải dùng `findFirst…`); `customers.loyalty_point` không bao giờ được cộng | V1; [OrderServiceImpl.java:74](../../backend/src/main/java/com/example/backend/order/service/OrderServiceImpl.java#L74) | Dễ trùng dữ liệu, có cột "chết" | −0.10 |
| | **Tổng trừ** | | | **−1.35** |

---

## B. Chức năng 01 — 1.10 / 1.50

| Mục | Hiện trạng | Thiếu hoặc lủng | Điểm |
|---|---|---|---:|
| Loại SP, sản phẩm (0.5) | CRUD danh mục (cây cha–con), thương hiệu, sản phẩm, biến thể, thông số, ảnh; tìm kiếm nâng cao | Không đáng kể | 0.45 |
| Khách hàng (0.5) | Nhân viên xem danh sách, chi tiết, lịch sử đơn, bảo hành, trả góp ([CustomerManagementController](../../backend/src/main/java/com/example/backend/customer/controller/CustomerManagementController.java) **chỉ có GET**) | Nhân viên **không tạo và không sửa được** hồ sơ khách hàng. Quyền `CUSTOMER_CREATE`/`CUSTOMER_UPDATE` có seed nhưng không endpoint nào dùng. Khách chỉ được tạo qua tự đăng ký. Điểm tích lũy không hoạt động | 0.30 |
| Khuyến mãi (0.5) | CRUD, kiểm tra trùng thời gian, tự áp giá khi checkout, khóa sản phẩm khi tính giá | Mô hình khuyến mãi quá đơn giản (A7). **App mobile không hiển thị khuyến mãi** (đề cương mục 6 yêu cầu) | 0.35 |

---

## C. Chức năng 02 — 1.00 / 2.50

### C1. Đặt hàng, hủy đặt — 0.30 / 0.50

**Làm tốt:**

- Checkout có khóa customer và khóa từng dòng tồn kho theo thứ tự cố định để tránh deadlock.
- Có giữ chỗ tồn kho (`RESERVE`).
- Lưu snapshot địa chỉ giao hàng.

**Lỗi:**

- **Đơn đã thanh toán không hủy được, ở bất kỳ vai trò nào.** [OrderTransitions.java](../../backend/src/main/java/com/example/backend/order/service/OrderTransitions.java) chặn với thông báo `"Payment đã PAID; cần refund flow"`, nhưng refund flow không tồn tại. Hậu quả: khách đã trả tiền VNPAY mà cửa hàng hết hàng thì không xử lý được trên hệ thống.
- **Đơn VNPAY quá hạn giữ tồn kho vĩnh viễn.**
  - Toàn backend không có `@Scheduled` nào.
  - Phiên VNPAY hết hạn sau 15 phút ([VnpayServiceImpl.java:56](../../backend/src/main/java/com/example/backend/order/payment/service/VnpayServiceImpl.java#L56)), nhưng đơn vẫn ở `PENDING` và `reserved_quantity` không được trả lại. Phải chờ khách hoặc nhân viên hủy tay.
  - Hậu quả: khách bỏ ngang thanh toán thì hàng bị "khóa ảo", người khác không mua được.
- Không ghi lý do hủy (xem A3).

### C2. Mua hàng, thanh toán, hóa đơn — 0.40 / 1.00

**Làm tốt:** có COD, VNPAY sandbox (IPN, return, đối soát), trả góp qua đối tác.

**Lỗi:**

- **COD không bao giờ được đánh dấu đã thu tiền.** Chỉ [VnpayServiceImpl.java:153](../../backend/src/main/java/com/example/backend/order/payment/service/VnpayServiceImpl.java#L153) gán `PAID`. Khi đơn COD chuyển `DELIVERED`, `payments.status` vẫn là `PENDING`. Hậu quả:
  - Báo cáo thanh toán (`/api/reports/payments`) cho thấy toàn bộ đơn COD **chưa thu tiền**.
  - Không đối soát được tiền nhân viên giao hàng nộp về.
- **Hóa đơn: không có.** Đây là yêu cầu được ghi đích danh trong đề cương.
- **Bán hàng tại quầy: không có** (xem A4).

### C3. Trạng thái đơn, giao hàng, đổi trả — 0.30 / 1.00

**Làm tốt:**

- Máy trạng thái rõ ràng: `PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED`, kèm nhánh `CANCELLED`.
- Giới hạn chuyển trạng thái theo chức danh ([OrderStaffPolicy.java](../../backend/src/main/java/com/example/backend/order/service/OrderStaffPolicy.java)).
- Tự cấp serial ở bước `PROCESSING`; trừ kho và tạo bảo hành ở bước `DELIVERED`.

**Lỗi:**

- **Giao hàng chỉ là đổi trạng thái.** Bảng `shipments` không dùng. Không biết ai giao, đơn vị vận chuyển nào, ngày gửi, ngày nhận. Không có trạng thái giao thất bại hay hoàn hàng.
- **Serial được hệ thống tự chọn** (`reserveAvailable`), nhân viên kho không quét hay xác nhận serial thực tế khi xuất. Nghi ngờ về nghiệp vụ: serial trên hệ thống có thể không khớp với máy thật giao cho khách. Vì bảo hành gắn theo serial nên đây là rủi ro tranh chấp.
- **Đổi trả: hoàn toàn không có.**

---

## D. Chức năng 03 — 1.20 / 1.50

| Mục | Hiện trạng | Thiếu hoặc lủng | Điểm |
|---|---|---|---:|
| Lịch sử mua hàng (0.375) | Khách xem đơn của mình trên web (`/my-orders`) và mobile. Nhân viên xem lịch sử theo khách | Thiếu lọc theo thời gian hoặc trạng thái phía khách (chưa xác minh) | 0.35 |
| Bảo hành (0.375) | Tự tạo phiếu bảo hành khi giao. Tra cứu theo serial/IMEI. Khách tạo yêu cầu, nhân viên xử lý. Có trên web và mobile | Chỉ hàng có serial được bảo hành (A9). Không có chi phí sửa chữa, ngày nhận/trả máy, tình trạng máy khi nhận. **Nhân viên không tạo được phiếu** cho khách mang máy đến quầy (chỉ có `PATCH /tickets/{id}`) | 0.30 |
| Chatbot (0.75) | Dịch vụ riêng: RAG, pgvector, chọn được nhà cung cấp LLM. Có trang `/chatbot` trên web | **App mobile không có chatbot.** Bảng quản lý kịch bản và Q&A (`chatbot_qna`, `chatbot_scripts`) không dùng, nên quản trị viên không quản lý được nội dung tư vấn trên giao diện | 0.55 |

---

## E. Chức năng 04 — 0.40 / 0.75

- **Kết xuất biểu mẫu (0.375): 0.15.**
  - Đã có: nút "Xuất file Excel (CSV)" trên `/reports`, xuất toàn bộ số liệu báo cáo theo khoảng ngày ([ReportsPage.jsx:65](../../Fontend/src/features/reports/pages/ReportsPage.jsx#L65)).
  - Thiếu: **biểu mẫu nghiệp vụ** của siêu thị điện máy (hóa đơn bán hàng, phiếu nhập kho, phiếu bảo hành, phiếu tiếp nhận bảo hành, phiếu đổi trả). Không có trang in hay xuất PDF nào. Đề cương mục 1 còn yêu cầu "thu thập các biểu mẫu", nên hội đồng sẽ đối chiếu biểu mẫu thu thập được với biểu mẫu hệ thống in ra.
- **Thống kê báo cáo (0.375): 0.25.**
  - Đã có: doanh thu theo ngày/tháng, top sản phẩm, trạng thái đơn, theo chi nhánh, thanh toán, nhập hàng, tồn thấp.
  - **Thiếu thống kê theo loại sản phẩm.** Đề cương mục 5 ghi rõ "doanh thu, sản phẩm, **loại sản phẩm**". Toàn bộ `report/` không có truy vấn nào theo `category`.
  - Báo cáo thanh toán sai với COD (xem C2).

---

## F. Chức năng cơ bản — 0.40 / 0.75

| Mục | Hiện trạng | Điểm |
|---|---|---:|
| Đăng nhập, đăng xuất (0.25) | JWT + refresh token xoay vòng, CSRF, đăng ký | 0.25 |
| Phân quyền (0.25) | Phân quyền theo vai trò và permission, 8 vai trò | 0.15 |
| Backup & Restore (0.25) | **Không có** | 0.00 |

Phân quyền bị trừ vì **không có API tạo nhân viên.** `new Employee()` không xuất hiện ngoài dữ liệu seed, và `UserController` không có POST. Quản trị viên không thêm được nhân viên mới qua hệ thống. Quyền `EMPLOYEE_CREATE` có seed nhưng không dùng.

---

## G. Ứng dụng di động — 0.55 / 1.00

Đối chiếu mục 6 đề cương ("Xây dựng được ứng dụng di động…"):

| Yêu cầu | Có? |
|---|---|
| Xem, tìm kiếm sản phẩm chi tiết | ✔ |
| Đặt hàng, thanh toán | ✔ |
| Quản lý trạng thái đơn hàng | ✔ (xem; hủy đơn chưa thanh toán) |
| Khuyến mãi | ✘ chỉ thấy số tiền giảm trong chi tiết đơn |
| Đổi trả | ✘ |
| Phản hồi, đánh giá sản phẩm | ✘ |
| Thông tin khách hàng, lịch sử mua bán | ✔ |
| Chatbot (đề cương mục 5 yêu cầu chạy trên 02 nền tảng) | ✘ |

---

## H. Phần làm thêm ngoài đề cương (không cộng điểm)

- Đa chi nhánh: chọn chi nhánh, giỏ hàng gắn với chi nhánh.
- Nhà cung cấp.
- Phiếu nhập kho kèm Serial/IMEI.
- Quản lý serial.
- Trả góp và đối tác trả góp.
- Địa chỉ giao hàng nhiều mục.
- Chatbot Lab và benchmark.
- Triển khai qua tunnel.

Các phần này làm tốt về kỹ thuật, nhưng đề cương không yêu cầu. Trong khi đó **5 yêu cầu ghi đích danh trong đề cương vẫn còn trống**: đổi trả, hóa đơn, giao hàng, biểu mẫu, backup/restore. Nên ưu tiên lấp những phần này trước khi bảo vệ.

---

## I. Đề xuất thứ tự sửa (theo điểm có thể thu hồi)

| Ưu tiên | Việc | Có thể thu hồi | Ghi chú |
|---|---|---:|---|
| 1 | Đổi trả: API, UI web và mobile, dùng sẵn `return_requests`/`return_items`, thêm hoàn tiền | ~0.6 | Bảng đã có sẵn |
| 2 | Hóa đơn: tự tạo khi `DELIVERED`, dùng sẵn `invoices`, in/xuất PDF | ~0.5 | Gộp được với biểu mẫu |
| 3 | Giao hàng: dùng sẵn `shipments`, phân công nhân viên giao; COD chuyển `PAID` khi giao thành công | ~0.4 | Sửa luôn lỗi báo cáo COD |
| 4 | Thống kê theo loại sản phẩm; in biểu mẫu nghiệp vụ (phiếu nhập, phiếu bảo hành, phiếu đổi trả) | ~0.3 | Xuất CSV báo cáo đã có |
| 5 | Backup & Restore: gọi `pg_dump`/`pg_restore` qua màn hình quản trị | ~0.25 | |
| 6 | Job tự hủy đơn VNPAY quá hạn và trả lại tồn kho | ~0.15 | `@Scheduled` |
| 7 | Mobile: hiển thị khuyến mãi, đánh giá sản phẩm (bảng `reviews`), chatbot | ~0.4 | |
| 8 | CSDL: thêm CHECK cho status, bảng lịch sử trạng thái đơn, lý do hủy | ~0.3 | Cần migration mới. `backend/AGENTS.md` đang **khóa schema**, phải được nhóm đồng ý trước |

> Nếu không kịp làm, ít nhất **bỏ các bảng không dùng khỏi ERD trong báo cáo** hoặc ghi rõ "hướng phát triển". Nếu không, mô hình dữ liệu trong báo cáo sẽ mâu thuẫn với phần mềm.
