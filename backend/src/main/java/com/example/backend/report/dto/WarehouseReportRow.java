package com.example.backend.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class WarehouseReportRow {
    private final UUID warehouseId;
    private final String warehouseName;
    private final long count;
    private final BigDecimal amount;

    public WarehouseReportRow(UUID warehouseId, String warehouseName, long count, BigDecimal amount) {
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
        this.count = count;
        this.amount = amount;
    }

    public UUID getWarehouseId() { return warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public long getCount() { return count; }
    public BigDecimal getAmount() { return amount; }
}
