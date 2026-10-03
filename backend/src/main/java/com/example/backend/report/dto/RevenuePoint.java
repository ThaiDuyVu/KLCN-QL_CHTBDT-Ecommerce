package com.example.backend.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class RevenuePoint {
    private final LocalDate bucket;
    private final BigDecimal revenue;
    private final long completedOrders;

    public RevenuePoint(LocalDate bucket, BigDecimal revenue, long completedOrders) {
        this.bucket = bucket;
        this.revenue = revenue;
        this.completedOrders = completedOrders;
    }

    public LocalDate getBucket() { return bucket; }
    public BigDecimal getRevenue() { return revenue; }
    public long getCompletedOrders() { return completedOrders; }
}
