-- =========================================================
-- V4 - Seed Auth Roles & Permissions
-- =========================================================

-- =========================================================
-- 1. SEED ROLES
-- =========================================================

INSERT INTO roles (role_name, description)
VALUES
    ('ADMIN', 'Quản trị viên hệ thống'),
    ('MANAGER', 'Quản lý cửa hàng'),
    ('STAFF', 'Nhân viên cửa hàng'),
    ('CUSTOMER', 'Khách hàng');


-- =========================================================
-- 2. SEED PERMISSIONS
-- =========================================================

INSERT INTO permissions (permission_name, description)
VALUES

    -- -----------------------------------------------------
    -- USER MANAGEMENT
    -- -----------------------------------------------------
    ('USER_VIEW', 'Xem thông tin người dùng'),
    ('USER_CREATE', 'Tạo người dùng'),
    ('USER_UPDATE', 'Cập nhật thông tin người dùng'),
    ('USER_DISABLE', 'Vô hiệu hóa người dùng'),

    -- -----------------------------------------------------
    -- USER - ROLE ASSIGNMENT
    -- -----------------------------------------------------
    ('USER_ROLE_VIEW', 'Xem vai trò được gán cho người dùng'),
    ('USER_ROLE_ASSIGN', 'Gán vai trò cho người dùng'),
    ('USER_ROLE_REMOVE', 'Gỡ vai trò khỏi người dùng'),

    -- -----------------------------------------------------
    -- ROLE - PERMISSION ASSIGNMENT
    -- -----------------------------------------------------
    ('ROLE_PERMISSION_VIEW', 'Xem quyền của vai trò'),
    ('ROLE_PERMISSION_ASSIGN', 'Gán quyền cho vai trò'),
    ('ROLE_PERMISSION_REMOVE', 'Gỡ quyền khỏi vai trò'),

    -- -----------------------------------------------------
    -- EMPLOYEE MANAGEMENT
    -- -----------------------------------------------------
    ('EMPLOYEE_VIEW', 'Xem thông tin nhân viên'),
    ('EMPLOYEE_CREATE', 'Tạo hồ sơ nhân viên'),
    ('EMPLOYEE_UPDATE', 'Cập nhật hồ sơ nhân viên'),

    -- -----------------------------------------------------
    -- CUSTOMER MANAGEMENT
    -- -----------------------------------------------------
    ('CUSTOMER_VIEW', 'Xem thông tin khách hàng'),
    ('CUSTOMER_CREATE', 'Tạo hồ sơ khách hàng'),
    ('CUSTOMER_UPDATE', 'Cập nhật hồ sơ khách hàng');


-- =========================================================
-- 3. ADMIN -> ALL PERMISSIONS
-- =========================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT
    r.role_id,
    p.permission_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.role_name = 'ADMIN';


-- =========================================================
-- 4. MANAGER -> OPERATIONAL USER MANAGEMENT
-- =========================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT
    r.role_id,
    p.permission_id
FROM roles r
         JOIN permissions p
              ON p.permission_name IN (
                                       'USER_VIEW',

                                       'EMPLOYEE_VIEW',
                                       'EMPLOYEE_CREATE',
                                       'EMPLOYEE_UPDATE',

                                       'CUSTOMER_VIEW',
                                       'CUSTOMER_CREATE',
                                       'CUSTOMER_UPDATE'
                  )
WHERE r.role_name = 'MANAGER';


-- =========================================================
-- 5. STAFF -> READ-ONLY BASIC INFORMATION
-- =========================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT
    r.role_id,
    p.permission_id
FROM roles r
         JOIN permissions p
              ON p.permission_name IN (
                                       'EMPLOYEE_VIEW',
                                       'CUSTOMER_VIEW'
                  )
WHERE r.role_name = 'STAFF';


-- =========================================================
-- 6. CUSTOMER
-- =========================================================
-- CUSTOMER intentionally has no administrative permissions.
-- Customer-specific business permissions can be introduced
-- later by another feature/module if required.