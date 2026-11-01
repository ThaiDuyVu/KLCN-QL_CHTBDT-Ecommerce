-- =========================================================
-- V9: chức danh nhân viên theo báo cáo (mục 1.3.1, UC hệ thống)
-- Chỉ thêm dữ liệu vào roles / role_permissions; không đổi cấu trúc bảng.
-- STAFF được giữ nguyên làm vai trò nhân viên tổng hợp cho tài khoản hiện có.
-- Được phép tạo riêng cho kế hoạch sửa hệ thống S2 (27/10/2026).
-- =========================================================
INSERT INTO roles (role_name, description)
VALUES
    ('SALES_STAFF', 'Nhân viên bán hàng'),
    ('WAREHOUSE_KEEPER', 'Thủ kho'),
    ('WARRANTY_TECHNICIAN', 'Nhân viên kỹ thuật/bảo hành'),
    ('DELIVERY_STAFF', 'Nhân viên giao hàng')
ON CONFLICT (role_name) DO NOTHING;

-- Mọi chức danh nhân viên xem được thông tin nhân viên như STAFF.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
         JOIN permissions p ON p.permission_name = 'EMPLOYEE_VIEW'
WHERE r.role_name IN ('SALES_STAFF', 'WAREHOUSE_KEEPER', 'WARRANTY_TECHNICIAN', 'DELIVERY_STAFF')
ON CONFLICT DO NOTHING;

-- Bán hàng và bảo hành làm việc trực tiếp với hồ sơ khách hàng.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
         JOIN permissions p ON p.permission_name = 'CUSTOMER_VIEW'
WHERE r.role_name IN ('SALES_STAFF', 'WARRANTY_TECHNICIAN')
ON CONFLICT DO NOTHING;
