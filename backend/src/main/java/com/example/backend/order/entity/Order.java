package com.example.backend.order.entity;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;
import jakarta.persistence.*;

@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "order_id")
    private UUID orderId;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "warehouse_id")
    private UUID warehouseId;
    @Column(name = "order_code")
    private String orderCode;
    @Column(name = "order_date")
    private OffsetDateTime orderDate;
    @Column(name = "recipient_name")
    private String recipientName;
    @Column(name = "recipient_phone")
    private String recipientPhone;
    @Column(name = "shipping_address", columnDefinition = "text")
    private String shippingAddress;
    @Column(name = "note", columnDefinition = "text")
    private String note;
    @Column(name = "subtotal", precision = 15, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "discount_amount", precision = 15, scale = 2)
    private BigDecimal discountAmount;
    @Column(name = "shipping_fee", precision = 15, scale = 2)
    private BigDecimal shippingFee;
    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;
    @Enumerated(EnumType.STRING) @Column(name = "status")
    private OrderStatus status;
    public Order() {}
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID value) { this.orderId = value; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID value) { this.customerId = value; }
    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID value) { this.warehouseId = value; }
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
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus value) { this.status = value; }
}
