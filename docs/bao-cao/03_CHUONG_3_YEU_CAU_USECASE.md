# Giai đoạn 3 — Chương 3 (3.1–3.3): danh sách thay đổi

Đã sửa trực tiếp trong `BaoCaoKhoaLuan_ChinhSua.txt`.

**Căn cứ:** quyết định D1 (giữ tác nhân theo báo cáo, thống nhất tên theo 1.3.1), D2 (giữ phần đã thiết kế), D3 (bỏ đổi trả), D5 (chatbot trên mobile), D6 (bỏ "cấu hình hệ thống"), D8 (ghi chú chức năng lớn hệ thống có thêm).

## 1. Mục 3.1.1 Yêu cầu chức năng

| Thay đổi | Lý do |
|---|---|
| Tiêu đề "Website (dành cho Admin/Quản lý)" → "(dành cho Admin và Quản lý cửa hàng)" | Thống nhất 7 tác nhân |
| Danh sách vai trò phân quyền: 4 → **7 vai trò** theo 1.3.1 | D1 |
| Bỏ "Cấu hình hệ thống"; thêm "nhà cung cấp" vào dòng quản lý danh mục | D6; UC-05 có quản lý NCC |
| Thanh toán ghi rõ "(trực tuyến, COD, trả góp)" | Khớp UC-06, 07, SYS-16 |
| **Thêm** gạch đầu dòng "Quản lý kho: đơn đặt hàng NCC, nhập kho kèm Serial/IMEI, xuất kho, kiểm kê, điều chuyển, cảnh báo tồn kho" | Bản cũ không có yêu cầu về kho, dù chương 3 thiết kế 5 UC kho (UC-01 → 05, SYS-01 → 05) |
| "Quản lý đổi trả hàng và bảo hành" → "Quản lý bảo hành theo Serial/IMEI" | D3 |
| "Nhóm chức năng Mobile App (dành cho khách hàng)" → "dành cho khách hàng (trên Website và Mobile App)" | Bản cũ không nêu chức năng khách hàng trên Website, dù 1.3.2 và UC-SYS-06 / 16 / 17 / 18 đều có |
| "Gửi yêu cầu đổi trả" → "Tra cứu bảo hành, gửi yêu cầu bảo hành" | D3 |

## 2. Mục 3.2 Khảo sát nghiệp vụ

Giữ nguyên toàn bộ (khảo sát thực tế). Chỉ thêm 1 câu cuối mục **3.2.4** nêu ranh giới: hệ thống hỗ trợ bảo hành theo Serial/IMEI; đổi trả được khảo sát nhưng không thuộc phạm vi hệ thống.

## 3. Mục 3.3 Use case

**Bỏ (D3):** khối **UC-13** (Xử lý đổi trả nội bộ, 37 dòng) và **UC-SYS-13** (Xử lý đổi trả / bồi hoàn, 34 dòng, gồm chú thích Hình 44).
- **Mã UC giữ nguyên**, nên hiện có khoảng trống UC-12 → UC-14 và UC-SYS-12 → UC-SYS-14. Lý do: các hình (sơ đồ, chú thích) đang dùng mã này. Việc đánh số lại toàn bộ UC và hình để ở **Giai đoạn 6**, làm một lượt cho đồng bộ.

**Thống nhất tên tác nhân** theo 1.3.1 (không đổi nghiệp vụ):

| Tên cũ | Tên mới | Vị trí |
|---|---|---|
| "Quản lý/Admin" (35 chỗ) | **Quản lý**: UC-03, UC-15, SYS-01, 02, 03, 04, 05, 14. **Admin**: UC-18 (dữ liệu chatbot) | Theo nhiệm vụ ở 1.3.1: Quản lý phụ trách danh mục, duyệt kho, báo cáo; Admin phụ trách người dùng, chatbot |
| "Quản lý/Admin" trong danh sách tác nhân tổng quát | "Quản lý cửa hàng, Admin" | 3.3.1, 3.3.2 |
| "Nhân viên Kỹ thuật/Giao hàng" | **Nhân viên giao hàng** (UC-14, SYS-09); **Nhân viên kỹ thuật/bảo hành** (UC-08: lắp đặt kỹ thuật) | Bản cũ gộp hai tác nhân |
| "Nhân viên Bảo hành", "Nhân viên Kỹ thuật", "Nhân viên ký thuật/Bảo hành" | **Nhân viên kỹ thuật/bảo hành** | Một tác nhân, một tên; sửa luôn lỗi chính tả "ký thuật" |
| "Nhân viên giao vận" | **Nhân viên giao hàng** | Thống nhất với 1.3.1 |
| "Thủ kho xuất (kho A), Thủ kho nhập (kho B)" | "Thủ kho (tại kho xuất và kho nhận)" | Một tác nhân ở hai vị trí (UC-04). Các bước trong luồng vẫn ghi "Thủ kho nhập" để chỉ vị trí |
| "Phụ: Người dùng hệ thống" | "Phụ: Không có" | Không phải tác nhân (UC-16, SYS-15) |
| UC-SYS-01: "đăng nhập với quyền Admin" | "với quyền Quản lý" | Khớp tác nhân chính |

**Các chỉnh sửa khác:**
- Mô tả UC nghiệp vụ tổng quát: "dịch vụ hậu mãi (đổi/trả, bảo hành)" → "(bảo hành)".
- UC-02, SYS-03 (xuất kho): bỏ "đổi trả" khỏi lý do xuất và chứng từ.
- **Chatbot trên mobile (D5):** UC-SYS-18 mô tả thành "hỏi đáp tự động **trên Website hoặc Mobile App**". UC-SYS-16 "mua hàng trên Website" → "trên Website hoặc Mobile App". UC-17 (nghiệp vụ) không gắn kênh nên giữ nguyên.
- **Ghi chú D8** (xóa hoặc chuyển thành nội dung khi đưa sang Word):
  - cuối **UC-SYS-16**: mua sắm theo chi nhánh, giữ chỗ tồn kho, VNPay sandbox (HMAC-SHA512, IPN / Return, đối soát QueryDr), trả góp;
  - cuối **UC-SYS-18**: chatbot so sánh bằng bảng thông số, từ chối so sánh khác danh mục, combo theo ngân sách, hội thoại nhiều lượt, tra cứu giỏ / đơn / bảo hành của chính khách, không giao dịch thay khách.

**Kiểm tra sau khi sửa:**
- 3.3 không còn tên tác nhân cũ (0 chỗ) và không còn "đổi trả".
- Ranh giới các khối xóa đúng: UC-12 kết thúc ở "Tình huống 5", UC-14 nối tiếp.
- File giữ UTF-8 BOM, CRLF.

## 4. Hình cần vẽ lại hoặc kiểm tra

| Hình | Việc | Mã sơ đồ |
|---|---|---|
| **Hình 13** UC nghiệp vụ tổng quát | **Vẽ lại**: 7 tác nhân + 4 đối tác, bỏ UC-13 | `so-do/hinh13_usecase_nghiep_vu_tong_quat.puml` |
| **Hình 31** UC hệ thống tổng quát | **Vẽ lại**: 7 tác nhân + 2 hệ thống ngoài, bỏ UC-SYS-13 | `so-do/hinh31_usecase_he_thong_tong_quat.puml` |
| Hình 44 (UC-SYS-13) và hình UC nghiệp vụ của UC-13 | **Xóa** khỏi Word | — |
| Các hình UC chi tiết có nhãn tác nhân cũ ("Quản lý/Admin", "Nhân viên Kỹ thuật/Giao hàng", "Nhân viên Bảo hành") | **Kiểm tra nhãn** trong ảnh, sửa cho khớp tên mới. Tôi không đọc được nội dung ảnh trong bản txt | Hình 15, 16, 21, 25, 26, 27, 30, 32, 33, 35, 36, 40, 42, 43, 45, 50 |

Hai file `.puml` chưa được render thử, vì máy không có PlantUML. Cách dùng: dán vào plantuml.com hoặc plugin PlantUML của VS Code, xuất PNG / SVG rồi chèn vào Word.

## 5. Để lại cho giai đoạn sau

- **Giai đoạn 4**: 3.4.10 (sơ đồ hoạt động đổi trả, Hình 60) và mục lục 3.4.10; ERD 05 / sơ đồ lớp "Return & Warranty"; đoạn tổng quan ERD còn nhắc "đổi trả".
- **Giai đoạn 6**: đánh số lại UC (bỏ khoảng trống 13) và toàn bộ hình; mục lục (dòng 185 "UC-13"); tóm tắt khóa luận.
