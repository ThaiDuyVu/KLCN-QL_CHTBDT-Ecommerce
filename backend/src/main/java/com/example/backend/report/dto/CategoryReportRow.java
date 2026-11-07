package com.example.backend.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Sales of one root product category (sub-categories are rolled up into their root). */
public class CategoryReportRow {
    private final UUID categoryId;
    private final String categoryName;
    private final long quantity;
    private final long orderCount;
    private final BigDecimal revenue;
    private final BigDecimal grossProfit;

    public CategoryReportRow(UUID categoryId, String categoryName, long quantity, long orderCount,
                             BigDecimal revenue, BigDecimal grossProfit) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.quantity = quantity;
        this.orderCount = orderCount;
        this.revenue = revenue;
        this.grossProfit = grossProfit;
    }

    public UUID getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public long getQuantity() { return quantity; }
    public long getOrderCount() { return orderCount; }
    public BigDecimal getRevenue() { return revenue; }
    public BigDecimal getGrossProfit() { return grossProfit; }
}
