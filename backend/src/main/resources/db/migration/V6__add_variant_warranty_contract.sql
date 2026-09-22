ALTER TABLE product_variants
    ADD COLUMN warranty_months INTEGER NOT NULL DEFAULT 0;

ALTER TABLE product_variants
    ADD CONSTRAINT ck_product_variants_warranty_months
        CHECK (warranty_months >= 0);

ALTER TABLE warranties
    ADD CONSTRAINT ck_warranties_status
        CHECK (status IN ('ACTIVE', 'EXPIRED'));

ALTER TABLE warranty_tickets
    ADD CONSTRAINT ck_warranty_tickets_status
        CHECK (status IN ('RECEIVED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED'));
