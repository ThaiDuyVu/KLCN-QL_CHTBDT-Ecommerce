package com.example.backend.report.dto;

import java.util.UUID;

public class LowStockRow {
    private final UUID inventoryId;
    private final UUID warehouseId;
    private final String warehouseName;
    private final UUID variantId;
    private final String sku;
    private final String productName;
    private final int quantity;
    private final int reservedQuantity;
    private final int availableQuantity;

    public LowStockRow(UUID inventoryId, UUID warehouseId, String warehouseName, UUID variantId, String sku, String productName, int quantity, int reservedQuantity, int availableQuantity) {
        this.inventoryId = inventoryId;
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
        this.variantId = variantId;
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.reservedQuantity = reservedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public UUID getInventoryId() { return inventoryId; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public UUID getVariantId() { return variantId; }
    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public int getAvailableQuantity() { return availableQuantity; }
}
