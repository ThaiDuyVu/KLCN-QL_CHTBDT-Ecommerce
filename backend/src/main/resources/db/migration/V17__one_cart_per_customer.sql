-- =========================================================
-- V17 – One cart per customer (customers 1-1 carts)
-- ---------------------------------------------------------
-- V1 modelled customers 1-n carts with a status column, but the application
-- never used more than one cart: it creates the cart once, empties it after
-- checkout and, when the customer picks another branch, clears it and moves
-- it there (carts.warehouse_id). status was always 'ACTIVE'.
--
-- The database now states that rule: one cart per customer, no status.
-- Fails (and rolls back) if a customer already has two carts.
-- =========================================================

ALTER TABLE carts DROP COLUMN status;

ALTER TABLE carts
    ADD CONSTRAINT uq_carts_customer_id UNIQUE (customer_id);

-- Covered by the unique constraint's index.
DROP INDEX idx_carts_customer_id;

COMMENT ON TABLE carts IS 'Giỏ hàng của khách (1-1 với customers): tạo một lần, được làm trống sau khi đặt hàng, gắn với chi nhánh đang mua.';
COMMENT ON COLUMN carts.warehouse_id IS 'Chi nhánh đang mua; đổi chi nhánh thì làm trống giỏ rồi gắn sang chi nhánh mới (tồn kho và giá tính theo chi nhánh).';
