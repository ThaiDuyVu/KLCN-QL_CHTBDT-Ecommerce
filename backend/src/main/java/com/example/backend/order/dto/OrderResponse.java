package com.example.backend.order.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class OrderResponse {
    private UUID orderId;
    private UUID customerId;
    private String orderCode;
    private OffsetDateTime orderDate;
    private String recipientName;
    private String recipientPhone;
    private String shippingAddress;
    private String note;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private com.example.backend.order.entity.OrderStatus status;
    private List<OrderItemResponse> items;
    private PaymentResponse payment;
    private List<com.example.backend.order.entity.OrderStatus> allowedStatuses;
    public OrderResponse() {}
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID value) { this.orderId = value; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID value) { this.customerId = value; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String value) { this.orderCode = value; }
    public OffsetDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(OffsetDateTime value) { this.orderDate = value; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String value) { this.recipientName = value; }
    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String value) { this.recipientPhone = value; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String value) { this.shippingAddress = value; }
    public String getNote() { return note; }
    public void setNote(String value) { this.note = value; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal value) { this.subtotal = value; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal value) { this.discountAmount = value; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal value) { this.shippingFee = value; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal value) { this.totalAmount = value; }
    public com.example.backend.order.entity.OrderStatus getStatus() { return status; }
    public void setStatus(com.example.backend.order.entity.OrderStatus value) { this.status = value; }
    public List<OrderItemResponse> getItems() { return items; }
    public void setItems(List<OrderItemResponse> value) { this.items = value; }
    public PaymentResponse getPayment() { return payment; }
    public void setPayment(PaymentResponse value) { this.payment = value; }
    public List<com.example.backend.order.entity.OrderStatus> getAllowedStatuses() { return allowedStatuses; }
    public void setAllowedStatuses(List<com.example.backend.order.entity.OrderStatus> value) { this.allowedStatuses = value; }
}
