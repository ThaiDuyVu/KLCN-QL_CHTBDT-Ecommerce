package com.example.backend.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class ReportSummaryResponse {
    private final LocalDate from;
    private final LocalDate to;
    private final String timezone;
    private final UUID warehouseId;
    private final BigDecimal revenue;
    private final BigDecimal grossProfit;
    private final BigDecimal totalDiscount;
    private final long totalOrders;
    private final long completedOrders;
    private final long cancelledOrders;
    private final BigDecimal averageOrderValue;

    public ReportSummaryResponse(LocalDate from, LocalDate to, String timezone, UUID warehouseId, BigDecimal revenue, BigDecimal grossProfit, BigDecimal totalDiscount, long totalOrders, long completedOrders, long cancelledOrders, BigDecimal averageOrderValue) {
        this.from = from;
        this.to = to;
        this.timezone = timezone;
        this.warehouseId = warehouseId;
        this.revenue = revenue;
        this.grossProfit = grossProfit;
        this.totalDiscount = totalDiscount;
        this.totalOrders = totalOrders;
        this.completedOrders = completedOrders;
        this.cancelledOrders = cancelledOrders;
        this.averageOrderValue = averageOrderValue;
    }

    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public String getTimezone() { return timezone; }
    public UUID getWarehouseId() { return warehouseId; }
    public BigDecimal getRevenue() { return revenue; }
    public BigDecimal getGrossProfit() { return grossProfit; }
    public BigDecimal getTotalDiscount() { return totalDiscount; }
    public long getTotalOrders() { return totalOrders; }
    public long getCompletedOrders() { return completedOrders; }
    public long getCancelledOrders() { return cancelledOrders; }
    public BigDecimal getAverageOrderValue() { return averageOrderValue; }
}
