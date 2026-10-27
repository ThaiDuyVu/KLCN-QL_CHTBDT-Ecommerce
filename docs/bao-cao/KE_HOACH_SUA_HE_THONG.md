# Kế hoạch sửa hệ thống cho khớp báo cáo

**Thời điểm thực hiện:** sau khi hoàn tất chỉnh sửa báo cáo (6 giai đoạn trong `KE_HOACH_CHINH_SUA_BAO_CAO.md`). Lúc đó báo cáo đã chốt, nên nó là "đặc tả" để hệ thống đi theo.

**Nguồn việc:**
- Ma trận đối chiếu `01_MA_TRAN_DOI_CHIEU.md`: các mục ❌ / ⚠️ / 🔄 mà báo cáo giữ lại theo quyết định D2.
- Phân tích tác nhân `01b_PHAN_TICH_TAC_NHAN.md`.
- Backlog `BACKLOG_CHINH_SUA_HE_THONG.md`.
- Báo cáo benchmark chatbot `chatbot-service/debug/benchmark/BAO_CAO_KET_QUA_BENCHMARK.md`.

## Tiến độ

| Giai đoạn | Trạng thái | Kết quả |
|---|---|---|
| S1 | ✅ Xong (27/10/2026) | **S1.1:** sửa R1, R2, R3, R5, R6, R7; benchmark tăng từ 84,5 lên **91,9/100**, 16 lượt chuyển từ trượt sang đạt, 0 lượt chuyển từ đạt sang trượt. **S1.2:** ẩn VNPay trên mobile khi backend tắt. **S1.3:** lưu phản hồi tin nhắn vào `message_feedback`. **S1.4:** xuất báo cáo ra tệp CSV mở bằng Excel. **S1.5:** ghi nhớ ngưỡng tồn kho thấp ở trình duyệt (ngưỡng lưu theo kho vẫn thuộc S4.1). **S1.6:** giới hạn quyền API nhà cung cấp, kho, phiếu nhập. Test: backend 282, frontend 163, chatbot 649, Flutter 18 + 1 bỏ qua, context-eval 54/54 lượt và 18/18 lượt, tất cả đều đạt. Báo cáo bản sao đã đồng bộ |
| S2 | ✅ Xong (27/10/2026) | Bạn cho phép migration V9 chỉ thêm dữ liệu.<br>**Vai trò:** thêm `SALES_STAFF`, `WAREHOUSE_KEEPER`, `WARRANTY_TECHNICIAN`, `DELIVERY_STAFF` và mã quyền của chúng; STAFF giữ làm nhân viên tổng hợp.<br>**Phân quyền:** API theo chức danh. Chuyển trạng thái đơn theo bước (`OrderStaffPolicy`): bán hàng xác nhận / xử lý / hủy, thủ kho chuyển sang xử lý, giao hàng chuyển sang đang giao / đã giao.<br>**Web:** route, menu và trang tổng quan theo nhóm vai trò. 4 tài khoản mẫu `seed.sales`, `seed.warehouse`, `seed.technician`, `seed.delivery`.<br>**Test:** backend 289, frontend 172, đều đạt. Báo cáo bản sao đã đồng bộ (Bảng 3.2, mục 3.8, 4.2, Bảng 4.1, 4.2) |
| S3–S6 | Chưa làm | — |

## Nguyên tắc

1. **Báo cáo đã chốt là đặc tả.** Chỉ làm đúng phần báo cáo mô tả; không thêm chức năng ngoài báo cáo.
2. **Ưu tiên việc có sẵn bảng trong CSDL.** Schema đang khóa (`backend/AGENTS.md`). Việc cần bảng hoặc cột mới được gom vào giai đoạn riêng (S4) và chỉ làm khi bạn cho phép đổi schema cho từng việc.
3. **Mỗi việc đều có test**, và mỗi giai đoạn kết thúc bằng hồi quy toàn bộ:
   - unit / integration backend, frontend, chatbot, flutter;
   - bộ test ngữ cảnh và benchmark chatbot (mốc hiện tại **84,5/100**), không được giảm điểm.
4. **Mỗi giai đoạn kết thúc bằng cập nhật báo cáo**: bảng "mức độ hoàn thành" ở Chương 4–5. Thay đổi về chatbot thì ghi thêm vào `chatbot-service/UPDATECHATBOTLOG.md`.
5. **Duyệt trước từng giai đoạn.** Có thể dừng ở bất kỳ giai đoạn nào nếu hết thời gian; các phần chưa làm vẫn hợp lệ vì báo cáo ghi "thiết kế, chưa cài đặt".

## Phân loại theo điều kiện CSDL (đã kiểm tra schema V1–V8)

| Chức năng | Bảng / cột sẵn có | Cần đổi schema? |
|---|---|---|
| Hóa đơn | `invoices` (invoice_number, issued_date, total_amount, status) | Không |
| Giao vận | `shipments` (employee_id, tracking_number, shipping_provider, shipped_date, delivered_date, status) | Không |
| Đánh giá sản phẩm | `reviews` (rating, review_text, reply_text, employee_id, replied_at, status) | Không |
| Đổi trả | `return_requests`, `return_items` (serial_id, refund_amount, status) | Không |
| Quản trị Q&A / kịch bản chatbot | `chatbot_qna`, `chatbot_scripts` | Không |
| Phản hồi tin nhắn chatbot | `message_feedback` | Không |
| Tích điểm thành viên | cột `customers.loyalty_point` | Không (hạng thành viên có thể suy ra từ điểm theo quy tắc) |
| Xuất báo cáo (BL-01) | Không cần bảng | Không |
| Ngưỡng cảnh báo tồn kho (BL-02) | Không có chỗ lưu | **Có**, trừ khi chỉ lưu phía trình duyệt |
| Tách vai trò STAFF (BL-03) | Bảng `roles` có sẵn; cần thêm dòng dữ liệu | **Có** (migration dữ liệu) |
| Đặt hàng NCC có duyệt, phiếu xuất kho, kiểm kê, điều chuyển kho, nhật ký hệ thống | Không có bảng | **Có** |

---

## S1 — Sửa lỗi và nhất quán nhỏ (rủi ro thấp, làm trước)

| # | Việc | Ghi chú | Khối lượng |
|---|---|---|---|
| S1.1 | **5 lỗi ưu tiên của benchmark chatbot** | Số trong tên model bị đọc thành giá; "bản 512GB" bị chặn; tên sản phẩm thiếu hãng; từ chối "đặt giúp tôi…"; định tuyến câu chính sách và mã đơn không tồn tại. Dự kiến thêm khoảng 19 lượt đạt | Vừa |
| S1.2 | Ẩn VNPay trên app mobile khi backend tắt VNPay | App đang luôn hiện lựa chọn này | Nhỏ |
| S1.3 | Lưu đánh giá tin nhắn chatbot vào `message_feedback` | Hiện chỉ lưu trong bộ nhớ | Nhỏ |
| S1.4 | **BL-01 Xuất báo cáo** (CSV / Excel) trên `/reports` | Quyết định D6 | Nhỏ – vừa |
| S1.5 | **BL-02 Ngưỡng tồn kho**, phương án không đổi schema: chọn ngưỡng trên màn hình báo cáo | Nếu cần lưu theo kho / biến thể thì chuyển sang S4 | Nhỏ |
| S1.6 | **Giới hạn quyền API kho** (phát hiện ở Giai đoạn 5) | `SupplierController`, `WarehouseController` (tất cả endpoint), `GoodsReceiptController` GET và POST hiện **không có `@RequireAnyAuthority`**: tài khoản CUSTOMER cũng gọi được, kể cả tạo / sửa NCC, kho, phiếu nhập. Giới hạn ADMIN / MANAGER / STAFF (theo S2 thì dùng vai trò Thủ kho) cho mọi endpoint, **trừ `GET /api/warehouses`**: khách hàng cần endpoint này để chọn chi nhánh (web `warehouseApi.js`, Flutter `warehouse_repository.dart`). Kèm test 403 cho CUSTOMER. Báo cáo 3.8 đang có ghi chú chờ việc này | Nhỏ |

## S2 — Tác nhân và phân quyền (bắt buộc: đã chọn phương án B ngày 27/10/2026)

| # | Việc | Ghi chú | Khối lượng |
|---|---|---|---|
| S2.1 | **BL-03** Tách STAFF thành Nhân viên bán hàng / Thủ kho / Nhân viên bảo hành / Nhân viên giao hàng | Thêm 4 vai trò (migration dữ liệu, cần cho phép); thay khoảng 15 chỗ `STAFF` trong annotation bằng vai trò tương ứng; sửa `ROLES`, `RequireRole`, menu quản trị web; seed tài khoản mẫu; test phân quyền cho từng vai trò | Vừa (khoảng 1 ngày) |

Vai trò đích lấy theo báo cáo sau khi đã thống nhất tên gọi ở Giai đoạn 2–3: Admin, Quản lý, Nhân viên bán hàng, Thủ kho, Nhân viên kỹ thuật/bảo hành, Nhân viên giao hàng, Khách hàng. Ma trận quyền chi tiết lấy từ đặc tả use case đã chốt.

## S3 — Chức năng đã có bảng trong CSDL (không đổi schema)

Thứ tự theo giá trị khi demo và mức phụ thuộc:

| # | Việc | UC trong báo cáo | Nội dung chính | Khối lượng |
|---|---|---|---|---|
| S3.1 | **Giao vận** (`shipments`) | UC-08, UC-SYS-09, SYS-17 | Khi đơn chuyển SHIPPED thì tạo shipment (nhân viên phụ trách, mã vận đơn, đơn vị vận chuyển); khi DELIVERED thì ghi ngày giao. Khách xem timeline giao hàng | Vừa |
| S3.2 | **Hóa đơn** (`invoices`) | UC-06, UC-SYS-08 | Phát hành hóa đơn khi đơn hoàn tất / đã thanh toán; xem và in hóa đơn (trang in của trình duyệt) | Vừa |
| S3.3 | **Đánh giá sản phẩm** (`reviews`) | Mobile 1.3.2, 3.1.1 | Khách đánh giá sản phẩm đã mua (gắn order_item); nhân viên phản hồi; hiển thị trên trang sản phẩm (web và mobile) | Vừa |
| ~~S3.4~~ | ~~Đổi trả~~ | — | **Bỏ** theo quyết định D3: đổi trả đã bị bỏ khỏi báo cáo, nghiệp vụ sau bán dùng bảo hành | — |
| S3.5 | **Tích điểm / hạng thành viên** (`customers.loyalty_point`) | UC-09, UC-SYS-10 | Cộng điểm khi đơn DELIVERED; hạng tính theo ngưỡng điểm (quy tắc cấu hình trong mã); hiển thị ở hồ sơ khách và trang quản lý khách hàng | Nhỏ – vừa |

## S4 — Chức năng cần đổi schema (chỉ làm khi được cho phép từng việc)

| # | Việc | UC trong báo cáo | Thay đổi schema dự kiến | Khối lượng |
|---|---|---|---|---|
| S4.1 | BL-02 ngưỡng tồn kho lưu theo kho / biến thể | UC-SYS-05 | Thêm cột ngưỡng vào `inventory` hoặc bảng cấu hình | Nhỏ |
| S4.2 | Phiếu xuất kho (xuất bán, xuất bảo hành) | UC-02, UC-SYS-03 | Bảng phiếu xuất và dòng phiếu (kèm Serial) | Lớn |
| S4.3 | Điều chuyển hàng giữa chi nhánh | UC-04 | Bảng phiếu điều chuyển và dòng phiếu (kèm Serial) | Lớn |
| S4.4 | Kiểm kê kho và điều chỉnh chênh lệch | UC-03, UC-SYS-04 | Bảng biên bản kiểm kê và dòng chênh lệch | Lớn |
| S4.5 | Đơn đặt hàng NCC có duyệt | UC-01 | Bảng đơn đặt hàng và dòng đơn; liên kết phiếu nhập | Lớn |
| S4.6 | Nhật ký hệ thống (audit log) | 3.1.1 | Bảng nhật ký thao tác | Vừa |

## S5 — Chatbot (theo báo cáo đã chốt)

| # | Việc | Ghi chú | Khối lượng |
|---|---|---|---|
| S5.1 | **Chatbot trên app mobile** (D5) | Màn hình chat Flutter gọi API chatbot sẵn có: lịch sử, thẻ sản phẩm, bảng so sánh | Vừa |
| S5.2 | **Quản trị dữ liệu chatbot** (UC-17, UC-SYS-18) | Màn hình quản trị: nạp / cập nhật tài liệu tri thức (thay script); Q&A dùng bảng `chatbot_qna`; xem phản hồi tin nhắn | Vừa – lớn |
| S5.3 | Lỗi benchmark nhóm B và ngữ cảnh nâng cao (G4b) | So sánh 3 máy, "laptop rẻ nhất", USD, lỗi gõ, phạm vi tìm kiếm sau so sánh, thứ tự thứ ba / cuối, trả lời câu hỏi lại… | Lớn (làm dần) |
| S5.4 | Chuyển hội thoại cho nhân viên (UC-16 tác nhân phụ) | Dùng cột `employee_id` có sẵn ở `chat_sessions` / `chat_history`. Thuộc diện tùy chọn | Lớn |

## S6 — Đồng bộ lại báo cáo và nghiệm thu

1. Chạy hồi quy toàn bộ và benchmark chatbot (lặp 3 lần); so với mốc 84,5.
2. Cập nhật bảng mức độ hoàn thành ở Chương 4, Chương 5, ảnh chụp màn hình và số liệu kiểm thử.
3. Chụp lại màn hình cho các chức năng mới.

---

## Thứ tự đề xuất và điểm dừng

**S1 → S2 → S3.1 → S3.2 → S3.5 → S5.1 → S3.3 → S5.2 → S4 (nếu được phép) → S5.3–S5.4 → S6.**

Mỗi giai đoạn để lại hệ thống ở trạng thái chạy được và đã kiểm thử, nên có thể dừng ở bất kỳ điểm nào trước buổi bảo vệ.

**Điểm dừng tối thiểu khuyến nghị: hết S1 + S2 + S3.1 + S3.2 + S5.1.** Khi đó các chức năng báo cáo nhấn mạnh nhất (giao hàng, hóa đơn, chatbot trên cả web và mobile) đều demo được.
