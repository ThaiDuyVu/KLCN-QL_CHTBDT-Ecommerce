package com.example.backend.customer.dto;

import java.util.UUID;
import java.time.OffsetDateTime;
import com.example.backend.order.entity.OrderStatus;
import java.math.BigDecimal;
import com.example.backend.order.entity.InstallmentStatus;

public class CustomerInstallmentResponse {
    private final UUID installmentId;
    private final UUID orderId;
    private final String orderCode;
    private final OffsetDateTime orderDate;
    private final OrderStatus orderStatus;
    private final String providerName;
    private final BigDecimal totalAmount;
    private final BigDecimal downPayment;
    private final BigDecimal remainingAmount;
    private final int termMonths;
    private InstallmentStatus status;

    public CustomerInstallmentResponse(UUID installmentId, UUID orderId, String orderCode, OffsetDateTime orderDate, OrderStatus orderStatus, String providerName, BigDecimal totalAmount, BigDecimal downPayment, BigDecimal remainingAmount, int termMonths, InstallmentStatus status) {
        this.installmentId = installmentId;
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.orderDate = orderDate;
        this.orderStatus = orderStatus;
        this.providerName = providerName;
        this.totalAmount = totalAmount;
        this.downPayment = downPayment;
        this.remainingAmount = remainingAmount;
        this.termMonths = termMonths;
        this.status = status;
    }

    public UUID getInstallmentId() { return installmentId; }
    public UUID getOrderId() { return orderId; }
    public String getOrderCode() { return orderCode; }
    public OffsetDateTime getOrderDate() { return orderDate; }
    public OrderStatus getOrderStatus() { return orderStatus; }
    public String getProviderName() { return providerName; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getDownPayment() { return downPayment; }
    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public int getTermMonths() { return termMonths; }
    public InstallmentStatus getStatus() { return status; }
    public void setStatus(InstallmentStatus status) { this.status = status; }
}
