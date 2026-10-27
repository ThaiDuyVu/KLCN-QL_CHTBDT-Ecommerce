# Giai đoạn 1b — Phân tích tác nhân: báo cáo ↔ hệ thống

## 1. Tác nhân trong báo cáo

### 1.1 Số lượng theo từng phần

| Phần báo cáo | Tác nhân được nêu | Số lượng |
|---|---|---|
| 1.3.1 Đối tượng sử dụng | Admin/Quản lý hệ thống, Nhân viên bán hàng, Nhân viên kho, Nhân viên giao hàng, Khách hàng | **5** |
| 3.1.1 Yêu cầu chức năng | Admin, nhân viên bán hàng, nhân viên kho, nhân viên giao hàng (+ khách hàng ở nhóm Mobile) | **5** |
| 3.3.1 UC nghiệp vụ tổng quát | Khách hàng, Nhân viên bán hàng, Nhân viên giao vận, Nhân viên kỹ thuật/Bảo hành, Thủ kho, Đơn vị tài chính (đối tác), Quản lý/Admin | **7** |
| 3.3.2 UC hệ thống tổng quát | Giống 3.3.1 | **7** |

Tổng hợp từ đặc tả của 18 UC nghiệp vụ và 19 UC hệ thống, báo cáo dùng **12 tác nhân khác nhau**:

| Loại | Tác nhân | Cách gọi trong báo cáo | Xuất hiện ở (tác nhân chính / phụ) |
|---|---|---|---|
| Nội bộ | **Admin** | "Admin", "Quản lý/Admin" | UC-09, 15, 16, 18; SYS-01, 02, 04, 05, 10, 14, 15, 19 |
| Nội bộ | **Quản lý** | "Quản lý", "Quản lý/Admin" | UC-01, 03, 05, 15; SYS-02, 04, 05, 14 |
| Nội bộ | **Nhân viên bán hàng** | "Nhân viên Bán hàng" | UC-06, 07, 08, 09, 17 (phụ); SYS-07, 08, 10, 18 (phụ) |
| Nội bộ | **Thủ kho** | "Thủ kho", "Thủ kho xuất/nhập", "Nhân viên kho" | UC-01, 02, 03, 04; SYS-02, 03, 04; phụ ở UC-06, 10, 13, SYS-13, 16 |
| Nội bộ | **Nhân viên giao hàng / giao vận** | "Nhân viên Giao hàng", "Nhân viên giao vận", gộp "Nhân viên Kỹ thuật/Giao hàng" | UC-08 (phụ), SYS-09 |
| Nội bộ | **Nhân viên kỹ thuật / bảo hành** | "Nhân viên Bảo hành", "Nhân viên Kỹ thuật" | UC-12, 13, 14; SYS-11, 12, 13 |
| Khách | **Khách hàng** | "Khách hàng", "Khách truy cập" | UC-10, 11, 17; SYS-06, 16, 17, 18; phụ nhiều UC |
| Ngoài | **Đơn vị tài chính** (trả góp) | "Đơn vị tài chính/Bên thứ 3" | UC-07, SYS-07 |
| Ngoài | **Cổng thanh toán** | "Cổng thanh toán" | UC-06, 10, 13; SYS-13, 16 |
| Ngoài | **Đơn vị bảo hành hãng** | "Đơn vị Bảo hành Hãng" | UC-12, 14 |
| Ngoài | **Nhà cung cấp** | "NCC" | UC-01, 05 (bước nghiệp vụ) |
| Ngoài | **Bộ phận kế toán** | "Bộ phận kế toán" | UC-05 (bước 6) |

Gom lại: **6 tác nhân nội bộ + Khách hàng + 5 tác nhân bên ngoài = 12.**

### 1.2 Mâu thuẫn ngay trong báo cáo

1. **"Admin" và "Quản lý" lúc tách lúc gộp.** 1.3.1 gộp thành "Admin/Quản lý hệ thống"; UC-01, 03, 05 chỉ có "Quản lý"; UC-16, SYS-15, 19 chỉ có "Admin"; nhiều UC ghi "Quản lý/Admin".
2. **Nhân viên bảo hành có ở chương 3 nhưng không có ở 1.3.1.**
3. **"Nhân viên Kỹ thuật/Giao hàng"** (UC-08, 14, SYS-09) gộp hai tác nhân mà ở chỗ khác lại tách riêng.
4. **"Thủ kho xuất (kho A)" / "Thủ kho nhập (kho B)"** (UC-04) là cùng một tác nhân ở hai vai, không phải hai tác nhân.
5. **"Người dùng hệ thống"** (tác nhân phụ UC-16, SYS-15) không phải tác nhân riêng.

---

## 2. Tác nhân trong hệ thống

### 2.1 Vai trò và cơ chế phân quyền

| Vai trò | Mô tả (seed `V1__create_full_database.sql`) | Ghi chú |
|---|---|---|
| **ADMIN** | Quản trị viên hệ thống | Có tất cả 16 mã quyền |
| **MANAGER** | Quản lý cửa hàng | 7 mã quyền: xem người dùng; xem, tạo, sửa nhân viên; xem, tạo, sửa khách hàng |
| **STAFF** | Nhân viên cửa hàng | Quyền đọc cơ bản |
| **CUSTOMER** | Khách hàng | Tự đăng ký tài khoản |
| *(Khách vãng lai)* | Chưa đăng nhập | Xem trang chủ, danh mục, sản phẩm, chi tiết sản phẩm |

**Cơ chế phân quyền:**
- Mỗi người dùng có **1 vai trò**. Quyền gồm *tên vai trò + các mã quyền của vai trò* (`AuthenticatedUserPrincipal`).
- Các chức năng nghiệp vụ kiểm tra **tên vai trò**, ví dụ `@RequireAnyAuthority({"ADMIN","MANAGER","STAFF"})`. Có 35 + 6 chỗ ADMIN/MANAGER, 15 chỗ ADMIN/MANAGER/STAFF, 23 chỗ CUSTOMER.
- 16 mã quyền chỉ dùng cho quản lý người dùng, nhân viên, khách hàng.
- **Không có API tạo vai trò mới.** ADMIN chỉ đổi được vai trò của người dùng và tập quyền của vai trò. Vì vậy, thêm "Thủ kho" hay "Nhân viên giao hàng" **cần sửa mã**, không chỉ thêm dữ liệu.

**Tác nhân bên ngoài có trong hệ thống:**
- **Cổng thanh toán VNPay (sandbox)**: gọi IPN và return về backend.
- **Nhà cung cấp trả góp**: chỉ là dữ liệu (`installment_providers`), không kết nối hệ thống. Việc duyệt hồ sơ do ADMIN/MANAGER cập nhật tay.
- **Mô hình AI chạy cục bộ (Ollama)**: là một thành phần của hệ thống, không phải tác nhân.

### 2.2 Ma trận quyền thật

Nguồn: annotation ở các controller và guard `RequireRole` của web. ✔ = được phép.

| Chức năng | ADMIN | MANAGER | STAFF | CUSTOMER | Vãng lai |
|---|---|---|---|---|---|
| Xem sản phẩm, tìm kiếm, chi tiết | ✔ | ✔ | ✔ | ✔ | ✔ |
| Quản lý danh mục, thương hiệu, sản phẩm, biến thể, ảnh, thông số | ✔ | ✔ | | | |
| Quản lý khuyến mãi | ✔ | ✔ | | | |
| Quản lý nhà cung cấp trả góp; duyệt / từ chối trả góp | ✔ | ✔ | | | |
| Lập / sửa phiếu nhập kho (kèm Serial/IMEI) | ✔ | ✔ | ✔ | | |
| **Duyệt** phiếu nhập (DRAFT → CONFIRMED / CANCELLED) | ✔ | ✔ | | | |
| Xem tồn kho, tra cứu Serial/IMEI | ✔ | ✔ | ✔ | | |
| Xem / xử lý đơn hàng (chuyển trạng thái, gồm SHIPPED / DELIVERED) | ✔ | ✔ | ✔ | | |
| Xem khách hàng, đơn, bảo hành của khách | ✔ | ✔ | ✔ | | |
| Tra cứu bảo hành, xử lý phiếu bảo hành | ✔ | ✔ | ✔ | | |
| Xem báo cáo (8 báo cáo) | ✔ | ✔ | | | |
| Quản lý người dùng, gán vai trò, sửa quyền vai trò | ✔ | | | | |
| Chọn chi nhánh, giỏ hàng, đặt hàng, thanh toán, hủy đơn chưa thanh toán | | | | ✔ | |
| Sổ địa chỉ, đơn của tôi, bảo hành của tôi, tạo phiếu bảo hành | | | | ✔ | |
| Chatbot tư vấn (web) | | | | ✔ | |

**Kết luận:** hệ thống có **4 vai trò** (+ khách vãng lai). Báo cáo có **6 tác nhân nội bộ**:
- Admin và Quản lý **khớp** với ADMIN và MANAGER.
- Nhân viên bán hàng, Thủ kho, Nhân viên bảo hành, Nhân viên giao hàng **được gộp chung thành STAFF**.

---

## 3. Ánh xạ tác nhân báo cáo → hệ thống

| Tác nhân trong báo cáo | Hệ thống hiện tại | Mức khớp |
|---|---|---|
| Admin | ADMIN | ✅ |
| Quản lý | MANAGER | ✅ (báo cáo cần tách rõ khỏi Admin) |
| Nhân viên bán hàng | STAFF | 🔄 gộp |
| Thủ kho | STAFF (lập phiếu); MANAGER/ADMIN (duyệt) | 🔄 gộp |
| Nhân viên bảo hành / kỹ thuật | STAFF | 🔄 gộp |
| Nhân viên giao hàng / giao vận | STAFF (chuyển đơn SHIPPED → DELIVERED) | 🔄 gộp, không có màn hình riêng |
| Khách hàng | CUSTOMER (+ khách vãng lai) | ✅ |
| Cổng thanh toán | VNPay sandbox | ✅ |
| Đơn vị tài chính | Dữ liệu nhà cung cấp trả góp, duyệt thủ công | 🔄 không kết nối |
| Đơn vị bảo hành hãng | Không có | ❌ |
| Nhà cung cấp, Kế toán | Không phải người dùng hệ thống | — (chỉ ở nghiệp vụ) |

---

## 4. Phương án làm cho báo cáo và hệ thống thống nhất

| | **A. Báo cáo theo hệ thống** | **B. Hệ thống theo báo cáo** | **C. Kết hợp (khuyến nghị)** |
|---|---|---|---|
| **Làm gì** | UC hệ thống chỉ dùng 4 vai trò | Thêm vai trò SALES, WAREHOUSE, WARRANTY, SHIPPER thay cho STAFF | Hệ thống giữ 4 vai trò. Báo cáo mô tả vai trò **Nhân viên** gồm 4 chức danh nghiệp vụ |
| **Báo cáo** | Thay "NV bán hàng / Thủ kho / NV bảo hành / NV giao hàng" bằng "Nhân viên" ở UC hệ thống | Gần như giữ nguyên (chỉ sửa mâu thuẫn ở mục 1.2) | UC nghiệp vụ (3.3.1) giữ 6 tác nhân thực tế. UC hệ thống ghi "Nhân viên (bán hàng / kho / bảo hành / giao hàng)" và thêm bảng ánh xạ mục 3 cùng ma trận quyền mục 2.2 |
| **Code** | Không đổi | Sửa khoảng 15 annotation STAFF thành từng vai trò; thêm 4 vai trò vào CSDL; sửa `ROLES`, `RequireRole`, menu quản trị ở web; cập nhật seed và test | Không đổi |
| **Ràng buộc** | — | Thêm vai trò là thêm dữ liệu vào bảng `roles`, cần migration Flyway mới. Quy tắc dự án (`backend/AGENTS.md`) cấm tạo migration nếu bạn không cho phép riêng | — |
| **Rủi ro** | Báo cáo mất phần phân tách chức danh, trông "mỏng" hơn | Hồi quy phân quyền; khối lượng khoảng 1 ngày làm việc kèm kiểm thử | Giám khảo có thể hỏi vì sao chưa tách quyền theo chức danh. Trả lời: RBAC đã có sẵn bảng vai trò và quyền, việc tách là hướng phát triển |
| **Khi demo** | Khớp hoàn toàn | Khớp hoàn toàn | Khớp: đăng nhập bằng tài khoản STAFF làm được mọi việc của 4 chức danh, đúng như báo cáo mô tả |

**Khuyến nghị: phương án C.**
- Không sửa code, nên không có rủi ro hồi quy trước buổi bảo vệ.
- Báo cáo vẫn giữ phân tích nghiệp vụ đầy đủ 6 tác nhân (đúng tinh thần "giữ các phần chưa triển khai").
- Bảng ánh xạ và ma trận quyền làm rõ ranh giới, nên demo không mâu thuẫn với báo cáo.
- Việc tách STAFF thành 4 vai trò được ghi vào Hướng phát triển và backlog.

Dù chọn phương án nào, các mâu thuẫn ở mục 1.2 cũng phải sửa: thống nhất tên gọi, tách Admin / Quản lý, bỏ "Người dùng hệ thống", gộp "Thủ kho xuất/nhập".

**Đã chốt (27/10/2026): phương án B.** Giữ tác nhân theo báo cáo; hệ thống sẽ được sửa theo báo cáo (S2). Báo cáo vẫn sửa các mâu thuẫn nội bộ ở mục 1.2. Khi triển khai S2 sẽ cần cho phép tạo migration dữ liệu thêm vai trò.
