package com.example.backend.order.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class PaymentResponse {
    private UUID paymentId;
    private com.example.backend.order.entity.PaymentMethod paymentMethod;
    private com.example.backend.order.entity.PaymentStatus status;
    private BigDecimal amount;
    private String transactionCode;
    private String paymentUrl;
    private OffsetDateTime paymentExpiresAt;
    public PaymentResponse() {}
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID value) { this.paymentId = value; }
    public com.example.backend.order.entity.PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(com.example.backend.order.entity.PaymentMethod value) { this.paymentMethod = value; }
    public com.example.backend.order.entity.PaymentStatus getStatus() { return status; }
    public void setStatus(com.example.backend.order.entity.PaymentStatus value) { this.status = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { this.amount = value; }
    public String getTransactionCode() { return transactionCode; }
    public void setTransactionCode(String value) { this.transactionCode = value; }
    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String value) { paymentUrl = value; }
    public OffsetDateTime getPaymentExpiresAt() { return paymentExpiresAt; }
    public void setPaymentExpiresAt(OffsetDateTime value) { paymentExpiresAt = value; }
}
