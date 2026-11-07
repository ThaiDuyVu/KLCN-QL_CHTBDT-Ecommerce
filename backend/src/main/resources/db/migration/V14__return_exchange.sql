-- =========================================================
-- V14 – Exchange returned goods for other products
-- ---------------------------------------------------------
-- The outline asks for "đổi trả": until now a return could only be refunded.
-- An EXCHANGE request lists the products the customer wants instead; once the
-- returned goods are received, the store creates a replacement order.
--
-- Accounting:
--   return_requests.refund_amount  value of the returned goods (both types);
--                                  for EXCHANGE it is the credit applied to the
--                                  replacement order.
--   exchange_difference            replacement total - credit:
--                                  > 0 customer pays the difference,
--                                  < 0 store refunds the difference.
-- Revenue: the original order is reduced by refund_amount (REFUNDED and
-- EXCHANGED requests); the replacement order carries the new goods in full.
-- =========================================================

ALTER TABLE return_requests ADD COLUMN request_type VARCHAR(20) NOT NULL DEFAULT 'RETURN';
ALTER TABLE return_requests
    ADD CONSTRAINT ck_return_requests_request_type CHECK (request_type IN ('RETURN', 'EXCHANGE'));

ALTER TABLE return_requests ADD COLUMN exchange_order_id UUID;
ALTER TABLE return_requests
    ADD CONSTRAINT fk_return_requests_exchange_order
        FOREIGN KEY (exchange_order_id) REFERENCES orders (order_id) ON DELETE RESTRICT;
ALTER TABLE return_requests
    ADD CONSTRAINT uq_return_requests_exchange_order UNIQUE (exchange_order_id);
ALTER TABLE return_requests
    ADD CONSTRAINT ck_return_requests_exchange_order_type
        CHECK (exchange_order_id IS NULL OR request_type = 'EXCHANGE');

ALTER TABLE return_requests ADD COLUMN exchange_difference NUMERIC(15,2);

-- EXCHANGED: replacement order created (terminal state of an EXCHANGE request).
ALTER TABLE return_requests DROP CONSTRAINT ck_return_requests_status;
ALTER TABLE return_requests
    ADD CONSTRAINT ck_return_requests_status
        CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'RECEIVED', 'REFUNDED', 'EXCHANGED'));

-- Products the customer wants in exchange.
CREATE TABLE return_exchange_items (
    exchange_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id        UUID    NOT NULL,
    variant_id       UUID    NOT NULL,
    quantity         INTEGER NOT NULL,

    CONSTRAINT fk_return_exchange_items_return
        FOREIGN KEY (return_id) REFERENCES return_requests (return_id) ON DELETE RESTRICT,
    CONSTRAINT fk_return_exchange_items_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants (variant_id) ON DELETE RESTRICT,
    CONSTRAINT uq_return_exchange_items_return_variant UNIQUE (return_id, variant_id),
    CONSTRAINT ck_return_exchange_items_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_return_exchange_items_return_id ON return_exchange_items (return_id);
CREATE INDEX idx_return_requests_exchange_order_id ON return_requests (exchange_order_id);
