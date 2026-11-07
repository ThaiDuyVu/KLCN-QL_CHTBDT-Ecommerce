-- =========================================================
-- V10 – Status / enum integrity (plan P3.1, evaluation A2)
-- ---------------------------------------------------------
-- Until now only users, serial_numbers, warranties and warranty_tickets
-- constrained their status; the core business tables relied on Java enums
-- alone. These CHECKs mirror the Java enums exactly, so a hand-written
-- UPDATE or an import cannot store a status the application cannot read.
--
-- If existing data violates a constraint, this migration fails and rolls
-- back as a whole (PostgreSQL DDL is transactional); fix the rows first.
--
-- Not constrained on purpose:
--   carts.status   only 'ACTIVE' exists today; carts have no lifecycle yet.
--   chatbot tables owned by chatbot-service.
-- =========================================================

-- Orders, payments, delivery, invoices ------------------------------------
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'));

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_status
        CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED'));

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_payment_method
        CHECK (payment_method IN ('COD', 'VNPAY', 'INSTALLMENT'));

ALTER TABLE installment_payments
    ADD CONSTRAINT ck_installment_payments_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'APPROVED', 'REJECTED'));

ALTER TABLE installment_providers
    ADD CONSTRAINT ck_installment_providers_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE shipments
    ADD CONSTRAINT ck_shipments_status
        CHECK (status IN ('SHIPPING', 'DELIVERED', 'FAILED'));

ALTER TABLE invoices
    ADD CONSTRAINT ck_invoices_status
        CHECK (status IN ('ISSUED', 'CANCELLED'));

-- After-sales -------------------------------------------------------------
ALTER TABLE return_requests
    ADD CONSTRAINT ck_return_requests_status
        CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'RECEIVED', 'REFUNDED'));

ALTER TABLE reviews
    ADD CONSTRAINT ck_reviews_status
        CHECK (status IN ('VISIBLE', 'HIDDEN'));

-- A returned device's warranty is voided rather than reported as expired.
ALTER TABLE warranties DROP CONSTRAINT ck_warranties_status;
ALTER TABLE warranties
    ADD CONSTRAINT ck_warranties_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'VOIDED'));

-- Promotions --------------------------------------------------------------
ALTER TABLE promotions
    ADD CONSTRAINT ck_promotions_discount_type
        CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT'));

ALTER TABLE promotions
    ADD CONSTRAINT ck_promotions_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'));

-- A percentage discount above 100 % would make prices negative.
ALTER TABLE promotions
    ADD CONSTRAINT ck_promotions_percentage_range
        CHECK (discount_type <> 'PERCENTAGE' OR discount_value <= 100);

-- Catalogue, suppliers, branches, goods receipts --------------------------
ALTER TABLE categories
    ADD CONSTRAINT ck_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE brands
    ADD CONSTRAINT ck_brands_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE products
    ADD CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE product_variants
    ADD CONSTRAINT ck_product_variants_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE suppliers
    ADD CONSTRAINT ck_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE warehouses
    ADD CONSTRAINT ck_warehouses_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE goods_receipts
    ADD CONSTRAINT ck_goods_receipts_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED'));
