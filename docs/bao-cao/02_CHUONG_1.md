# Giai đoạn 2 — Chương 1: danh sách thay đổi

Đã sửa trực tiếp trong `BaoCaoKhoaLuan_ChinhSua.txt` (số dòng theo bản sao).

**Căn cứ:** quyết định D1, D2, D3, D5, D8 (ngày 27/10/2026) trong `01_MA_TRAN_DOI_CHIEU.md`:
- giữ phạm vi đã thiết kế;
- giữ tác nhân theo báo cáo nhưng thống nhất tên gọi;
- bỏ đổi trả;
- giữ chatbot trên mobile;
- ghi chú các chức năng lớn mà hệ thống có nhưng báo cáo chưa nêu.

| Mục | Dòng | Thay đổi | Lý do |
|---|---|---|---|
| 1.2 Mục tiêu (gạch đầu dòng chatbot) | 304 | "chatbot theo mô hình RAG… dựa trên nguồn tri thức" → chatbot **trên Website và Mobile App**, **kết hợp LLM với dữ liệu thực tế** của cửa hàng và RAG trên tài liệu | Khớp chương 2 (kiến trúc lai) và D5 |
| 1.3.1 Đối tượng sử dụng | 310–317 | Từ **5** thành **7** đối tượng: tách **Admin** và **Quản lý cửa hàng**; "Nhân viên kho" → **Thủ kho (nhân viên kho)**; thêm **Nhân viên kỹ thuật/bảo hành**; mô tả nhiệm vụ từng tác nhân theo đúng các use case ở chương 3. Thêm câu về **đối tác bên ngoài** (cổng thanh toán, đơn vị tài chính trả góp, đơn vị bảo hành hãng). Bỏ "cấu hình hệ thống" (D6) | Báo cáo cũ có 5 đối tượng ở chương 1 nhưng 7 tác nhân ở chương 3, gộp Admin / Quản lý không thống nhất và thiếu nhân viên bảo hành (01b mục 1.2) |
| 1.3.2 Phạm vi Website | 320 | Bỏ "quản lý đổi trả hàng" (giữ "quản lý bảo hành") | D3 |
| 1.3.2 Phạm vi Mobile | 321 | "yêu cầu đổi trả" → "tra cứu bảo hành và gửi yêu cầu bảo hành" | D3; hệ thống thật có phần này |
| 1.3.2 Ghi chú | 322 (mới) | Dòng `[GHI CHÚ – CHỨC NĂNG HỆ THỐNG CHƯA CÓ TRONG BÁO CÁO: …]` nêu 3 chức năng lớn: mua sắm theo chi nhánh, thanh toán VNPay sandbox kèm đối soát, chatbot so sánh / combo theo ngân sách | D8. **Xóa dòng này** khi đưa sang Word, hoặc chuyển thành nội dung chính thức nếu bạn muốn |
| 1.3.2 Ngoài phạm vi | 323 | "quản lý kho toàn diện theo từng lô hàng, chi tiết nhập/xuất kho" → "quản lý kho theo từng lô hàng nhập" | Bản cũ tự mâu thuẫn: loại "nhập/xuất kho" khỏi phạm vi trong khi chương 3 thiết kế xuất kho, kiểm kê, điều chuyển (UC-02 → 04) |
| 1.4.1 | 329 | "[Bổ sung…]" → "[CẦN BỔ SUNG: …]" | Thống nhất ký hiệu chỗ cần bạn bổ sung |
| 1.4.3 | 336 | Chatbot "dựa trên công nghệ RAG" → "kết hợp LLM với dữ liệu thực tế của cửa hàng và kỹ thuật RAG" | Thống nhất với 1.2 |

**Không đổi:**
- 1.1 lý do chọn đề tài, 1.5 bố cục.
- Phần khảo sát 1.4.1–1.4.2.
- "12 tuần" (D9).

**Lưu ý khi chuyển sang Word:**
- 1.4.4: các dòng như "Mẫu Phiếu Bảo Hành File Excel… 2021", "| Cogover Blog" trong bản txt là **văn bản thay thế (alt text) của ảnh**, không phải chữ hiển thị. Nên sửa alt text trong Word (chuột phải ảnh → *Edit Alt Text*) cho gọn, hoặc bỏ qua.
- Chú thích "Hình 1 … Hình 7" của chương 1 nên đổi theo kiểu "Hình 1.1 …" cho thống nhất với chương 2 (đã dùng "Hình 2.1"). Việc này để ở Giai đoạn 6, khi đánh số lại toàn bộ.

**Ảnh hưởng tới các giai đoạn sau:**
- Chương 3 phải dùng **đúng 7 tác nhân** ở 1.3.1. Sửa các tên gọi không thống nhất: "Quản lý/Admin", "Nhân viên Kỹ thuật/Giao hàng", "Người dùng hệ thống", "Thủ kho xuất/nhập".
- Phần "Tóm tắt khóa luận" (dòng 130) vẫn nhắc "đổi trả, xuất hóa đơn…"; sẽ viết lại ở Giai đoạn 6.
- Kế hoạch sửa hệ thống: S2 tạo các vai trò khớp 7 tác nhân này.
