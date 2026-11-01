# Backlog — chỉnh sửa hệ thống cho khớp báo cáo

Các việc **chưa làm**, ghi lại để triển khai khi tiếp tục phát triển. Mỗi việc cần bạn duyệt trước khi bắt tay.

Kế hoạch triển khai đầy đủ (thứ tự, điều kiện CSDL, điểm dừng) nằm ở `KE_HOACH_SUA_HE_THONG.md`. Bảng dưới chỉ ghi các việc phát sinh từ quyết định khi rà báo cáo.

| # | Việc | Nguồn | Phạm vi dự kiến | Trạng thái |
|---|---|---|---|---|
| BL-01 | **Xuất báo cáo** (CSV/Excel) cho các báo cáo quản trị | Quyết định D6 (27/10/2026); báo cáo 1.3.2, 3.1.1, 4.3.5 | Endpoint xuất hoặc xuất phía web từ dữ liệu đang hiển thị; nút "Xuất file" trên `/reports` | Chưa làm |
| BL-02 | **Cấu hình ngưỡng cảnh báo tồn kho** (UC-SYS-05) | Quyết định D7 (27/10/2026) | Hiện báo cáo tồn kho thấp dùng ngưỡng mặc định 5 truyền khi gọi API (`ReportController.lowStock`). **Schema không có chỗ lưu ngưỡng** (bảng `inventory` chỉ có `quantity`, `reserved_quantity`; không có bảng cấu hình). Hai lựa chọn: (a) thêm cột / bảng ngưỡng: cần migration, mà CSDL đang khóa theo `backend/AGENTS.md`, nên cần bạn cho phép riêng; (b) không đổi schema: cho người dùng chọn ngưỡng trên màn hình báo cáo và lưu phía trình duyệt. Cách (b) đáp ứng "cấu hình" ở mức giao diện, không lưu theo từng kho / biến thể | Chưa làm |
| BL-03 | Tách vai trò STAFF thành các chức danh (bán hàng / kho / bảo hành / giao hàng) | Phân tích tác nhân (01b), nếu chọn phương án B | Thêm vai trò vào bảng `roles`, sửa annotation, `RequireRole` và menu web | Đã chốt làm (D1, 27/10/2026); cần cho phép migration khi triển khai |

Ghi thêm việc mới ở cuối bảng khi phát sinh trong các giai đoạn sau.
