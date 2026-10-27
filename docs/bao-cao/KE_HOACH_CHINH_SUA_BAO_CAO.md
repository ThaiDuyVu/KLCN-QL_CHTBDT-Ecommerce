# Kế hoạch rà soát và chỉnh sửa báo cáo khóa luận

**Mục tiêu**: báo cáo (`backend/docs/BaoCaoKhoaLuan.txt`, bản xuất từ Word) và project mô tả **cùng một hệ thống**. Báo cáo được viết trước khi xây dựng, nên mỗi phần được đối chiếu với mã nguồn (nguồn sự thật) rồi sửa báo cáo. Chỉ chỉnh nhẹ project khi sửa code hợp lý hơn sửa báo cáo, và việc đó cần bạn duyệt.

**Cách làm mỗi giai đoạn:**
1. Tôi đối chiếu phần báo cáo đó với mã nguồn / CSDL / giao diện thật.
2. Tôi **sửa trực tiếp vào bản sao** `docs/bao-cao/BaoCaoKhoaLuan_ChinhSua.txt`. Bản gốc `backend/docs/BaoCaoKhoaLuan.txt` giữ nguyên để đối chiếu.
3. Kèm theo mỗi giai đoạn: danh sách thay đổi, các hình cần vẽ lại kèm mã sơ đồ (PlantUML / Mermaid), và các điểm cần bạn quyết định.
4. Bạn duyệt và chuyển nội dung sang Word, rồi chuyển giai đoạn sau.

Đầu ra mỗi giai đoạn nằm trong `docs/bao-cao/`.

---

## Giai đoạn 1 — Ma trận đối chiếu báo cáo ↔ hệ thống (nền tảng cho mọi giai đoạn sau)

**Làm gì:** rà từng chức năng, use case, thực thể, công nghệ được nêu trong báo cáo và gắn trạng thái kèm bằng chứng mã nguồn:

| Trạng thái | Ý nghĩa |
|---|---|
| ✅ Đã làm đúng | Báo cáo khớp hệ thống |
| 🔄 Làm khác | Có làm nhưng khác mô tả (ví dụ vai trò, luồng thanh toán) |
| ❌ Chưa làm | Có trong báo cáo, không có trong hệ thống |
| ➕ Có thêm | Hệ thống có, báo cáo chưa nêu (ví dụ chọn chi nhánh, VNPay, phiếu nhập kèm IMEI, benchmark chatbot) |

Mỗi mục ❌ có đề xuất xử lý: (a) bỏ khỏi báo cáo; (b) chuyển sang "Hướng phát triển"; (c) chỉnh nhẹ project để có.

**Đầu ra:**
- `01_MA_TRAN_DOI_CHIEU.md`.
- Danh sách quyết định cho bạn chốt một lần, áp dụng cho toàn bộ các giai đoạn sau.

**Phát hiện sơ bộ (sẽ xác minh đầy đủ):**
- **Vai trò**: hệ thống có ADMIN / MANAGER / STAFF / CUSTOMER. Báo cáo ghi nhân viên bán hàng / kho / giao hàng.
- **Bảng có trong CSDL nhưng không có mã sử dụng**: `invoices`, `shipments`, `reviews`, `return_requests` / `return_items`, `chatbot_qna`, `chatbot_scripts`, `message_feedback`, `chat_message_retrievals`. `chat_business_retrievals` không có trong CSDL.
- **Chưa thấy trong hệ thống**: đơn đặt hàng NCC có duyệt, xuất kho, kiểm kê, điều chuyển kho, cảnh báo tồn kho, hạng thành viên / tích điểm, hóa đơn VAT, lập lịch giao hàng & lắp đặt, nhật ký hệ thống, sao lưu / phục hồi, quản trị kịch bản chatbot.
- **Mobile app không có chatbot**: báo cáo ghi có ở các mục 1.3.2, 3.1.1, 3.9, 4.3.6.
- **Sai công nghệ**: "SQL Server" (3.6.2.8) và "Chroma" (5.1.1), trong khi hệ thống dùng PostgreSQL + pgvector; "ví dụ .NET 8" (4.1).
- **Lỗi trình bày**: UC-13 ghi "(Bỏ USE CASE Này)" nhưng vẫn còn sơ đồ hoạt động 3.4.10; trùng số hình (Hình 67, 81 xuất hiện 2 lần; chú thích Hình 70 sai nội dung); còn nhiều chỗ "[Bổ sung…]".

---

## Tiến độ

| Giai đoạn | Trạng thái | Kết quả |
|---|---|---|
| 1. Ma trận đối chiếu | ✅ Xong (27/10/2026) | `01_MA_TRAN_DOI_CHIEU.md`, `01b_PHAN_TICH_TAC_NHAN.md`; quyết định D1–D9 đã chốt |
| 2. Chương 1 | ✅ Đã duyệt (27/10/2026) | `02_CHUONG_1.md` |
| 3. Chương 3 (3.1–3.3) | ✅ Đã duyệt (27/10/2026) | `03_CHUONG_3_YEU_CAU_USECASE.md`, `so-do/hinh13…puml`, `so-do/hinh31…puml` |
| 4. Chương 3 (3.4–3.7) | ✅ Đã duyệt (27/10/2026) | `04_CHUONG_3_MO_HINH.md`; 17 sơ đồ mới trong `so-do/` (ERD sinh từ schema thật, sơ đồ lớp phân tích và thiết kế) |
| 5. 3.8–3.9 + Chương 4 | ✅ Đã duyệt (27/10/2026) | `05_CHUONG_3_API_GIAO_DIEN_CHUONG_4.md`; bỏ 3.5.12 (gộp vào 3.5.6); Bảng 3.4–3.6, 4.1–4.4; khung hình giao diện chờ bạn chèn ảnh |
| 6. Chương 5, tài liệu, rà soát | ✅ Xong, chờ bạn duyệt | `06_CHUONG_5_VA_RA_SOAT.md`: tóm tắt, mục lục, danh mục hình (124) / bảng (12) / từ viết tắt; đánh số lại UC, hình, bảng; Chương 5; tài liệu tham khảo hợp nhất [1]–[47] |
| Tiếp theo | Sửa hệ thống theo `KE_HOACH_SUA_HE_THONG.md` (S1 → S6) | Bước S6 cập nhật lại các dòng `[CẦN CẬP NHẬT SAU KHI SỬA HỆ THỐNG …]` |

## Giai đoạn 2 — Chương 1 (tổng quan, mục tiêu, phạm vi)

**Làm gì:**
- Sửa 1.2 mục tiêu, 1.3.1 đối tượng sử dụng (theo vai trò thật), 1.3.2 phạm vi chức năng Website / Mobile / ngoài phạm vi, 1.5 bố cục, theo các quyết định ở giai đoạn 1.
- Giữ nguyên phần khảo sát hiện trạng 1.4. Đó là khảo sát thực tế, phần ảnh biểu mẫu do bạn bổ sung.

**Đầu ra:** `02_CHUONG_1.md`, văn bản sửa theo từng mục, đánh dấu rõ chỗ thay đổi.

## Giai đoạn 3 — Chương 3 phần yêu cầu và Use-Case (3.1–3.3)

**Làm gì:**
- 3.1: viết lại danh sách yêu cầu chức năng và phi chức năng theo hệ thống thật.
- 3.2 (khảo sát nghiệp vụ thực tế): giữ, nhưng thêm một đoạn nêu rõ nghiệp vụ nào được hệ thống hỗ trợ, nghiệp vụ nào ngoài phạm vi.
- 3.3: rà 18 UC nghiệp vụ và khoảng 19 UC hệ thống. Bỏ hoặc gộp UC không còn; sửa đặc tả (tác nhân, luồng chính, ngoại lệ) theo luồng thật; thêm UC mới (chọn chi nhánh, thanh toán VNPay, trả góp nội bộ, nhập kho kèm Serial/IMEI…).

**Đầu ra:** `03_CHUONG_3_YEU_CAU_USECASE.md`, gồm bảng đặc tả UC đã sửa và mã PlantUML cho các sơ đồ Use-Case cần vẽ lại.

## Giai đoạn 4 — Chương 3 phần mô hình động và cấu trúc (3.4–3.7)

**Làm gì:**
- **Sơ đồ hoạt động, sơ đồ tuần tự**: giữ những sơ đồ đúng, viết lại những sơ đồ đã đổi, thêm sơ đồ cho luồng mới (checkout có khóa tồn kho, VNPay + đối soát, chatbot).
- **ERD**: dựng lại theo đúng schema Flyway V1–V8 (49 bảng). Với các bảng có thiết kế nhưng chưa dùng, quyết định trình bày như "thiết kế cho giai đoạn sau" hoặc lược bỏ khỏi sơ đồ.
- **Sơ đồ lớp**: đối chiếu với entity / service thật.

**Đầu ra:** `04_CHUONG_3_MO_HINH.md`, kèm mã PlantUML / Mermaid cho từng sơ đồ cần vẽ lại.

## Giai đoạn 5 — Chương 3 phần API và giao diện (3.8–3.9) + Chương 4 (xây dựng, kiểm thử)

**Làm gì:**
- **3.8**: bảng đặc tả endpoint thật lấy từ các controller, kèm cơ chế xác thực thật (JWT trong cookie HttpOnly, refresh token, CSRF). Phần này thay cho mô tả "middleware" hiện tại.
- **3.9**: danh sách màn hình thật của web (storefront + quản trị) và app Flutter.
- **4.1**: môi trường và phiên bản thật (Spring Boot 4.1.1 / Java 21, React 19 / Vite 8, Flutter / Dart 3.10, Python 3.12 / FastAPI, PostgreSQL 16 + pgvector, Ollama).
- **4.2–4.3**: mô tả cài đặt từng nhóm chức năng, kèm ảnh chụp màn hình. Tôi có thể tự chụp web bằng trình duyệt tự động; ảnh app mobile do bạn chụp.
- **4.4 kiểm thử**: số liệu thật, gồm unit / integration backend, frontend, chatbot, bộ benchmark chatbot 205 case (điểm 84,5/100) và bảng test case chính.
- **4.5 triển khai**: môi trường thật (Docker Compose, chạy local). Không ghi những gì chưa có (server, domain).

**Đầu ra:**
- `05_CHUONG_3_API_GIAO_DIEN_CHUONG_4.md`.
- Thư mục ảnh chụp màn hình web.

## Giai đoạn 6 — Chương 5, tài liệu tham khảo và rà soát tổng thể

**Làm gì:**
- **Chương 5**: kết quả đạt được, hạn chế (trung thực, có số liệu), hướng phát triển (gồm các mục ❌ đã chọn chuyển sang).
- **Tài liệu tham khảo**: hợp nhất tài liệu chương 2 với danh mục chung; bỏ mục không dùng (ví dụ sách C#).
- **Rà soát toàn bộ**:
  - đánh lại số hình / bảng, lập danh mục hình, bảng, từ viết tắt;
  - kiểm tra thuật ngữ thống nhất ("chi nhánh" / "kho", "biến thể"…);
  - kiểm tra tham chiếu chéo giữa các chương;
  - kiểm tra chương 2 khớp với các chương sau.

**Đầu ra:** `06_CHUONG_5_VA_RA_SOAT.md` và danh sách kiểm tra cuối cùng.

---

## Nhánh song song (chỉ khi bạn chọn) — Chỉnh nhẹ project

Chỉ áp dụng cho các điểm mà sửa code hợp lý hơn sửa báo cáo. Ví dụ:
- 5 lỗi ưu tiên của benchmark chatbot;
- ẩn VNPay trên app mobile khi tắt;
- một chức năng nhỏ đã có sẵn bảng trong CSDL.

Mỗi thay đổi được duyệt riêng, có test, và ghi vào `chatbot-service/UPDATECHATBOTLOG.md` nếu liên quan chatbot.

## Nguyên tắc chung

- **Mã nguồn là nguồn sự thật**: không ghi vào báo cáo chức năng chưa có, không phóng đại kết quả.
- **Giữ phần khảo sát nghiệp vụ thực tế** (1.4, 3.2) vì đó là khảo sát, không phải mô tả hệ thống. Ranh giới "hệ thống hỗ trợ đến đâu" được ghi rõ.
- **Sơ đồ**: tôi cung cấp mã PlantUML / Mermaid và mô tả; bạn vẽ lại hoặc render trong công cụ của nhóm.
- **Sửa trên bản sao txt**: không sửa file Word và không sửa bản gốc txt. Chỗ nào cần bạn bổ sung (ảnh, số liệu khảo sát) được đánh dấu `[CẦN BỔ SUNG: …]`.
