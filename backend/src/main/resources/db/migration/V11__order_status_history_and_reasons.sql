-- =========================================================
-- V11 – Order status history and dedicated reason columns
--        (plan P3.2, evaluation A3)
-- ---------------------------------------------------------
-- Until now an order kept only its current status: who confirmed it,
-- when it shipped and why it was cancelled were lost. Reasons were
-- appended to free-text columns as a stop-gap (orders.note,
-- return_requests.reason, return_items.reason).
-- =========================================================

-- One row per status change. changed_by_user_id is the actor's login account:
-- staff, the customer (self-cancel) or NULL for the system (e.g. VNPAY expiry).
CREATE TABLE order_status_history (
    history_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           UUID        NOT NULL,
    from_status        VARCHAR(30),
    to_status          VARCHAR(30) NOT NULL,
    changed_by_user_id UUID,
    note               TEXT,
    changed_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_order_status_history_order
        FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_status_history_user
        FOREIGN KEY (changed_by_user_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_order_status_history_from
        CHECK (from_status IS NULL OR from_status IN ('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_order_status_history_to
        CHECK (to_status IN ('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'))
);

CREATE INDEX idx_order_status_history_order ON order_status_history (order_id, changed_at);
CREATE INDEX idx_order_status_history_user ON order_status_history (changed_by_user_id);

-- Existing orders: one baseline row with the status they had when history started.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_at)
SELECT order_id, NULL, status, 'Trạng thái tại thời điểm bắt đầu ghi lịch sử', order_date
FROM orders;

-- Why an order was cancelled (customer, staff, failed delivery, VNPAY expiry).
ALTER TABLE orders ADD COLUMN cancel_reason TEXT;

-- Why sales rejected a return request.
ALTER TABLE return_requests ADD COLUMN rejection_reason TEXT;

-- Warehouse inspection result when a returned line comes back.
ALTER TABLE return_items ADD COLUMN received_condition VARCHAR(20);
ALTER TABLE return_items
    ADD CONSTRAINT ck_return_items_received_condition
        CHECK (received_condition IS NULL OR received_condition IN ('RESTOCK', 'DEFECTIVE'));
