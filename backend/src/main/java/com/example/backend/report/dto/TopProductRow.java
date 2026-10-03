package com.example.backend.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class TopProductRow {
    private final UUID productId;
    private final String productName;
    private final long quantity;
    private final BigDecimal revenue;

    public TopProductRow(UUID productId, String productName, long quantity, BigDecimal revenue) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.revenue = revenue;
    }

    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public long getQuantity() { return quantity; }
    public BigDecimal getRevenue() { return revenue; }
}
