# Giai đoạn 5 — 3.5.6/3.5.12, 3.8, 3.9 và Chương 4: danh sách thay đổi

Đã sửa trực tiếp trong `BaoCaoKhoaLuan_ChinhSua.txt` (UTF-8 BOM, CRLF giữ nguyên). Bảng trong bản sao viết dạng **các cột cách nhau bằng ký tự tab**. Khi đưa sang Word: bôi đen các dòng, chọn Insert → Table → Convert Text to Table, phân cách bằng Tabs.

Mọi số liệu đều lấy từ mã nguồn hoặc từ lần chạy thật ngày 27/10/2026, cụ thể ở mục 6.

## 1. 3.5.6 và 3.5.12

| Thay đổi | Lý do |
|---|---|
| **Bỏ 3.5.12** gồm tiêu đề, 2 chỗ ảnh và chú thích Hình 75 | Cùng chức năng với 3.5.6 (tìm kiếm sản phẩm, UC-SYS-06). Trong bản txt cả hai mục chỉ có hình, không có văn bản riêng để gộp |
| 3.5.6 đổi tên thành "Tra cứu, tìm kiếm và xem chi tiết sản phẩm trên Website"; chú thích Hình 69 thêm "(lọc theo giá, thương hiệu)". Sửa cả dòng mục lục | Giữ phần riêng của tiêu đề 3.5.12 cũ |
| Thêm dòng `[CẦN KIỂM TRA HÌNH: …]` trước Hình 69 | Tôi không xem được ảnh. Nếu Hình 75 có bước Hình 69 không có (xem cấu hình, lọc giá / thương hiệu), bạn dùng Hình 75 thay cho Hình 69 rồi xóa Hình 75 |

## 2. 3.8 Thiết kế kiến trúc API (viết lại)

- **Kiến trúc**: Backend API (Spring Boot) và Chatbot API (FastAPI); quy ước REST, phân trang, mã lỗi 400 / 401 / 403 / 404 / 409 (đều có trong mã).
- **Xác thực, phân quyền**: thay đoạn "middleware kiểm tra token" bằng cơ chế thật:
  - BCrypt;
  - JWT 15 phút, refresh token 7 ngày, cả hai trong cookie HttpOnly;
  - xoay vòng refresh token, chỉ lưu mã băm, phát hiện dùng lại token cũ thì thu hồi mọi phiên;
  - CSRF qua cookie `XSRF-TOKEN` và header `X-XSRF-TOKEN`;
  - `JwtAuthenticationFilter`, phân quyền theo vai trò hoặc mã quyền;
  - endpoint `/mine`; giá luôn tính ở máy chủ;
  - chatbot chuyển tiếp cookie sang `/api/auth/me`;
  - VNPay IPN / Return kiểm tra HMAC-SHA512.
- **Bảng 3.4**: 37 dòng, gom theo nhóm (Xác thực, Người dùng, Danh mục, Sản phẩm, Kho, Giỏ hàng, Khách hàng, Đơn hàng, Thanh toán, Trả góp, Khuyến mãi, Bảo hành, Báo cáo, Chatbot). Cột "Vai trò được phép" lấy từ `@RequireAnyAuthority` / `@PreAuthorize` trong 31 controller backend và router chatbot.
- **2 ghi chú chờ sửa hệ thống:**
  - **S2**: cập nhật cột vai trò khi tách STAFF.
  - **S1.6 (mới)**: lỗ hổng phân quyền, xem mục 5.

## 3. 3.9 Thiết kế giao diện (dựng khung)

- Đoạn nguyên tắc chung: menu và trang theo vai trò, trang "Không có quyền truy cập", bảng có tìm kiếm / lọc / phân trang, thông báo tiếng Việt.
- **3.9.1 Website** (mục con mới): **Bảng 3.5**, 20 màn hình chính lấy từ `Fontend/src/App.jsx`, cột "Đối tượng sử dụng" ghi theo 7 tác nhân ở 1.3.1 (D1).
- **3.9.2 Mobile App** (mục con mới): **Bảng 3.6**, 9 màn hình lấy từ `flutter/lib/features/*/pages`. Màn hình chatbot giữ theo D5.
- **Chỗ chèn hình**: `[CẦN BỔ SUNG HÌNH: …]` gợi ý màn hình nên chụp; 12 dòng chú thích `Hình 3.x. Giao diện …` để sẵn, gồm 8 hình web và 4 hình mobile.
- Mục lục: thêm 3.9.1, 3.9.2.

## 4. Chương 4 (viết lại)

| Mục | Nội dung mới |
|---|---|
| 4.1 | Phiên bản thật: Java 21, Spring Boot 4.1.1, jjwt 0.12.6, Flyway; React 19.3, React Router 7.18, Vite 8.3, Recharts 3.10, Node 22; Flutter 3.47.6 / Dart 3.13.5, Riverpod, go_router, Dio; Python 3.12, FastAPI 0.142, Pydantic 2.13; Ollama 0.35.1 (`qwen3:4b-instruct`, `qwen3-embedding:0.6b`); PostgreSQL 16.15, pgvector 0.8.7, Docker 29.8; máy Apple M4 16 GB |
| 4.2 | Bỏ "sao lưu / phục hồi" (D6) và danh sách vai trò cũ. Thêm Flyway V1–V8, cơ chế xác thực, phân quyền hai phía, quản lý người dùng. Ghi chú cập nhật sau S2 |
| 4.3 (mở đầu) | **Bảng 4.1 Mức độ hoàn thành**: 8 nhóm, cột "Đã cài đặt" và "Chưa cài đặt (đã thiết kế)". Chương 4 nêu đúng phần đã làm (D2) |
| 4.3.1 | Danh mục nhiều cấp, biến thể (giá vốn, Serial / IMEI, bảo hành), khuyến mãi tự áp giá |
| **4.3.2 (mới)** | **Quản lý kho**: phiếu nhập kèm Serial / IMEI, duyệt trong một giao dịch, tồn kho có giữ chỗ, tra cứu Serial. Bản cũ không có mục kho |
| 4.3.3 | Chọn chi nhánh, 3 phương thức (COD, VNPay sandbox + QueryDr, trả góp có duyệt), giá tính ở máy chủ, khóa dòng chống bán vượt. Ghi chú hóa đơn (S3.2) |
| 4.3.4 | Tên mới "xử lý đơn hàng, giao hàng và bảo hành" (bỏ "đổi trả", D3). Máy trạng thái đơn; gán Serial ở PROCESSING; trừ kho và tạo bảo hành ở DELIVERED. Ghi chú giao hàng (S3.1) |
| 4.3.5 | Chatbot theo kiến trúc thật: hiểu câu bằng LLM → kiểm chứng tất định → truy xuất (pgvector, RAG, dữ liệu của chính khách) → sinh câu → lớp chặn số liệu. Nêu rõ không giao dịch thay khách; tài liệu chính sách là DEMO |
| 4.3.6 | 8 báo cáo thật, lọc theo thời gian và kho. Ghi chú xuất báo cáo (S1.4) |
| 4.3.7 | Mobile App theo màn hình thật. Ghi chú chatbot mobile (S5.1) |
| 4.4.1 | Kế hoạch: unit, tích hợp với Testcontainers (gồm ca đồng thời), chức năng thủ công, đánh giá chatbot với mô hình thật |
| 4.4.2 | **Bảng 4.2** tổng hợp kết quả; **Bảng 4.3** 17 kịch bản kiểm thử; **Bảng 4.4** benchmark theo nhóm, kèm nhận xét điểm yếu |
| 4.5 | Chỉ ghi môi trường cục bộ: Docker Compose, các cổng, Android emulator / USB, VNPay sandbox, cấu hình qua biến môi trường. Nêu rõ **chưa triển khai lên máy chủ công khai** |

**Bảng 4.3, nguồn từng kịch bản:**
- **TC-01 → TC-14**: các ca kiểm thử tích hợp thật:
  - `AuthLoginIntegrationTest`, `AuthRefreshIntegrationTest`, `AuthLogoutIntegrationTest`;
  - `OrderCheckoutIntegrationTest`, `OrderWorkflowTest`;
  - `GoodsReceiptInventoryIntegrationTest`.
- **TC-15 → TC-17**: benchmark G1-05 (Pass), G5-02 (Pass), G8-01 (**Fail**). Ca Fail giữ lại cho trung thực; lỗi đã có trong S1.1.

**Mục lục**: 4.3.x đánh lại thành 4.3.1 → 4.3.7. Số trang giữ nguyên, Giai đoạn 6 cập nhật.

## 5. Phát hiện mới về hệ thống (đã thêm vào `KE_HOACH_SUA_HE_THONG.md` mục **S1.6**)

**Lỗ hổng phân quyền:** các API sau không có chú thích phân quyền, nên **tài khoản khách hàng cũng gọi được**, kể cả các thao tác tạo, sửa:
- `SupplierController`: toàn bộ;
- `WarehouseController`: toàn bộ;
- `GoodsReceiptController`: GET và POST.

**Cách sửa:** giới hạn cho nhân viên, nhưng **giữ `GET /api/warehouses`** cho khách, vì web và Flutter dùng nó để chọn chi nhánh. Báo cáo 3.8 có ghi chú tạm, sẽ xóa sau khi sửa.

## 6. Số liệu kiểm thử (chạy ngày 27/10/2026)

| Bộ | Kết quả | Nguồn |
|---|---|---|
| Backend | **275 / 275 đạt**, 30 lớp, 0 lỗi, 0 bỏ qua | `bash scripts/test-backend.sh -q`, `backend/target/surefire-reports` |
| Frontend | 159 / 159 đạt, 16 tệp | Vitest |
| Flutter | 15 đạt, 1 bỏ qua | `flutter test` |
| Chatbot | 618 / 618 đạt | pytest |
| Ngữ cảnh chatbot | 50 / 50, holdout 18 / 18 | `chatbot-service/debug/context-eval` |
| Benchmark chatbot | 84,5 / 100 (cốt lõi 85,8; ngữ cảnh nâng cao 54,5; an toàn 99,3) | `chatbot-service/debug/benchmark/BAO_CAO_KET_QUA_BENCHMARK.md` |

## 7. Việc của bạn khi đưa sang Word

1. Chuyển Bảng 3.4, 3.5, 3.6, 4.1, 4.2, 4.3 và bảng benchmark thành bảng Word. Bảng 3.4 và 4.3 rộng, nên đặt trang ngang hoặc cỡ chữ 10–11.
2. Chụp và chèn ảnh vào các dòng `Hình 3.x` / `Hình 4.x`, rồi xóa các dòng `[CẦN BỔ SUNG HÌNH …]`.
3. Kiểm tra Hình 69 / Hình 75 theo mục 1.
4. Giữ các dòng `[CẦN CẬP NHẬT SAU KHI SỬA HỆ THỐNG …]` và `[GHI CHÚ – …]` cho tới khi làm xong các việc S tương ứng. Tôi sẽ cập nhật lại ở bước S6.

## 8. Để lại cho Giai đoạn 6

- Đánh số lại bảng: bảng 3.1–3.3 của bản gốc chưa có chú thích dạng "Bảng 3.x.", cần rà khi đánh số lại.
- Đánh số lại hình: `Hình 3.x` / `Hình 4.x`, Hình 75 đã bỏ.
- Cập nhật số trang trong mục lục.
- Chương 5: "Kết quả đạt được" đang ghi có "giao hàng, đổi trả"; "Hạn chế" có "12 tuần"; "Hướng phát triển" cần lấy theo cột "Chưa cài đặt" của Bảng 4.1.
