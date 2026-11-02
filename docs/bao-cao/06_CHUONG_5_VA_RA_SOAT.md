# Giai đoạn 6 — Chương 5, tài liệu tham khảo và rà soát tổng thể

Đã sửa trực tiếp trong `BaoCaoKhoaLuan_ChinhSua.txt` (UTF-8 BOM, CRLF giữ nguyên). Đây là giai đoạn cuối của việc chỉnh sửa báo cáo. Sau giai đoạn này, báo cáo chỉ còn chờ ba việc: bạn chèn ảnh, bạn chuyển sang Word, và tôi cập nhật lại sau khi sửa hệ thống (bước S6).

## 1. Phần đầu báo cáo

| Mục | Thay đổi |
|---|---|
| Tóm tắt khóa luận | **Viết lại** theo hệ thống thật: kiến trúc 4 thành phần; các nghiệp vụ đã có (kho theo Serial/IMEI, giữ chỗ tồn kho, COD / VNPay sandbox / trả góp, bảo hành tự động); chatbot kiến trúc lai; hơn 1.000 ca kiểm thử tự động; benchmark 84,5/100. Nêu rõ chức năng chưa cài đặt và việc chưa triển khai lên máy chủ. Bỏ "xuất hóa đơn, đổi trả, giao hàng" và câu "hệ thống hoàn chỉnh, vận hành thực tế" |
| Mục lục | Chương 2 thay bằng **34 tiêu đề thật** (bản cũ là dàn ý 2.1–2.5 không còn đúng). 3.4.11–3.4.13 → **3.4.10–3.4.12** (lấp khoảng trống của 3.4.10 đã bỏ). 3.7.4.4 → "Bảo hành (Warranty)". Mã UC theo số mới |
| Danh mục hình ảnh | Trước đây trống, nay **điền đủ 124 hình** theo số mới |
| Danh mục bảng biểu | Trước đây trống, nay **điền đủ 12 bảng** |
| Danh mục từ viết tắt | Thêm 15 từ có dùng trong báo cáo (ACID, COD, CSRF, HMAC, HTTP, IPN, JPA, JSON, NCC, ORM, SKU, UC, UML, VAT, XSS), sắp xếp theo A–Z |

**Số trang trong mục lục chưa đúng.** Các dòng mới chưa có số trang, các dòng cũ giữ số trang cũ. Trong Word, dùng mục lục tự động (References → Table of Contents → Update Table), tương tự với danh mục hình và bảng (Insert Table of Figures). Nếu không dùng mục lục tự động, cần dò lại số trang sau khi chèn đủ ảnh.

## 2. Đánh số lại

### 2.1 Use Case: bỏ khoảng trống số 13 (UC đổi trả đã bỏ theo D3)

| Cũ | Mới | | Cũ | Mới |
|---|---|---|---|---|
| UC-14 Sửa chữa / chuyển bảo hành hãng | **UC-13** | | UC-SYS-14 Xem báo cáo quản trị | **UC-SYS-13** |
| UC-15 Xem báo cáo quản trị | **UC-14** | | UC-SYS-15 Quản lý người dùng | **UC-SYS-14** |
| UC-16 Quản lý người dùng & phân quyền | **UC-15** | | UC-SYS-16 Đặt hàng online | **UC-SYS-15** |
| UC-17 Tương tác với chatbot | **UC-16** | | UC-SYS-17 Tra cứu lịch sử đơn | **UC-SYS-16** |
| UC-18 Quản lý dữ liệu chatbot | **UC-17** | | UC-SYS-18 Tương tác với chatbot | **UC-SYS-17** |
| | | | UC-SYS-19 Quản lý dữ liệu chatbot | **UC-SYS-18** |

- **Đã sửa:**
  - toàn bộ bản sao (15 chỗ);
  - nhãn trong `so-do/hinh13…puml` và `so-do/hinh31…puml`;
  - `KE_HOACH_SUA_HE_THONG.md`.
- **Giữ số cũ:** các file ghi chép giai đoạn trước (`01_…` → `05_…`), vì đó là lịch sử làm việc.
- **Việc của bạn:** các ảnh UC chi tiết trong Word nếu có in mã UC trong hình (UC-13 → UC-17, UC-SYS-13 → UC-SYS-18 mới), cần sửa mã trong ảnh.

### 2.2 Hình: đánh số theo chương

**Cách đánh số mới:** Hình *chương.thứ tự*, giống Chương 2 đang dùng.
- Chương 1: 7 hình.
- Chương 2: 2 hình, giữ nguyên.
- Chương 3: 95 hình.
- Chương 4: 20 hình.

**Lỗi của bản gốc đã được xử lý:**
- Hình 67 và Hình 81 bị trùng số, nay đã tách riêng.
- Các số bị hụt (44, 60, 75, do bỏ mục) đã được lấp.
- Các hình mới ghi tạm `3.x` / `4.x` đã có số chính thức.

**Tham chiếu trong văn bản đã sửa theo số mới:**
- ghi chú kiểm tra Hình 3.60 (thay cho Hình 69 / 75 cũ);
- đoạn giới thiệu sơ đồ lớp ở 3.7.2;
- ghi chú sơ đồ chatbot ở 3.7.4.6–7;
- cột "Hình" trong Bảng 3.3 và 3.4. Màn hình nào không có hình ở Chương 3 thì ghi "Chương 4".

**Bảng đối chiếu số hình** nằm ở cuối tài liệu này. Dùng bảng đó khi sửa chú thích trong Word.

### 2.3 Bảng

**Đánh số lại:**
- Chương 3 chưa từng có Bảng 3.1–3.3. Bảng đầu tiên của chương là bảng 8 nhóm lớp ở 3.7.2 (đang ghi "Bảng 3.x"), nay là **Bảng 3.1**.
- Ba bảng thêm ở Giai đoạn 5 đổi số tương ứng: 3.4 → **3.2**, 3.5 → **3.3**, 3.6 → **3.4**.

**Vị trí chú thích:** mọi chú thích bảng được **đặt phía trên bảng** (bảng ghi tên phía trên, hình ghi tên phía dưới). Bảng 2.2–2.4 và các bảng mới trước đây ghi tên phía dưới.

**Bảng 3.1** trước đây đánh số "1, 2, 3, 4, 5, 5b, 6, 7", nay là 1–8. Câu dẫn sửa thành "8 nhóm lớp", kèm giải thích vì sao sơ đồ tổng quát chỉ có 7 package: Chatbot và RAG cùng thuộc chatbot-service.

## 3. Chương 2

- **Bỏ mục "TÀI LIỆU THAM KHẢO CỦA CHƯƠNG 2"** và gộp vào danh mục chung cuối báo cáo. Số [1]–[41] giữ nguyên nên các trích dẫn trong Chương 2 không phải sửa.
- **Tiêu đề chương:** "CHƯƠNG 2 : CƠ SỞ LÝ THUYẾT" sửa dấu cách thành "CHƯƠNG 2: CƠ SỞ LÝ THUYẾT"; mục lục ghi giống hệt.
- **2.5.10:** ý "Ứng dụng di động chưa tích hợp chatbot" đúng với hiện tại. Đã thêm ghi chú xóa ý này khi làm xong S5.1.
- **Bảng 2.4:** sửa lỗi hai dấu chấm ở cuối chú thích.

## 4. Chương 5 (viết lại)

| Mục | Nội dung |
|---|---|
| 5.1.1 Kết quả đạt được | Phân tích: 17 UC nghiệp vụ, 18 UC hệ thống, ERD 49 bảng. Xây dựng: 4 thành phần, các nhóm chức năng theo Bảng 4.1, 4 điểm kỹ thuật (giữ chỗ tồn kho có khóa, Serial/IMEI xuyên suốt, VNPay có chữ ký và đối soát, bảo mật phiên). Chatbot: kiến trúc lai, 84,5/100, an toàn 99,3. Kiểm thử: 1.068 ca tự động |
| 5.1.2 Hạn chế | **Trung thực, có số liệu**: danh sách chức năng chưa cài đặt; điểm yếu chatbot (ngữ cảnh nâng cao 54,5, dữ liệu chính sách mẫu, mô hình 4B, chưa có ANN); chỉ chạy cục bộ, VNPay sandbox, chỉ Android; chưa kiểm thử với người dùng thật và kiểm thử tải. Bỏ "12 tuần" (D9) |
| 5.2 Hướng phát triển | Hoàn thiện chức năng đã thiết kế (lấy từ cột "Chưa cài đặt" của Bảng 4.1); nâng chatbot; triển khai máy chủ, CI/CD, phát hành app; VNPay thật, đơn vị vận chuyển; app cho nhân viên; hệ gợi ý |

Bản cũ ghi "đã cài đặt đầy đủ… giao hàng, đổi trả, bảo hành…". Câu này không đúng với hệ thống nên đã bỏ.

## 5. Tài liệu tham khảo (một danh mục chung, kiểu IEEE)

- **[1]–[41]:** tài liệu của Chương 2, giữ nguyên.
- **[42]–[47]:** 6 tài liệu của danh mục cũ, chuyển sang định dạng IEEE:
  - bài giảng Phân tích hệ thống thông tin;
  - bài giảng Công nghệ phần mềm;
  - giáo trình Phân tích thiết kế hệ thống thông tin hướng đối tượng;
  - sách Sommerville;
  - website Nguyễn Kim;
  - website Chợ Lớn.
- **Đã bỏ:** "Object Oriented Programming using C#" (dự án không dùng C#).

**Việc của bạn:** [42]–[47] hiện **chưa được trích dẫn** trong thân bài. Nếu muốn, thêm trích dẫn ở các chỗ phù hợp:
- [46], [47] ở phần khảo sát 1.4;
- [42], [44] ở đầu Chương 3 (phương pháp phân tích thiết kế);
- [45] ở 4.4 (kiểm thử).

## 6. Rà soát tổng thể

| Kiểm tra | Kết quả |
|---|---|
| Tham chiếu chéo "mục x.y" trong Chương 2–5 | 17 tham chiếu, đều trỏ đúng mục đang tồn tại |
| Thuật ngữ | Thống nhất "Serial/IMEI" (sửa 8 chỗ "serial/IMEI"). "Website", "Mobile App" dùng thống nhất ở tiêu đề và mô tả hệ thống. "kho/chi nhánh" giữ nguyên vì cố ý dùng song song: một chi nhánh là một kho |
| Chương 2 khớp các chương sau | Công nghệ ở Chương 2 trùng với 4.1. Pipeline chatbot 2.5.3 khớp 4.3.5 và sơ đồ lớp 3.7.4.6–7. Cơ chế JWT, CSRF ở 2.6.1 khớp 3.8 |
| Đổi trả (D3) | Chỉ còn ở ba nơi: phần khảo sát thực tế (1.4, 3.2.4, Hình 3.4); hai bảng ReturnRequest / ReturnItem ở 3.6.2.5, ghi rõ là bảng thiết kế sẵn, nghiệp vụ không thuộc phạm vi; các câu nêu rõ "không thuộc phạm vi" |
| Định dạng file | UTF-8 BOM, CRLF; `diff` chỉ ra đúng các vùng đã sửa |

## 7. Danh sách kiểm tra cuối cùng (khi chuyển sang Word)

**Dòng đánh dấu còn lại trong bản sao:**

| Loại dòng đánh dấu | Số lượng | Việc cần làm |
|---|---|---|
| `[CẦN VẼ LẠI – ĐÃ PHÁC THẢO: …]`, `[CẦN CHÈN HÌNH – ĐÃ PHÁC THẢO: …]` | 18 | Render file `.puml` ghi trong dòng, thay ảnh, xóa dòng |
| `[CẦN BỔ SUNG HÌNH: …]` | 12 | Chụp màn hình, đặt đúng chú thích Hình 3.84–3.95, 4.1–4.20, xóa dòng |
| `[CẦN KIỂM TRA HÌNH: …]` | 1 | So sánh Hình 69 và Hình 75 của bản gốc, chọn ảnh cho Hình 3.60 |
| `[CẦN BỔ SUNG: …]` | 2 | Cấu hình máy các thành viên khác (4.1); nội dung bạn tự điền |
| `[CẦN CẬP NHẬT SAU KHI SỬA HỆ THỐNG …]`, `[GHI CHÚ – …]` | 18 | **Giữ lại** tới khi sửa hệ thống xong; tôi cập nhật ở bước S6 |

**Việc trong Word:**
1. Chuyển các bảng dạng tab thành bảng Word: Bảng 3.2, 3.3, 3.4, 4.1, 4.2, 4.3, 4.4.
2. Cập nhật mục lục và danh mục hình, bảng.
3. Kiểm tra mã UC trong các ảnh sơ đồ UC chi tiết (mục 2.1).
4. Phụ lục: dòng "[Đính kèm các biểu mẫu khảo sát…]" còn nguyên từ bản gốc, bạn tự quyết định nội dung.
5. Đọc lại toàn bộ một lượt trên Word: font, cỡ chữ, giãn dòng, lề, ngắt trang trước mỗi chương.

---

## Phụ lục: bảng đối chiếu số hình

Chương 2 (Hình 2.1, 2.2) không đổi nên không có trong bảng.

| Số mới | Số trong bản gốc | Chú thích |
|---|---|---|
| Hình 1.1 | Hình 1 | Phiếu Nhập Kho |
| Hình 1.2 | Hình 2 | Phiếu Kiểm Kê Kho Hàng |
| Hình 1.3 | Hình 3 | Phiếu Tiếp Nhận Bảo Hành |
| Hình 1.4 | Hình 4 | Phiếu Hóa Đơn Giá Trị Gia Tăng |
| Hình 1.5 | Hình 5 | Phiếu Xuất Kho |
| Hình 1.6 | Hình 6 | Phiếu Bảo Hành Sản Phẩm |
| Hình 1.7 | Hình 7 | Phiếu Báo Cáo Doanh Thu |
| Hình 3.1 | Hình 8 | Sơ đồ quy trình Mua hàng & Nhập kho |
| Hình 3.2 | Hình 9 | Sơ đồ quy trình Bán hàng |
| Hình 3.3 | Hình 10 | Sơ đồ quy trình Giao hàng & Lắp đặt |
| Hình 3.4 | Hình 11 | Sơ đồ quy trình Bảo hành & Đổi trả |
| Hình 3.5 | Hình 12 | Sơ đồ quy trình Kiểm kê & Chuyển kho |
| Hình 3.6 | Hình 13 | Sơ đồ Use-Case nghiệp vụ tổng quát |
| Hình 3.7 | Hình 14 | Sơ đồ Use-Case nghiệp vụ - Quản lý Đơn đặt hàng NCC (Nhà cung cấp) |
| Hình 3.8 | Hình 15 | Sơ đồ Use-Case nghiệp vụ - Lập Biên bản kiểm kê kho |
| Hình 3.9 | Hình 16 | Sơ đồ Use-Case nghiệp vụ - Phê duyệt & Cập nhật chênh lệch tồn kho |
| Hình 3.10 | Hình 17 | Sơ đồ Use-Case nghiệp vụ - Điều chuyển hàng giữa các kho/chi nhánh |
| Hình 3.11 | Hình 18 | Sơ đồ Use-Case nghiệp vụ - Quản lý danh mục hàng hóa và Nhà cung cấp |
| Hình 3.12 | Hình 19 | Sơ đồ Use-Case nghiệp vụ - Xử lý bán hàng và lập hóa đơn |
| Hình 3.13 | Hình 20 | Sơ đồ Use-Case nghiệp vụ - Xử lý bán hàng trả góp |
| Hình 3.14 | Hình 21 | Sơ đồ Use-Case nghiệp vụ - Lập lịch Giao hàng & Lắp đặt kỹ thuật |
| Hình 3.15 | Hình 22 | Sơ đồ Use-Case nghiệp vụ - Quản lý thông tin & Hạng thành viên Khách hàng |
| Hình 3.16 | Hình 23 | Sơ đồ Use-Case nghiệp vụ - Đặt hàng Online |
| Hình 3.17 | Hình 24 | Sơ đồ Use-Case nghiệp vụ - Tra cứu Lịch sử đơn hàng & Trạng thái giao hàng |
| Hình 3.18 | Hình 25 | Sơ đồ Use-Case nghiệp vụ - Tiếp nhận & Tra cứu Bảo hành |
| Hình 3.19 | Hình 26 | Sơ đồ Use-Case nghiệp vụ - Xử lý Sửa chữa / Chuyển Bảo hành Hãng |
| Hình 3.20 | Hình 27 | Sơ đồ Use-Case nghiệp vụ - Xem Báo cáo Quản trị |
| Hình 3.21 | Hình 28 | Sơ đồ Use-Case nghiệp vụ - Quản lý Người dùng & Phân quyền hệ thống |
| Hình 3.22 | Hình 29 | Sơ đồ Use-Case nghiệp vụ - Tương tác với Chatbot tư vấn |
| Hình 3.23 | Hình 30 | Sơ đồ Use-Case nghiệp vụ - Quản lý Kịch bản/Dữ liệu Chatbot |
| Hình 3.24 | Hình 31 | Sơ đồ Use-Case hệ thống tổng quát |
| Hình 3.25 | Hình 32 | Sơ đồ Use-Case hệ thống - Quản lý danh mục sản phẩm và mã Serial/IMEI |
| Hình 3.26 | Hình 33 | Sơ đồ Use-Case hệ thống - Lập và Duyệt Phiếu nhập kho |
| Hình 3.27 | Hình 34 | Sơ đồ Use-Case hệ thống - Xuất kho |
| Hình 3.28 | Hình 35 | Sơ đồ Use-Case hệ thống - Thực hiện kiểm kê kho |
| Hình 3.29 | Hình 36 | Sơ đồ Use-Case hệ thống - Cấu hình cảnh báo tồn kho |
| Hình 3.30 | Hình 37 | Sơ đồ Use-Case hệ thống - Tra cứu và tìm kiếm sản phẩm trên Website |
| Hình 3.31 | Hình 38 | Sơ đồ Use-Case hệ thống - Kết nối & Xử lý Đơn hàng Trả góp |
| Hình 3.32 | Hình 39 | Sơ đồ Use-Case hệ thống - In Hóa đơn VAT & Phiếu Giao hàng - Lắp đặt |
| Hình 3.33 | Hình 40 | Sơ đồ Use-Case hệ thống - Cập nhật Trạng thái Giao hàng & Lắp đặt |
| Hình 3.34 | Hình 41 | Sơ đồ Use-Case hệ thống - Quản lý Thông tin & Hạng thành viên Khách hàng |
| Hình 3.35 | Hình 42 | Sơ đồ Use-Case hệ thống - Tra cứu Thông tin Bảo hành |
| Hình 3.36 | Hình 43 | Sơ đồ Use-Case hệ thống - Lập Phiếu tiếp nhận & Cập nhật Trạng thái Bảo hành |
| Hình 3.37 | Hình 45 | Sơ đồ Use-Case hệ thống UC-SYS-13 - Xem Báo cáo Quản trị |
| Hình 3.38 | Hình 46 | Sơ đồ Use-Case hệ thống - Quản lý Người dùng |
| Hình 3.39 | Hình 47 | Sơ đồ Use-Case hệ thống - Đặt hàng Online |
| Hình 3.40 | Hình 48 | Sơ đồ Use-Case hệ thống - Tra cứu Lịch sử đơn hàng & Trạng thái giao hàng |
| Hình 3.41 | Hình 49 | Sơ đồ Use-Case hệ thống  - Tương tác với Chatbot tư vấn |
| Hình 3.42 | Hình 50 | Sơ đồ Use-Case hệ thống - Quản lý Kịch bản/Dữ liệu Chatbot |
| Hình 3.43 | Hình 51 | Sơ đồ hoạt động chức năng Quản lý Đơn đặt hàng NCC (UC-01) |
| Hình 3.44 | Hình 52 | Sơ đồ hoạt động chức năng Xuất kho (UC-02) |
| Hình 3.45 | Hình 53 | Sơ đồ hoạt động chức năng Kiểm kê kho & Cập nhật chênh lệch tồn kho (UC-03) |
| Hình 3.46 | Hình 54 | Sơ đồ hoạt động chức năng Điều chuyển hàng giữa các kho/chi nhánh (UC-04) |
| Hình 3.47 | Hình 55 | Sơ đồ hoạt động chức năng Xử lý bán hàng và lập hóa đơn (UC-06) |
| Hình 3.48 | Hình 56 | Sơ đồ hoạt động chức năng Xử lý bán hàng trả góp (UC-07) |
| Hình 3.49 | Hình 57 | Sơ đồ hoạt động chức năng Lập lịch Giao hàng & Lắp đặt kỹ thuật (UC-08) |
| Hình 3.50 | Hình 58 | Sơ đồ hoạt động chức năng Quản lý thông tin & Hạng thành viên Khách hàng (UC-09) |
| Hình 3.51 | Hình 59 | Sơ đồ hoạt động chức năng Tiếp nhận & Tra cứu Bảo hành (UC-12) |
| Hình 3.52 | Hình 61 | Sơ đồ hoạt động chức năng Xem Báo cáo Quản trị (UC-14) |
| Hình 3.53 | Hình 62 | Sơ đồ hoạt động chức năng Đặt hàng Online (UC-10) |
| Hình 3.54 | Hình 63 | Sơ đồ hoạt động chức năng Tra cứu Lịch sử đơn hàng & Trạng thái giao hàng (UC-11) |
| Hình 3.55 | Hình 64 | Sơ đồ tuần tự chức năng Quản lý Danh mục Sản phẩm & Mã Serial/IMEI |
| Hình 3.56 | Hình 65 | Sơ đồ tuần tự chức năng Lập & Duyệt Phiếu nhập kho |
| Hình 3.57 | Hình 66 | Sơ đồ tuần tự chức năng Lập Phiếu xuất kho |
| Hình 3.58 | Hình 67 | Sơ đồ tuần tự chức năng Thực hiện Kiểm kê kho |
| Hình 3.59 | Hình 68 | Sơ đồ tuần tự chức năng Cấu hình Cảnh báo Tồn kho |
| Hình 3.60 | Hình 69 | Sơ đồ tuần tự chức năng Tra cứu, tìm kiếm và xem chi tiết sản phẩm (lọc theo giá, thương hiệu) |
| Hình 3.61 | Hình 70 | Sơ đồ tuần tự chức năng Kết nối & Xử lý Đơn hàng Trả góp |
| Hình 3.62 | Hình 71 | Sơ đồ tuần tự chức năng In Hóa đơn VAT & Phiếu Giao hàng - Lắp đặt |
| Hình 3.63 | Hình 72 | Sơ đồ tuần tự chức năng Quản lý Thông tin & Hạng thành viên Khách hàng |
| Hình 3.64 | Hình 73 | Sơ đồ tuần tự chức năng Tra cứu Thông tin Bảo hành |
| Hình 3.65 | Hình 74 | Sơ đồ tuần tự chức năng Lập Phiếu tiếp nhận & Cập nhật Trạng thái Bảo hành |
| Hình 3.66 | (hình mới) | Sơ đồ lớp phân tích tổng quát của hệ thống |
| Hình 3.67 | Hình 76 | Mô hình dữ liệu (ERD) tổng quát của hệ thống |
| Hình 3.68 | Hình 77 | ERD phân hệ quản lý người dùng và phân quyền |
| Hình 3.69 | Hình 78 | ERD phân hệ danh mục và sản phẩm (Product Catalog) |
| Hình 3.70 | Hình 79 | ERD phân hệ nhà cung cấp và kho hàng (Supplier & Warehouse) |
| Hình 3.71 | Hình 80 | ERD phân hệ giỏ hàng và đơn hàng (Shopping Cart & Order) |
| Hình 3.72 | Hình 81 | ERD phân hệ hậu mãi và chăm sóc khách hàng |
| Hình 3.73 | Hình 67 | ERD phân hệ chatbot và hội thoại (Chatbot & Conversation) |
| Hình 3.74 | Hình 81 | ERD phân hệ kho tri thức RAG và chỉ mục ngữ nghĩa sản phẩm |
| Hình 3.75 | Hình 82 | Sơ đồ lớp thiết kế tổng quát của hệ thống (toàn bộ 7 package) |
| Hình 3.76 | Hình 83 | Sơ đồ lớp phân hệ Người dùng và Phân quyền (User & Access Control) |
| Hình 3.77 | Hình 84 | Sơ đồ lớp phân hệ Sản phẩm (Product Catalog) |
| Hình 3.78 | Hình 85 | Sơ đồ lớp phân hệ Nhà cung cấp và Kho hàng (Supplier & Warehouse) |
| Hình 3.79 | Hình 86 | Sơ đồ lớp phân hệ Giỏ hàng và Đơn hàng (Shopping Cart & Order) |
| Hình 3.80 | Hình 87 | Sơ đồ lớp phân hệ Bảo hành (Warranty) |
| Hình 3.81 | Hình 88 | Sơ đồ lớp phân hệ Khuyến mãi và Đánh giá (Promotion & Review) |
| Hình 3.82 | Hình 89 | Sơ đồ lớp phân hệ Chatbot và Hội thoại (Chatbot & Conversation) |
| Hình 3.83 | Hình 90 | Sơ đồ lớp phân hệ Kho tri thức RAG (RAG Knowledge Base) |
| Hình 3.84 | (hình mới) | Giao diện trang chủ Website |
| Hình 3.85 | (hình mới) | Giao diện chi tiết sản phẩm |
| Hình 3.86 | (hình mới) | Giao diện giỏ hàng và thanh toán |
| Hình 3.87 | (hình mới) | Giao diện trợ lý AI tư vấn sản phẩm |
| Hình 3.88 | (hình mới) | Giao diện quản lý sản phẩm |
| Hình 3.89 | (hình mới) | Giao diện lập phiếu nhập kho kèm Serial/IMEI |
| Hình 3.90 | (hình mới) | Giao diện quản lý đơn hàng |
| Hình 3.91 | (hình mới) | Giao diện báo cáo thống kê |
| Hình 3.92 | (hình mới) | Giao diện trang chủ và chi tiết sản phẩm trên Mobile App |
| Hình 3.93 | (hình mới) | Giao diện giỏ hàng và thanh toán trên Mobile App |
| Hình 3.94 | (hình mới) | Giao diện đơn hàng và bảo hành trên Mobile App |
| Hình 3.95 | (hình mới) | Giao diện chatbot tư vấn trên Mobile App |
| Hình 4.1 | (hình mới) | Giao diện đăng nhập |
| Hình 4.2 | (hình mới) | Giao diện quản lý người dùng và phân quyền |
| Hình 4.3 | (hình mới) | Giao diện danh sách sản phẩm |
| Hình 4.4 | (hình mới) | Giao diện thêm sản phẩm |
| Hình 4.5 | (hình mới) | Giao diện quản lý khuyến mãi |
| Hình 4.6 | (hình mới) | Giao diện lập phiếu nhập kho |
| Hình 4.7 | (hình mới) | Giao diện tồn kho theo chi nhánh |
| Hình 4.8 | (hình mới) | Giao diện tra cứu Serial/IMEI |
| Hình 4.9 | (hình mới) | Giao diện giỏ hàng |
| Hình 4.10 | (hình mới) | Giao diện thanh toán |
| Hình 4.11 | (hình mới) | Giao diện kết quả thanh toán VNPay |
| Hình 4.12 | (hình mới) | Giao diện quản lý đơn hàng |
| Hình 4.13 | (hình mới) | Giao diện chi tiết đơn hàng và cập nhật trạng thái |
| Hình 4.14 | (hình mới) | Giao diện xử lý phiếu bảo hành |
| Hình 4.15 | (hình mới) | Chatbot gợi ý sản phẩm theo ngân sách |
| Hình 4.16 | (hình mới) | Chatbot so sánh hai sản phẩm bằng bảng thông số |
| Hình 4.17 | (hình mới) | Chatbot trả lời câu hỏi nối tiếp theo ngữ cảnh |
| Hình 4.18 | (hình mới) | Giao diện báo cáo thống kê |
| Hình 4.19 | (hình mới) | Giao diện Mobile App: trang chủ, chi tiết sản phẩm, giỏ hàng |
| Hình 4.20 | (hình mới) | Giao diện Mobile App: thanh toán, đơn hàng, bảo hành |
