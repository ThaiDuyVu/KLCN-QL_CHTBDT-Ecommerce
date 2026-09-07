-- =========================================================
-- V1 - CREATE AUTH SCHEMA
-- Project: QL_CHTBDT_Ecommerce
-- Database: PostgreSQL 16
--
-- Feature:
--   - User
--   - Role
--   - Permission
--   - UserRole
--   - RolePermission
--   - Employee
--   - Customer
--
-- Notes:
--   - UUID primary keys
--   - PostgreSQL generates UUID using gen_random_uuid()
--   - Money is not used in this migration
--   - Date/time uses TIMESTAMPTZ
--   - No hard delete for business data
--   - Foreign keys use ON DELETE RESTRICT
-- =========================================================


CREATE TABLE users (
                       user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                       username VARCHAR(255) NOT NULL,
                       password VARCHAR(255) NOT NULL,
                       email VARCHAR(255) NOT NULL,
                       phone VARCHAR(30),
                       status VARCHAR(30) NOT NULL,

                       created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT uq_users_username
                           UNIQUE (username),

                       CONSTRAINT uq_users_email
                           UNIQUE (email)
);


CREATE TABLE roles (
                       role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                       role_name VARCHAR(100) NOT NULL,
                       description VARCHAR(255),

                       CONSTRAINT uq_roles_role_name
                           UNIQUE (role_name)
);


CREATE TABLE permissions (
                             permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                             permission_name VARCHAR(150) NOT NULL,
                             description VARCHAR(255),

                             CONSTRAINT uq_permissions_permission_name
                                 UNIQUE (permission_name)
);


CREATE TABLE user_roles (
                            user_id UUID NOT NULL,
                            role_id UUID NOT NULL,

                            CONSTRAINT pk_user_roles
                                PRIMARY KEY (user_id, role_id),

                            CONSTRAINT fk_user_roles_user
                                FOREIGN KEY (user_id)
                                    REFERENCES users (user_id)
                                    ON DELETE RESTRICT,

                            CONSTRAINT fk_user_roles_role
                                FOREIGN KEY (role_id)
                                    REFERENCES roles (role_id)
                                    ON DELETE RESTRICT
);


CREATE TABLE role_permissions (
                                  role_id UUID NOT NULL,
                                  permission_id UUID NOT NULL,

                                  CONSTRAINT pk_role_permissions
                                      PRIMARY KEY (role_id, permission_id),

                                  CONSTRAINT fk_role_permissions_role
                                      FOREIGN KEY (role_id)
                                          REFERENCES roles (role_id)
                                          ON DELETE RESTRICT,

                                  CONSTRAINT fk_role_permissions_permission
                                      FOREIGN KEY (permission_id)
                                          REFERENCES permissions (permission_id)
                                          ON DELETE RESTRICT
);


CREATE TABLE employees (
                           employee_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                           user_id UUID NOT NULL,
                           employee_code VARCHAR(50) NOT NULL,
                           full_name VARCHAR(255) NOT NULL,
                           position VARCHAR(100),

                           CONSTRAINT uq_employees_user_id
                               UNIQUE (user_id),

                           CONSTRAINT uq_employees_employee_code
                               UNIQUE (employee_code),

                           CONSTRAINT fk_employees_user
                               FOREIGN KEY (user_id)
                                   REFERENCES users (user_id)
                                   ON DELETE RESTRICT
);


CREATE TABLE customers (
                           customer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                           user_id UUID NOT NULL,
                           full_name VARCHAR(255) NOT NULL,
                           address VARCHAR(500),
                           loyalty_point INTEGER NOT NULL DEFAULT 0,

                           CONSTRAINT uq_customers_user_id
                               UNIQUE (user_id),

                           CONSTRAINT ck_customers_loyalty_point
                               CHECK (loyalty_point >= 0),

                           CONSTRAINT fk_customers_user
                               FOREIGN KEY (user_id)
                                   REFERENCES users (user_id)
                                   ON DELETE RESTRICT
);


-- =========================================================
-- INDEXES
-- =========================================================

CREATE INDEX idx_user_roles_role_id
    ON user_roles (role_id);

CREATE INDEX idx_role_permissions_permission_id
    ON role_permissions (permission_id);