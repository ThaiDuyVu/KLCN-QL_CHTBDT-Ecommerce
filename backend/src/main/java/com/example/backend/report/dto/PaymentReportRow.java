package com.example.backend.report.dto;

import java.math.BigDecimal;

public class PaymentReportRow {
    private final String paymentMethod;
    private final String status;
    private final long count;
    private final BigDecimal amount;

    public PaymentReportRow(String paymentMethod, String status, long count, BigDecimal amount) {
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.count = count;
        this.amount = amount;
    }

    public String getPaymentMethod() { return paymentMethod; }
    public String getStatus() { return status; }
    public long getCount() { return count; }
    public BigDecimal getAmount() { return amount; }
}
