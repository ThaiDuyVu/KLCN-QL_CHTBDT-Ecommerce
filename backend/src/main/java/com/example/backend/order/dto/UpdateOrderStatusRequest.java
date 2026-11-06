package com.example.backend.order.dto;

import jakarta.validation.constraints.*;

public class UpdateOrderStatusRequest {
    @NotNull
    private com.example.backend.order.entity.OrderStatus status;
    /** Cancellation reason; mandatory when cancelling an order whose payment is already PAID. */
    @Size(max = 500)
    private String reason;
    /** SHIPPED only: delivery employee; defaults to the caller's own employee profile. */
    private java.util.UUID deliveryEmployeeId;
    /** SHIPPED only: carrier name; defaults to in-house delivery. */
    @Size(max = 255)
    private String shippingProvider;
    /** SHIPPED only: carrier tracking number; generated when blank. */
    @Size(max = 100)
    @Pattern(regexp = "^[A-Za-z0-9-]*$", message = "Mã vận đơn chỉ gồm chữ, số và gạch ngang")
    private String trackingNumber;
    public UpdateOrderStatusRequest() {}
    public java.util.UUID getDeliveryEmployeeId() { return deliveryEmployeeId; }
    public void setDeliveryEmployeeId(java.util.UUID value) { this.deliveryEmployeeId = value; }
    public String getShippingProvider() { return shippingProvider; }
    public void setShippingProvider(String value) { this.shippingProvider = value == null || value.isBlank() ? null : value.trim(); }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String value) { this.trackingNumber = value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT); }
    public com.example.backend.order.entity.OrderStatus getStatus() { return status; }
    public void setStatus(com.example.backend.order.entity.OrderStatus value) { this.status = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
}
