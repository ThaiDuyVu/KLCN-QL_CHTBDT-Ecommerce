-- =========================================================
-- V13 – Selling at the store counter (plan P3.4, evaluation A4)
-- ---------------------------------------------------------
-- Until now every customer needed a login account (customers.user_id NOT NULL)
-- and every order went through online checkout, so an electronics store could
-- not sell to a walk-in customer.
-- =========================================================

-- Walk-in customers: a profile without a login account, identified by phone.
ALTER TABLE customers ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE customers ADD COLUMN phone VARCHAR(30);
ALTER TABLE customers ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Existing profiles: creation time of their account (closest information available).
UPDATE customers c SET created_at = u.created_at FROM users u WHERE u.user_id = c.user_id;

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_account_or_phone
        CHECK (user_id IS NOT NULL OR phone IS NOT NULL);

-- A walk-in phone number identifies one customer.
CREATE UNIQUE INDEX uq_customers_phone ON customers (phone) WHERE phone IS NOT NULL;

-- Where the sale happened.
ALTER TABLE orders ADD COLUMN sales_channel VARCHAR(20) NOT NULL DEFAULT 'ONLINE';
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_sales_channel CHECK (sales_channel IN ('ONLINE', 'IN_STORE'));
CREATE INDEX idx_orders_sales_channel ON orders (sales_channel);

-- Cash paid at the counter.
ALTER TABLE payments DROP CONSTRAINT ck_payments_payment_method;
ALTER TABLE payments
    ADD CONSTRAINT ck_payments_payment_method CHECK (payment_method IN ('COD', 'VNPAY', 'INSTALLMENT', 'CASH'));
