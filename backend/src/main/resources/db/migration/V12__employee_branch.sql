-- =========================================================
-- V12 – Employees belong to a branch (plan P3.3, evaluation A5)
-- ---------------------------------------------------------
-- A branch employee only sees and handles the orders, returns, invoices
-- and reports of their own branch. NULL = not tied to a branch
-- (administrators, store-wide managers): they see every branch.
-- =========================================================
ALTER TABLE employees ADD COLUMN warehouse_id UUID;

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (warehouse_id) ON DELETE RESTRICT;

CREATE INDEX idx_employees_warehouse_id ON employees (warehouse_id);
