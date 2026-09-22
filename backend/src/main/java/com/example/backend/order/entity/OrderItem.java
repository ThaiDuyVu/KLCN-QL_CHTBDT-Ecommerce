package com.example.backend.order.entity;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;
import jakarta.persistence.*;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "order_item_id")
    private UUID orderItemId;
    @Column(name = "order_id")
    private UUID orderId;
    @Column(name = "variant_id")
    private UUID variantId;
    @Column(name = "serial_id")
    private UUID serialId;
    @Column(name = "quantity")
    private Integer quantity;
    @Column(name = "cost_price", precision = 15, scale = 2)
    private BigDecimal costPrice;
    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "discount_amount", precision = 15, scale = 2)
    private BigDecimal discountAmount;
    @Column(name = "final_unit_price", precision = 15, scale = 2)
    private BigDecimal finalUnitPrice;
    public OrderItem() {}
    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID value) { this.orderItemId = value; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID value) { this.orderId = value; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID value) { this.variantId = value; }
    public UUID getSerialId() { return serialId; }
    public void setSerialId(UUID value) { this.serialId = value; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal value) { this.costPrice = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { this.unitPrice = value; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal value) { this.discountAmount = value; }
    public BigDecimal getFinalUnitPrice() { return finalUnitPrice; }
    public void setFinalUnitPrice(BigDecimal value) { this.finalUnitPrice = value; }
}
