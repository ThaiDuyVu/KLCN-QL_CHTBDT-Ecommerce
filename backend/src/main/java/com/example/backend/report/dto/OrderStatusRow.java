package com.example.backend.report.dto;


public class OrderStatusRow {
    private final String status;
    private final long count;

    public OrderStatusRow(String status, long count) {
        this.status = status;
        this.count = count;
    }

    public String getStatus() { return status; }
    public long getCount() { return count; }
}
