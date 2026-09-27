package com.example.backend.order.entity;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;
import jakarta.persistence.*;

@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "payment_id")
    private UUID paymentId;
    @Column(name = "order_id")
    private UUID orderId;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method")
    private PaymentMethod paymentMethod;
    @Column(name = "transaction_code")
    private String transactionCode;
    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(name = "payment_date")
    private OffsetDateTime paymentDate;
    @Enumerated(EnumType.STRING) @Column(name = "status")
    private PaymentStatus status;
    public Payment() {}
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID value) { this.paymentId = value; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID value) { this.orderId = value; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod value) { this.paymentMethod = value; }
    public String getTransactionCode() { return transactionCode; }
    public void setTransactionCode(String value) { this.transactionCode = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { this.amount = value; }
    public OffsetDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(OffsetDateTime value) { this.paymentDate = value; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus value) { this.status = value; }
}
