-- =========================================================
-- V16 – Trace every serial back to the goods receipt it came in on
-- ---------------------------------------------------------
-- Confirming a receipt copied each declared serial into serial_numbers as
-- text only, so "which supplier / receipt / unit cost did this device come
-- from?" relied on matching serial_number strings.
--
-- serial_numbers.receipt_device_id now points at the exact declared device
-- (goods_receipt_item_devices, 1-1). Receipt item, receipt, supplier and
-- unit cost follow by joins. NULL = serial not created by a goods receipt
-- (none today; kept open for e.g. warranty replacement units).
--
-- The FK only proves the device row exists; a trigger also checks that the
-- serial agrees with what was declared (same variant, same serial number).
-- Warehouse is not checked: a device may legitimately move between branches.
-- Confirmed receipts are read-only in the application, so the declared side
-- does not change afterwards.
-- =========================================================

ALTER TABLE serial_numbers ADD COLUMN receipt_device_id UUID;

-- Existing serials: the confirmed receipt that declared the same serial number
-- (serial numbers are unique, so at most one confirmed declaration matches).
UPDATE serial_numbers s
SET receipt_device_id = d.receipt_device_id
FROM goods_receipt_item_devices d
JOIN goods_receipt_items i ON i.receipt_item_id = d.receipt_item_id
JOIN goods_receipts r ON r.receipt_id = i.receipt_id
WHERE r.status = 'CONFIRMED'
  AND d.serial_number = s.serial_number
  AND i.variant_id = s.variant_id;

ALTER TABLE serial_numbers
    ADD CONSTRAINT fk_serial_numbers_receipt_device
        FOREIGN KEY (receipt_device_id) REFERENCES goods_receipt_item_devices (receipt_device_id) ON DELETE RESTRICT;

-- One declared device becomes at most one serial.
ALTER TABLE serial_numbers
    ADD CONSTRAINT uq_serial_numbers_receipt_device UNIQUE (receipt_device_id);

CREATE FUNCTION fn_serial_numbers_match_receipt_device() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM goods_receipt_item_devices d
        JOIN goods_receipt_items i ON i.receipt_item_id = d.receipt_item_id
        WHERE d.receipt_device_id = NEW.receipt_device_id
          AND d.serial_number = NEW.serial_number
          AND i.variant_id = NEW.variant_id
    ) THEN
        RAISE EXCEPTION 'Serial % không khớp thiết bị đã khai báo trên phiếu nhập (biến thể hoặc số serial khác)',
            NEW.serial_number
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_serial_numbers_match_receipt_device
    BEFORE INSERT OR UPDATE OF receipt_device_id, variant_id, serial_number ON serial_numbers
    FOR EACH ROW
    WHEN (NEW.receipt_device_id IS NOT NULL)
    EXECUTE FUNCTION fn_serial_numbers_match_receipt_device();

COMMENT ON COLUMN serial_numbers.receipt_device_id IS
    'Thiết bị đã khai báo trên phiếu nhập sinh ra serial này (1-1); qua đó truy được phiếu nhập, nhà cung cấp, ngày nhập, giá vốn. Trigger đảm bảo cùng biến thể và cùng số serial.';
COMMENT ON FUNCTION fn_serial_numbers_match_receipt_device() IS
    'Chặn serial trỏ tới thiết bị khai báo khác biến thể hoặc khác số serial.';
