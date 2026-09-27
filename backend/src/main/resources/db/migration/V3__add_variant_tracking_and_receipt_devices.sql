ALTER TABLE product_variants
    ADD COLUMN tracking_type VARCHAR(30) NOT NULL DEFAULT 'NONE';

ALTER TABLE product_variants
    ADD CONSTRAINT ck_product_variants_tracking_type
        CHECK (tracking_type IN ('NONE', 'SERIAL', 'IMEI'));

ALTER TABLE serial_numbers
    ADD CONSTRAINT ck_serial_numbers_status
        CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD', 'RETURNED', 'DEFECTIVE'));

CREATE TABLE goods_receipt_item_devices (
    receipt_device_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_item_id UUID NOT NULL,
    serial_number VARCHAR(255) NOT NULL,

    CONSTRAINT uq_receipt_item_devices_item_serial
        UNIQUE (receipt_item_id, serial_number),
    CONSTRAINT ck_receipt_item_devices_serial_not_blank
        CHECK (btrim(serial_number) <> ''),
    CONSTRAINT fk_receipt_item_devices_item
        FOREIGN KEY (receipt_item_id)
            REFERENCES goods_receipt_items (receipt_item_id)
            ON DELETE CASCADE
);

CREATE INDEX idx_receipt_item_devices_item
    ON goods_receipt_item_devices (receipt_item_id);

CREATE TABLE goods_receipt_item_device_imeis (
    receipt_device_imei_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_device_id UUID NOT NULL,
    imei_number VARCHAR(50) NOT NULL,

    CONSTRAINT uq_receipt_device_imeis_device_number
        UNIQUE (receipt_device_id, imei_number),
    CONSTRAINT ck_receipt_device_imeis_number_not_blank
        CHECK (btrim(imei_number) <> ''),
    CONSTRAINT fk_receipt_device_imeis_device
        FOREIGN KEY (receipt_device_id)
            REFERENCES goods_receipt_item_devices (receipt_device_id)
            ON DELETE CASCADE
);

CREATE INDEX idx_receipt_device_imeis_device
    ON goods_receipt_item_device_imeis (receipt_device_id);
