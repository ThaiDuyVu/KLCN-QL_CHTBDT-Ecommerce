ALTER TABLE carts
    ADD COLUMN warehouse_id UUID;

ALTER TABLE carts
    ADD CONSTRAINT fk_carts_warehouse
        FOREIGN KEY (warehouse_id)
            REFERENCES warehouses (warehouse_id)
            ON DELETE RESTRICT;

CREATE INDEX idx_carts_warehouse_id
    ON carts (warehouse_id);

ALTER TABLE orders
    ADD COLUMN warehouse_id UUID;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_warehouse
        FOREIGN KEY (warehouse_id)
            REFERENCES warehouses (warehouse_id)
            ON DELETE RESTRICT;

CREATE INDEX idx_orders_warehouse_id
    ON orders (warehouse_id);
