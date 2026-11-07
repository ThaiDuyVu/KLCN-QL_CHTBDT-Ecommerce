package com.example.backend.returns.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "return_requests")
public class ReturnRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "return_id")
    private UUID returnId;
    @Column(name = "return_code")
    private String returnCode;
    @Column(name = "order_id")
    private UUID orderId;
    @Column(name = "customer_id")
    private UUID customerId;
    /** Last employee who processed the request. */
    @Column(name = "employee_id")
    private UUID employeeId;
    @Column(name = "reason")
    private String reason;
    @Column(name = "request_date")
    private OffsetDateTime requestDate;
    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount;
    @Enumerated(EnumType.STRING) @Column(name = "status")
    private ReturnStatus status;
    /** Why sales rejected the request (V11). */
    @Column(name = "rejection_reason")
    private String rejectionReason;
    @Enumerated(EnumType.STRING) @Column(name = "request_type")
    private ReturnType requestType = ReturnType.RETURN;
    /** Replacement order of an EXCHANGE request (V14). */
    @Column(name = "exchange_order_id")
    private UUID exchangeOrderId;
    /** Replacement total minus credit: positive = customer pays, negative = store refunds (V14). */
    @Column(name = "exchange_difference", precision = 15, scale = 2)
    private BigDecimal exchangeDifference;

    public UUID getReturnId() { return returnId; }
    public String getReturnCode() { return returnCode; }
    public void setReturnCode(String value) { this.returnCode = value; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID value) { this.orderId = value; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID value) { this.customerId = value; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID value) { this.employeeId = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public OffsetDateTime getRequestDate() { return requestDate; }
    public void setRequestDate(OffsetDateTime value) { this.requestDate = value; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime value) { this.processedAt = value; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal value) { this.refundAmount = value; }
    public ReturnType getRequestType() { return requestType; }
    public void setRequestType(ReturnType value) { this.requestType = value; }
    public UUID getExchangeOrderId() { return exchangeOrderId; }
    public void setExchangeOrderId(UUID value) { this.exchangeOrderId = value; }
    public BigDecimal getExchangeDifference() { return exchangeDifference; }
    public void setExchangeDifference(BigDecimal value) { this.exchangeDifference = value; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String value) { this.rejectionReason = value; }
    public ReturnStatus getStatus() { return status; }
    public void setStatus(ReturnStatus value) { this.status = value; }
}
