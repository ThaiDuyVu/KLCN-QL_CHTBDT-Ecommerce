package com.example.backend.report.dto;

import java.math.BigDecimal;
import java.util.List;

public class GoodsReceiptReportResponse {
    private final long count;
    private final BigDecimal totalAmount;
    private final List<WarehouseReportRow> warehouses;

    public GoodsReceiptReportResponse(long count, BigDecimal totalAmount, List<WarehouseReportRow> warehouses) {
        this.count = count;
        this.totalAmount = totalAmount;
        this.warehouses = warehouses;
    }

    public long getCount() { return count; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public List<WarehouseReportRow> getWarehouses() { return warehouses; }
}
