# Kế hoạch chỉnh sửa và bổ sung để hệ thống bám sát Đề cương CNTT-KLCN054

- **Ngày lập:** 06/11/2026
- **Đầu vào:** [DANH_GIA_THEO_DE_CUONG.md](DANH_GIA_THEO_DE_CUONG.md), hiện đạt **5.30/10**.
- **Thay thế thứ tự ưu tiên của:** [KE_HOACH_SUA_HE_THONG.md](KE_HOACH_SUA_HE_THONG.md). Các giai đoạn S1, S2 đã xong vẫn giữ nguyên. Mã việc cũ (S3.x, S5.x…) được ghi kèm để tiện đối chiếu.
- **Mục tiêu:** đạt **≥ 8.5/10** theo cách chấm của bản đánh giá, tức là mọi yêu cầu ghi đích danh trong đề cương đều demo được trên web, và mobile làm được những gì mục 6 của đề cương yêu cầu.

---

## 1. Nguyên tắc

1. **Đề cương là đặc tả cao nhất.**
   - Báo cáo mô tả theo đề cương, hệ thống làm theo đề cương.
   - Quyết định cũ nào trái với đề cương thì bỏ quyết định đó.
2. **Dừng làm thêm phần ngoài đề cương** cho tới khi xong phần trong đề cương. Các việc sau chuyển sang "hướng phát triển":
   - S3.5 tích điểm;
   - S4.2–S4.5 phiếu xuất, điều chuyển, kiểm kê, đơn đặt NCC;
   - S5.3–S5.4 tối ưu benchmark, chuyển hội thoại cho nhân viên.
3. **Dùng bảng có sẵn trước.**
   - `shipments`, `invoices`, `return_requests`, `return_items`, `reviews` đã có trong V1, nên phần lớn việc ở giai đoạn P1 **không cần đổi schema**.
4. **Mọi thay đổi schema đều phải được cho phép trước.** `backend/AGENTS.md` mục 10 đang khóa schema.
   - Các việc cần migration được gom vào giai đoạn P3.
   - Mỗi migration cần bạn duyệt riêng, giống cách đã làm với V9.
5. **Mỗi việc có test và tiêu chí nghiệm thu.** Mỗi giai đoạn kết thúc bằng:
   - chạy hồi quy toàn bộ (backend, web, Flutter, chatbot);
   - cập nhật bảng "mức độ hoàn thành" trong báo cáo.

---

## 2. Quyết định cần chốt trước khi làm

> **Đã chốt ngày 06/11/2026:** bạn đồng ý toàn bộ đề xuất Q1–Q6 dưới đây.
> - Q3: cho phép thêm các giá trị enum đã liệt kê.
> - Q4: cho phép migration V10–V13, nhưng **vẫn duyệt nội dung từng migration** trước khi áp dụng.

| # | Quyết định | Đề xuất | Lý do |
|---|---|---|---|
| Q1 | **Đảo lại D3**, đưa đổi trả trở lại | **Làm lại đổi trả** | Đề cương ghi đổi trả ở 3 chỗ (mục 6 web, mục 6 mobile, mục 9 CN02, 0.5 điểm) |
| Q2 | Chính sách đổi trả | Trong **7 ngày** kể từ ngày giao. Chỉ áp dụng cho đơn `DELIVERED`. Trả một phần theo từng dòng hoặc từng serial | Theo thông lệ các siêu thị điện máy (Nguyễn Kim, Điện máy Chợ Lớn là tài liệu tham khảo [6][7] của đề cương). Số ngày để thành hằng số cấu hình |
| Q3 | Thêm giá trị enum mới (không cần migration vì các cột này không có CHECK) | `PaymentStatus.REFUNDED`. Trạng thái shipment: `SHIPPING/DELIVERED/FAILED`. Trạng thái invoice: `ISSUED/CANCELLED`. Trạng thái return: `REQUESTED/APPROVED/REJECTED/RECEIVED/REFUNDED`. Trạng thái review: `VISIBLE/HIDDEN` | `AGENTS.md` cấm tự đặt enum mới, nên cần bạn cho phép |
| Q4 | Mở khóa schema cho P3 | Duyệt **từng migration** V10–V13 ở mục P3 | Tầng CSDL là phần bị trừ nặng nhất (0.65/2.0) |
| Q5 | Có làm bán hàng tại quầy (POS) không | **Có, ở mức tối thiểu** (P3.4) | Đề cương mục 5 ghi "bán hàng" tách riêng với "đặt hàng, mua hàng". Đề tài là siêu thị điện máy, nên hội đồng dễ hỏi |
| Q6 | Chia việc cho 3 thành viên | Theo luồng: **Backend + CSDL** / **Web** / **Mobile + Chatbot** | Mỗi việc trong P1 đều có phần backend, web và mobile, nên 3 người làm song song được |

---

## 3. Lộ trình tổng quan

| Giai đoạn | Nội dung | Đổi schema? | Dự kiến thu hồi | Điểm sau giai đoạn |
|---|---|---|---:|---:|
| **P0** | Sửa lỗi nghiệp vụ trong phần đã có | Không | +0.7 | ~6.0 |
| **P1** | Lấp các yêu cầu đề cương còn thiếu bằng bảng có sẵn | Không (chỉ cần Q3) | +2.0 | ~8.0 |
| **P2** | Biểu mẫu nghiệp vụ, Backup & Restore | Không | +0.5 | ~8.5 |
| **P3** | Gia cố CSDL, bán tại quầy, phân chi nhánh | **Có** (từng migration) | +0.6 | ~9.1 |
| **P4** | Đồng bộ báo cáo, ERD, kiểm thử, nghiệm thu | Không | Giữ điểm | — |

> **Điểm dừng tối thiểu: hết P0 + P1.** Khi đó mọi chức năng đề cương ghi đích danh đều có, trừ biểu mẫu nghiệp vụ và backup.
>
> Lưu ý thời gian: mục 8 đề cương ghi thời gian thực hiện tới **08/11/2026**. Nếu ngày nộp hoặc bảo vệ sát hơn khối lượng dưới đây, làm theo đúng thứ tự P0 → P1.1 → P1.2 → P1.3 → P2.2 và cắt từ cuối danh sách.

---

## P0 — Sửa lỗi nghiệp vụ trong phần đã có (không đổi schema)

| # | Việc | Lỗi hiện tại (từ bản đánh giá) | Cách sửa | Tiêu chí nghiệm thu | Khối lượng |
|---|---|---|---|---|---|
| P0.1 | **COD chuyển `PAID` khi giao thành công** | Chỉ VNPAY gán `PAID`, nên báo cáo thanh toán coi mọi đơn COD là chưa thu (C2) | Trong `OrderServiceImpl.status(...)`: khi chuyển `DELIVERED` và payment là COD thì gán `PAID`, `payment_date = now` | Đơn COD giao xong hiện "Đã thanh toán". Báo cáo thanh toán đúng. Có test | Nhỏ |
| P0.2 | **Tự hủy đơn VNPAY quá hạn và trả tồn kho** | Không có `@Scheduled`, nên tồn kho bị giữ mãi (C1) | Bật `@EnableScheduling`. Job chạy mỗi 5 phút, tìm đơn `PENDING` + VNPAY + `PENDING` + chưa có `transaction_code` + quá 15 phút, rồi gọi cùng luồng hủy (`RELEASE` tồn kho) | Đặt hàng VNPAY, bỏ ngang, sau 15–20 phút đơn thành `CANCELLED` và `reserved_quantity` giảm lại. Có test | Nhỏ – vừa |
| P0.3 | **Thống kê theo loại sản phẩm** | Đề cương yêu cầu nhưng chưa có (E) | `GET /api/reports/categories`: doanh thu, số lượng bán, số đơn theo `categories` (gộp danh mục con về danh mục gốc). Thêm biểu đồ trên `/reports` và cột trong file CSV | Biểu đồ hiển thị đúng. Tổng doanh thu theo loại bằng tổng doanh thu chung | Nhỏ – vừa |
| P0.4 | **Tạo tài khoản nhân viên** | Không có API, nên admin không thêm được nhân viên (F) | `POST /api/employees` (ADMIN, MANAGER; quyền `EMPLOYEE_CREATE` đã có): tạo `users` + `employees` + gán vai trò trong 1 transaction. `PUT /api/employees/{id}` để sửa. Thêm form trên `/user-management` | Admin tạo được nhân viên thủ kho, đăng nhập được, menu đúng vai trò | Vừa |
| P0.5 | **Nhân viên tạo và sửa hồ sơ khách hàng** | `CustomerManagementController` chỉ có GET (B) | `POST /api/customers`, `PUT /api/customers/{id}` (quyền `CUSTOMER_CREATE`/`CUSTOMER_UPDATE` đã có). Khóa/mở khóa dùng `PATCH /api/users/{id}/status` sẵn có, thêm nút trên trang khách hàng | Nhân viên bán hàng tạo được khách tại cửa hàng | Nhỏ – vừa |
| P0.6 | **Nhân viên lập phiếu bảo hành tại quầy** | Chỉ khách tự tạo được phiếu (D) | `POST /api/warranties/{warrantyId}/tickets` cho `WARRANTY_TECHNICIAN`. Tra cứu bằng serial/IMEI (đã có `/lookup`), rồi lập phiếu | Kỹ thuật viên nhận máy, tra serial, lập phiếu, xử lý tới `COMPLETED` | Nhỏ |
| P0.7 | **Hủy đơn đã thanh toán** | Bị chặn với thông báo "cần refund flow" (C1) | Cho ADMIN/MANAGER hủy đơn `PAID` ở trạng thái `PENDING/CONFIRMED`: trả tồn kho, payment chuyển `REFUNDED` (Q3), bắt buộc nhập lý do. Hoàn tiền VNPAY thực hiện ngoài hệ thống, nhân viên xác nhận đã hoàn | Hủy được đơn VNPAY đã trả tiền. Payment chuyển `REFUNDED`. Báo cáo không tính doanh thu đơn này | Vừa |

---

## P1 — Lấp các yêu cầu đề cương còn thiếu (dùng bảng có sẵn)

### P1.1 Giao hàng (`shipments`) — mã cũ S3.1 — Đề cương: CN02 "giao hàng"

**Quy tắc nghiệp vụ:**

- Khi chuyển `PROCESSING → SHIPPED`, bắt buộc chọn **nhân viên giao** (vai trò `DELIVERY_STAFF`; `shipments.employee_id NOT NULL`) và **đơn vị vận chuyển** (mặc định "Giao nội bộ"). Mã vận đơn tự sinh hoặc nhập tay. Hệ thống tạo `shipments` với `status = SHIPPING`, `shipped_date = now`.
- `DELIVERED`: ghi `delivered_date`, shipment chuyển `DELIVERED`, đồng thời chạy P0.1 (COD → PAID).
- **Giao thất bại** (`FAILED`, kèm lý do ghi ở `note` của đơn):
  - đơn chuyển `CANCELLED`;
  - trả tồn kho: `RELEASE` số lượng đã giữ chỗ;
  - **trả serial đã cấp về `AVAILABLE`**. Bước này hiện chưa có, cần viết thêm trong `SerialAllocationService`.
- Nhân viên giao chỉ thấy các đơn được giao cho mình.

**Phạm vi:**

| Thành phần | Nội dung |
|---|---|
| Backend | Entity, repository, service `Shipment`. Mở rộng `PATCH /api/orders/{id}/status` để nhận thông tin giao hàng. `GET /api/shipments/mine` cho nhân viên giao |
| Web | Hộp chọn nhân viên giao khi bấm "Giao hàng". Khối "Thông tin giao hàng" trong chi tiết đơn. Trang "Đơn tôi cần giao" |
| Mobile | Timeline giao hàng trong `order_detail_screen.dart` |

**Nghiệm thu:** một đơn đi trọn luồng xác nhận → xử lý → giao → đã giao, có đủ người giao và ngày giờ. Giao thất bại thì tồn kho và serial được trả lại.

### P1.2 Đổi trả hàng (`return_requests`, `return_items`) — Đề cương: CN02 + mục 6 web và mobile

**Quy tắc nghiệp vụ:**

- **Khách tạo yêu cầu** (web và mobile):
  - chỉ với đơn `DELIVERED` trong 7 ngày (Q2);
  - chọn từng dòng hàng hoặc từng serial, nhập lý do;
  - một serial chỉ được nằm trong 1 yêu cầu chưa kết thúc.
- **Trạng thái:** `REQUESTED` → `APPROVED` hoặc `REJECTED` (nhân viên bán hàng duyệt, ghi `employee_id`) → `RECEIVED` (thủ kho nhận hàng) → `REFUNDED`.
- **Khi `RECEIVED`:**
  - thủ kho chọn tình trạng hàng: nhập lại kho (serial về `AVAILABLE`, `inventory.quantity + 1`) hoặc hàng lỗi (serial về `DEFECTIVE`, không cộng kho bán);
  - hàng không quản lý serial thì cộng theo số lượng.
- **Khi `REFUNDED`:**
  - `refund_amount` = tổng `final_unit_price × số lượng` của các dòng trả;
  - trả toàn bộ đơn thì payment chuyển `REFUNDED` và hóa đơn chuyển `CANCELLED` (P1.3);
  - trả một phần thì đơn vẫn `DELIVERED`, báo cáo doanh thu **trừ** `refund_amount`.
- **Bảo hành của serial đã trả:** `warranties.status` chỉ cho phép `ACTIVE/EXPIRED` (CHECK ở V6). Tạm thời cho hết hạn bằng cách gán `end_date = ngày trả` và `status = EXPIRED`. Cách đúng hơn (thêm `VOIDED`) nằm ở P3.1.

**Phạm vi:**

| Thành phần | Nội dung |
|---|---|
| Backend | Package mới `returns/` theo cấu trúc vertical-slice. API khách: `POST/GET /api/returns/mine`. API nhân viên: `GET /api/returns`, `PATCH /api/returns/{id}/status`. Sửa truy vấn báo cáo doanh thu để trừ tiền hoàn |
| Web | Nút "Yêu cầu đổi trả" trong `/my-orders/:id`. Trang quản lý `/returns` (lọc theo trạng thái) và trang chi tiết |
| Mobile | Nút đổi trả trong chi tiết đơn, màn hình tạo yêu cầu, danh sách yêu cầu của tôi |

**Nghiệm thu:**

- Trọn vòng: yêu cầu → duyệt → nhận hàng → hoàn tiền.
- Tồn kho, serial, doanh thu thay đổi đúng.
- Bị chặn khi quá 7 ngày, khi đơn chưa giao, khi trả trùng serial.

### P1.3 Hóa đơn (`invoices`) — mã cũ S3.2 — Đề cương: CN02 "mua hàng, thanh toán, hóa đơn"

**Quy tắc nghiệp vụ:**

- Phát hành **tự động khi đơn `DELIVERED`**, vì lúc đó payment đã `PAID` sau P0.1. Mỗi đơn có tối đa 1 hóa đơn (`uq_invoices_order_id`).
- Số hóa đơn có dạng `HD-yyyyMMdd-NNNN`, tăng dần theo ngày, khóa để không trùng.
- Khi trả toàn bộ đơn (P1.2) hoặc hủy đơn sau khi đã phát hành, hóa đơn chuyển `CANCELLED`, không xóa.
- Dòng hóa đơn lấy từ `order_items`, là snapshot giá đã khóa lúc đặt hàng.

**Phạm vi:**

| Thành phần | Nội dung |
|---|---|
| Backend | `GET /api/invoices`, `GET /api/invoices/{id}`, `GET /api/orders/mine/{id}/invoice` |
| Web | Trang danh sách hóa đơn. **Trang in hóa đơn** (khổ A4/A5, `@media print`, có thông tin cửa hàng, chi nhánh, khách hàng, dòng hàng kèm serial/IMEI, tổng tiền bằng số và bằng chữ) |
| Mobile | Xem hóa đơn trong chi tiết đơn |

**Nghiệm thu:** đơn giao xong có hóa đơn, in ra đúng mẫu, số liệu khớp đơn hàng.

### P1.4 Đánh giá và phản hồi sản phẩm (`reviews`) — mã cũ S3.3 — Đề cương: mục 6 mobile

**Quy tắc nghiệp vụ:**

- Chỉ khách đã nhận hàng (`order_item` thuộc đơn `DELIVERED`) mới đánh giá được, mỗi `order_item` một lần (`uq_reviews_customer_order_item` đã có).
- Chấm 1–5 sao, kèm nội dung.
- Nhân viên phản hồi (`reply_text`, `replied_at`, `employee_id`) và ẩn/hiện đánh giá vi phạm.
- Trang sản phẩm hiển thị điểm trung bình và danh sách đánh giá `VISIBLE`.

**Phạm vi:**

| Thành phần | Nội dung |
|---|---|
| Backend | `POST /api/reviews` (khách), `GET /api/products/{id}/reviews` (công khai), `GET /api/reviews` và `PATCH /api/reviews/{id}` (nhân viên) |
| Web | Khối đánh giá ở trang chi tiết sản phẩm. Nút "Đánh giá" trong đơn đã giao. Trang quản lý đánh giá |
| Mobile | Hiển thị đánh giá ở chi tiết sản phẩm. Viết đánh giá từ chi tiết đơn |

### P1.5 Khuyến mãi trên mobile — Đề cương: mục 6 mobile "khuyến mãi"

- API sản phẩm đã trả `discountAmount` (web dùng trong `SaleProductsSection.jsx`). Phía Flutter chưa đọc trường này.
- **Mobile:**
  - giá gốc gạch ngang và giá sau giảm trên thẻ và trang chi tiết sản phẩm;
  - mục "Đang khuyến mãi" ở trang chủ.
- **Nghiệm thu:** cùng một sản phẩm thì giá trên web, mobile và lúc checkout trùng nhau.

### P1.6 Chatbot trên mobile — mã cũ S5.1 — Đề cương: mục 5 "xây dựng hệ thống trên 02 nền tảng"

- Màn hình chat Flutter gọi API chatbot sẵn có: lịch sử hội thoại, thẻ sản phẩm, bấm thẻ để mở chi tiết sản phẩm.
- **Nghiệm thu:** hỏi "laptop dưới 20 triệu" trên mobile ra kết quả giống web.

---

## P2 — Biểu mẫu nghiệp vụ và Backup & Restore

### P2.1 Bộ biểu mẫu in — Đề cương: CN04 "kết xuất biểu mẫu"

Mỗi biểu mẫu là một trang in (`@media print`, A4), dùng chung một khung: logo, tên cửa hàng, chi nhánh, số phiếu, ngày, chữ ký.

| Biểu mẫu | Nguồn dữ liệu | Ai in |
|---|---|---|
| Hóa đơn bán hàng | P1.3 | Bán hàng |
| Phiếu nhập kho (kèm serial/IMEI) | `goods_receipts` (đã có) | Thủ kho |
| Phiếu giao hàng | P1.1 | Giao hàng |
| Phiếu bảo hành | `warranties` (đã có) | Bán hàng, kỹ thuật |
| Phiếu tiếp nhận bảo hành | `warranty_tickets` (đã có) | Kỹ thuật |
| Phiếu đổi trả | P1.2 | Bán hàng, kho |

Mẫu in nên **bám các biểu mẫu nhóm thu thập ở mục 1 đề cương**, để khi bảo vệ có thể đặt cạnh nhau so sánh. In ra PDF bằng chức năng "Lưu thành PDF" của trình duyệt là đủ, không cần thêm thư viện.

### P2.2 Backup & Restore — Đề cương: mục 9 "chức năng cơ bản" + tuần 10

**Backend** (chỉ ADMIN):

- `POST /api/admin/backups`: chạy `pg_dump -Fc` qua `ProcessBuilder`, lưu vào thư mục cấu hình `app.backup.dir`, tên file theo thời gian.
- `GET /api/admin/backups`: liệt kê bản sao lưu (tên, kích thước, thời gian). Kèm tải về.
- `POST /api/admin/backups/{name}/restore`: chạy `pg_restore --clean --if-exists`. Bắt buộc gửi lại tên file để xác nhận. Trước khi khôi phục **tự sao lưu bản hiện tại**.

**Ràng buộc kỹ thuật:**

- Máy chạy backend phải có `pg_dump` cùng phiên bản với PostgreSQL 16 (Docker dùng `pgvector/pgvector:pg16`).
- Cách đơn giản nhất: gọi `docker exec ecommerce-postgres pg_dump …`, hoặc cài `postgresql-client-16`. Ghi rõ trong `LOCAL_SETUP.md`.
- Tên file chỉ chấp nhận đúng định dạng đã sinh, chống path traversal.
- Mật khẩu truyền qua `PGPASSWORD` và không ghi vào log.

**Web:** trang `/admin/backups` gồm nút "Sao lưu ngay", danh sách bản sao lưu, nút "Khôi phục" kèm hộp xác nhận.

**Nghiệm thu:**

1. Sao lưu.
2. Xóa một sản phẩm thử.
3. Khôi phục.
4. Sản phẩm xuất hiện lại.

---

## P3 — Gia cố CSDL (cần cho phép đổi schema, duyệt từng migration)

| # | Migration | Nội dung | Sửa lỗi đánh giá | Ảnh hưởng mã |
|---|---|---|---|---|
| P3.1 | **V10 – Ràng buộc trạng thái** | Thêm `CHECK (status IN …)` cho `orders`, `payments` (+ `payment_method`), `shipments`, `invoices`, `return_requests`, `reviews`, `promotions` (+ `discount_type`), `products`, `carts`. Thêm `VOIDED` vào CHECK của `warranties` | A2, A9 (một phần) | Không đổi mã. Chỉ cần dữ liệu cũ hợp lệ: chạy truy vấn kiểm tra trước khi áp |
| P3.2 | **V11 – Lịch sử trạng thái đơn** | Bảng `order_status_history` (order_id, from_status, to_status, employee_id, note, changed_at). Thêm cột `orders.cancel_reason` | A3 | Mỗi lần đổi trạng thái thì ghi 1 dòng. Chi tiết đơn có timeline "ai, lúc nào, làm gì" |
| P3.3 | **V12 – Nhân viên thuộc chi nhánh** | `employees.warehouse_id` (FK, cho phép NULL với ADMIN/MANAGER cấp hệ thống) | A5 | Nhân viên chỉ thấy và xử lý đơn, phiếu nhập, bảo hành của chi nhánh mình. Báo cáo tự lọc theo chi nhánh |
| P3.4 | **V13 – Bán hàng tại quầy (tối thiểu)** | Cho phép `customers.user_id` NULL (khách vãng lai, thêm `customers.phone`). Thêm `orders.sales_channel` (`ONLINE`/`IN_STORE`) | A4 | Màn hình POS cho nhân viên bán hàng: chọn hoặc tạo khách theo SĐT, quét SKU/serial, thanh toán tiền mặt, xuất kho và hóa đơn ngay (bỏ qua giao hàng) |
| P3.5 | V14 – Khuyến mãi đầy đủ hơn *(tùy chọn)* | `max_discount_amount`, `min_order_amount` | A7 | Cập nhật `PromotionServiceImpl.calculate` |

**Không đổi, chỉ giải thích trong báo cáo:**

- `user_roles` có `UNIQUE(user_id)` (A8): ghi là quyết định thiết kế "mỗi tài khoản một chức danh", bảng trung gian để sau này mở rộng.
- `chatbot_scripts` và `chatbot_qna`: hoặc làm màn hình quản trị Q&A (S5.2), hoặc **đưa ra khỏi ERD** và chuyển sang hướng phát triển. Không để bảng "chết" trong ERD.

---

## P4 — Đồng bộ báo cáo và nghiệm thu

1. **ERD và mô hình dữ liệu:** cập nhật theo V10 trở đi. Mọi bảng trong ERD đều phải chỉ ra được màn hình và API sử dụng.
2. **Use case:** thêm lại UC đổi trả, UC backup/restore, UC bán tại quầy (nếu làm P3.4). Sửa đặc tả giao hàng và hóa đơn theo luồng thật.
3. **Ma trận đối chiếu đề cương ↔ chức năng ↔ màn hình:** một bảng duy nhất trong Chương 4, mỗi dòng là một yêu cầu đề cương. Đây là bảng hội đồng dò đầu tiên.
4. **Kiểm thử:**
   - hồi quy toàn bộ (backend, web, Flutter, chatbot);
   - thêm test cho từng quy tắc nghiệp vụ ở P0–P2: chặn trả quá hạn, trả tồn kho khi giao thất bại, COD → PAID…
5. **Kịch bản demo** (một luồng đi qua hầu hết đề cương):
   - nhập kho kèm IMEI;
   - khách đặt trên mobile với giá khuyến mãi;
   - nhân viên xác nhận, xuất kho, giao hàng;
   - in hóa đơn;
   - khách đánh giá;
   - khách yêu cầu đổi trả, nhân viên duyệt và hoàn tiền;
   - kỹ thuật viên lập phiếu bảo hành;
   - xem thống kê theo loại sản phẩm và xuất CSV;
   - sao lưu, khôi phục.
6. **Chấm lại** theo [DANH_GIA_THEO_DE_CUONG.md](DANH_GIA_THEO_DE_CUONG.md) và cập nhật điểm.

---

## 4. Bảng theo dõi

| Mã | Việc | Phụ trách | Phụ thuộc | Trạng thái |
|---|---|---|---|---|
| P0.1 | COD → PAID khi giao | | | ✅ 06/11/2026: `OrderServiceImpl.settleOnDelivery`. Áp dụng cho cả **trả góp** (đã duyệt, khi giao). VNPAY vẫn chỉ do callback xử lý. +3 test tích hợp; backend 292/292 đạt. **Chưa xử lý:** dữ liệu cũ (đơn COD đã `DELIVERED` từ trước vẫn `PENDING`), cần một câu `UPDATE` nếu bạn muốn |
| P0.2 | Tự hủy đơn VNPAY quá hạn | | | ✅ 06/11/2026: `VnpayExpiryJob` chạy 5 phút/lần, hủy đơn VNPAY chưa có kết quả sau **20 phút** (15 phút phiên + 5 phút chờ IPN), trả tồn kho, payment → `FAILED`, ghi lý do vào ghi chú đơn. Không đụng đơn đang chờ đối soát (mã 07). Tắt bằng `VNPAY_EXPIRY_ENABLED=false`. +3 test |
| P0.3 | Thống kê theo loại sản phẩm | | | ✅ 06/11/2026: `GET /api/reports/categories` (gộp danh mục con về danh mục gốc; số lượng, số đơn, doanh thu, lợi nhuận gộp; lọc theo chi nhánh). Biểu đồ trên `/reports` và mục mới trong file CSV. +3 test backend, cập nhật test xuất file |
| P0.4 | Tạo/sửa nhân viên | | | ✅ 06/11/2026: `POST /api/employees` (tạo `users` + `user_roles` + `employees` trong 1 giao dịch, mã tự sinh `NVxxxx`), `GET /api/employees/by-user/{id}`, `PUT /api/employees/{id}`. ADMIN tạo mọi chức danh; MANAGER chỉ tạo chức danh nhân viên (không tạo ADMIN/MANAGER). Web: nút “+ Thêm nhân viên” và khối “Hồ sơ nhân viên” ở `/user-management`. +4 test backend, +1 test web |
| P0.5 | Tạo/sửa khách hàng | | | ✅ 06/11/2026: `POST /api/customers`, `PUT /api/customers/{id}`. Số điện thoại không được trùng giữa các khách; tên đăng nhập mặc định là số điện thoại. Nhân viên bán hàng được phép (theo vai trò, không cần thêm quyền vào CSDL). Web: “+ Thêm khách hàng”, “Sửa thông tin”. +2 test backend, +4 test web (trước đây trang khách hàng chưa có test) |
| P0.6 | Nhân viên lập phiếu bảo hành | | | ✅ 06/11/2026: `POST /api/warranties/{id}/tickets` cho ADMIN/MANAGER/STAFF/WARRANTY_TECHNICIAN; phiếu được giao luôn cho người tiếp nhận; dùng chung điều kiện hợp lệ với phía khách. Web: form “Lập phiếu tiếp nhận” ở trang chi tiết bảo hành. +3 test backend (trước đây module bảo hành chưa có test), +1 test web |
| P0.7 | Hủy đơn đã thanh toán + REFUNDED | | Q3 | ✅ 06/11/2026: thêm `PaymentStatus.REFUNDED`. Chỉ ADMIN/MANAGER hủy được đơn đã trả tiền, chỉ ở bước `PENDING`/`CONFIRMED`, **bắt buộc lý do**; trả tồn kho, payment → `REFUNDED`. Lý do ghi vào ghi chú đơn (chờ cột `cancel_reason` ở V11). Web: hộp nhập lý do khi hủy, nhãn “Đã hoàn tiền”; mobile: nhãn trạng thái thanh toán mới. +3 test backend, +1 test web |
| **P0** | **Kết quả hồi quy 06/11/2026** | | | Backend **310/310**, web **181/181**, lint sạch, build thành công. Flutter: chưa chạy `flutter analyze`/`flutter test` vì máy này không có Flutter SDK, cần chạy trên máy nhóm. Chưa commit |
| P1.1 | Giao hàng (`shipments`) | | P0.1 | ✅ 06/11/2026: bàn giao `PROCESSING → SHIPPED` tạo bản ghi `shipments` (nhân viên giao bắt buộc có vai trò DELIVERY_STAFF và đang hoạt động; nhân viên giao tự nhận nếu không chọn; đơn vị mặc định “Giao nội bộ”; mã vận đơn tự sinh `GH-…`, không trùng). `DELIVERED` ghi ngày giao. **Giao không thành công** = `SHIPPED → CANCELLED`, bắt buộc lý do: trả tồn kho, trả serial về `AVAILABLE` và gỡ khỏi dòng đơn; đơn đã trả tiền chỉ Quản lý/Admin xử lý (→ `REFUNDED`). Nhân viên giao chỉ đóng được chuyến của mình. API mới: `GET /api/orders/deliveries`, `GET /api/orders/delivery-staff`. Web: form bàn giao, khối “Vận chuyển”, nút “Giao không thành công”, trang `/deliveries`. Mobile: thẻ “Vận chuyển” ở chi tiết đơn. +5 test tích hợp, +1 test policy, +2 test web, +1 test Flutter (chưa chạy được). Hồi quy: backend 315/315, web 183/183, lint sạch, build thành công |
| P1.2 | Đổi trả | | Q1, Q2, Q3 | ✅ 06/11/2026: package `returns` dùng `return_requests`/`return_items`. Khách gửi yêu cầu trong **7 ngày** kể từ ngày giao (đơn `DELIVERED`, theo dòng/serial, không vượt số lượng còn lại) → bán hàng duyệt/từ chối (bắt buộc lý do) → thủ kho nhận hàng và chọn tình trạng từng dòng (nhập kho bán lại: serial `AVAILABLE`, tồn +; hàng lỗi: serial `DEFECTIVE`) và kết thúc bảo hành của serial → quản lý xác nhận hoàn tiền. Trả hết đơn: payment `REFUNDED`, hóa đơn `CANCELLED`. **Báo cáo doanh thu, lợi nhuận, top sản phẩm, theo loại, theo chi nhánh đều trừ phần đã hoàn.** Web: khối đổi trả trong đơn đã giao, `/my-returns`, `/returns`. Mobile: khối đổi trả và màn hình gửi yêu cầu. Giới hạn: kết quả kiểm tra hàng và lý do từ chối được ghi nối vào cột `reason` (chưa có cột riêng). +4 test tích hợp, +3 test web, +1 test Flutter |
| P1.3 | Hóa đơn + trang in | | P0.1 | ✅ 06/11/2026: package `invoice` dùng `invoices`. Tự phát hành khi đơn `DELIVERED`, số `HD-yyyyMMdd-NNNN` tăng dần theo ngày (khóa advisory PostgreSQL chống trùng), mỗi đơn 1 hóa đơn. Hủy khi khách trả toàn bộ hàng. Web: `/invoices` (tra theo số), trang in A4 (`@media print`, số tiền bằng chữ, serial/IMEI, chữ ký), khách xem/in ở `/my-orders/:id/invoice`. Mobile: số hóa đơn trong chi tiết đơn. Giới hạn: đơn đã giao trước khi có chức năng này chưa có hóa đơn; chưa có thông tin VAT (bảng không có cột). +1 test tích hợp (số tuần tự, quyền xem), +11 test web, +1 test Flutter |
| P1.4 | Đánh giá sản phẩm | | Q3 | ✅ 06/11/2026: package `review` dùng `reviews`. Chỉ người mua dòng hàng của đơn đã giao được đánh giá, mỗi dòng 1 lần (1–5 sao). Nhân viên phản hồi và ẩn/hiện. Trang sản phẩm có điểm trung bình (chỉ đánh giá hiển thị). Web: khối đánh giá ở trang sản phẩm (thay nút “sắp có”), đánh giá từ chi tiết đơn, `/reviews` cho nhân viên. Mobile: khối đánh giá ở chi tiết sản phẩm, đánh giá từ chi tiết đơn. +3 test tích hợp, +2 test web, +1 test Flutter |
| P1.5 | Khuyến mãi trên mobile | | | ✅ 06/11/2026: **đã có sẵn** giá gốc gạch ngang + giá sau giảm (bản đánh giá ghi “không có” là sai một phần). Bổ sung: nhãn “-x%”, tên chương trình khuyến mãi, mục “Đang khuyến mãi” ở trang chủ. +1 test Flutter |
| P1.6 | Chatbot trên mobile | | | ✅ 06/11/2026: màn hình “Trợ lý tư vấn” (nút nổi “Tư vấn” ở trang chủ của khách), gọi cùng API `/api/v1/chat` với web: thẻ sản phẩm mở chi tiết sản phẩm, câu hỏi gợi ý, cuộc trò chuyện mới. **Lưu ý triển khai:** `API_BASE_URL` của app phải trỏ tới cổng gateway (Caddy `:8088` hoặc tunnel), vì `/api/v1/chat` do chatbot-service phục vụ, không phải backend `:8080`. +1 test Flutter |
| **P1** | **Kết quả hồi quy 06/11/2026** | | | Backend **323/323**, web **199/199** (1 lần chạy có 2 test timeout khi máy tải nặng, chạy lại đạt), lint sạch, build thành công. **Flutter chưa chạy được `flutter analyze`/`flutter test`** (máy không có SDK): cần nhóm chạy trước khi demo. Chưa commit |
| P2.1 | Bộ biểu mẫu in | | P1.1–P1.3 | ✅ 06/11/2026: khung in A4 dùng chung (`components/print/PrintForm`, chỉ đưa vào DOM khi bấm in) và 5 biểu mẫu mới, cùng với hóa đơn ở P1.3: **phiếu nhập kho** (kèm serial/IMEI, tổng tiền bằng chữ), **phiếu giao hàng** (ghi thu hộ COD hay đã thanh toán), **phiếu bảo hành**, **phiếu tiếp nhận bảo hành**, **phiếu đổi trả**. Mỗi trang chi tiết có nút “In phiếu…”, lưu PDF bằng hộp thoại in của trình duyệt. Cần đối chiếu với biểu mẫu nhóm thu thập ở mục 1 đề cương. +3 test web |
| P2.2 | Backup & Restore | | | ✅ 06/11/2026: `/api/admin/backups` (ADMIN): sao lưu (`pg_dump -Fc`), liệt kê, tải về, khôi phục (`pg_restore --clean --single-transaction`, bắt buộc nhập lại tên, **tự sao lưu dữ liệu hiện tại trước**), xóa. Chỉ nhận tên do hệ thống sinh (chặn path traversal), mật khẩu qua `PGPASSWORD`, mỗi lần một thao tác, giới hạn 10 phút. Chạy được bằng công cụ trên máy hoặc trong container (`BACKUP_COMMAND_PREFIX`), hướng dẫn ở `LOCAL_SETUP.md`. Web: trang `/admin/backups`. +2 test tích hợp **khôi phục thật** trên container PostgreSQL 16 + pgvector, +2 test web |
| **P2** | **Kết quả hồi quy 06/11/2026** | | | Backend **325/325**, web **204/204**, lint sạch, build thành công. Flutter: P2 không sửa mobile. Chưa commit |
| P3.1 | V10 CHECK trạng thái | | Q4 | ✅ 06/11/2026: CHECK cho trạng thái/phương thức của `orders`, `payments`, `installment_*`, `shipments`, `invoices`, `return_requests`, `reviews`, `promotions` (kèm giảm % ≤ 100), danh mục, thương hiệu, sản phẩm, biến thể, NCC, chi nhánh, phiếu nhập; giá trị khớp enum Java. Thêm `VOIDED` cho bảo hành: thiết bị bị trả lại thì bảo hành chuyển `VOIDED` (giữ nguyên thời hạn gốc). Không ràng buộc `carts.status` (chưa có vòng đời) và bảng của chatbot-service |
| P3.2 | V11 Lịch sử trạng thái đơn | | Q4 | ✅ 06/11/2026: bảng `order_status_history` (từ → đến, người thao tác qua `users`, ghi chú, thời điểm), backfill 1 dòng mốc cho đơn cũ. Cột `orders.cancel_reason`, `return_requests.rejection_reason`, `return_items.received_condition` thay cho việc ghi nối lý do vào cột ghi chú (P0.7, P1.1, P1.2). Web: dòng thời gian trạng thái, lý do hủy, lý do từ chối, tình trạng nhận lại. Mobile: lý do hủy |
| P3.3 | V12 Nhân viên thuộc chi nhánh | | Q4 | ✅ 06/11/2026: `employees.warehouse_id`. Nhân viên có chi nhánh chỉ thấy và xử lý đơn hàng, đổi trả, hóa đơn của chi nhánh mình; báo cáo của quản lý chi nhánh tự lọc theo chi nhánh; không giao đơn cho nhân viên giao hàng chi nhánh khác; quản lý chi nhánh chỉ tạo nhân viên cho chi nhánh mình. ADMIN và nhân viên không gán chi nhánh thấy toàn hệ thống. Web: chọn chi nhánh trong form nhân viên. **Chưa áp phạm vi chi nhánh cho phiếu nhập kho và phiếu bảo hành** |
| P3.4 | V13 Bán tại quầy | | Q4, Q5 | ✅ 06/11/2026: khách vãng lai (`customers.user_id` cho phép NULL, `customers.phone` duy nhất, `customers.created_at`), `orders.sales_channel` (`ONLINE`/`IN_STORE`), phương thức `CASH`. `POST /api/orders/in-store`: chọn khách, quét serial (hoặc để hệ thống chọn), thu tiền mặt → xuất kho, bán serial, lập bảo hành, hóa đơn, lịch sử trong 1 giao dịch. Web: trang `/pos`, tạo khách vãng lai, nhãn “Tại quầy” |
| P3.5 | V15 Khuyến mãi mở rộng | | Q4 | ⏸ Chưa làm (tùy chọn theo kế hoạch). Riêng phần chặn giảm % > 100 đã có ở V10 |
| P3.6 | **V14 Đổi hàng** (bổ sung theo yêu cầu 06/11/2026) | | | ✅ Yêu cầu đổi trả có 2 hình thức: trả hàng hoàn tiền hoặc **đổi sang sản phẩm khác**. Khách chọn hàng trả và sản phẩm muốn nhận (kiểm tra còn hàng ở chi nhánh của đơn) → duyệt → nhận và kiểm tra hàng trả → nhân viên xác nhận **Đã đổi hàng**: hệ thống tạo đơn đổi `EX-…` theo giá hiện hành, trừ giá trị hàng trả. Đơn online: đơn đổi bắt đầu ở “Đã xác nhận” và giao như thường, thu phần chênh bằng COD. Đơn tại quầy: giao ngay, thu tiền mặt. Rẻ hơn thì ghi số tiền cửa hàng hoàn lại. Doanh thu: đơn gốc trừ giá trị hàng trả, đơn đổi ghi đủ, tổng khớp tiền thực thu. Web, phiếu in, mobile đều cập nhật. +3 test tích hợp (kèm kiểm tra hạch toán), +2 test web, +1 test Flutter. Backend 339/339, web 208/208 |
| **P3** | **Kết quả hồi quy 06/11/2026** | | | Backend **336/336** (mọi test chạy trên PostgreSQL 16 thật nên V10–V13 đã được áp và kiểm tra), web **206/206**, lint sạch, build thành công. **V10–V13 chưa chạy trên CSDL dev của nhóm**: Flyway sẽ tự áp khi khởi động backend lần tới. Chưa commit |
| P4 | Đồng bộ báo cáo, nghiệm thu | | Tất cả | ☐ |
