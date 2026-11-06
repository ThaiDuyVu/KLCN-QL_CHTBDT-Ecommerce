package com.example.backend.returns.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "return_items")
public class ReturnItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "return_item_id")
    private UUID returnItemId;
    @Column(name = "return_id")
    private UUID returnId;
    @Column(name = "order_item_id")
    private UUID orderItemId;
    @Column(name = "serial_id")
    private UUID serialId;
    @Column(name = "quantity")
    private Integer quantity;
    /** Customer's reason; the warehouse inspection result is appended when the goods are received. */
    @Column(name = "reason")
    private String reason;

    public UUID getReturnItemId() { return returnItemId; }
    public UUID getReturnId() { return returnId; }
    public void setReturnId(UUID value) { this.returnId = value; }
    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID value) { this.orderItemId = value; }
    public UUID getSerialId() { return serialId; }
    public void setSerialId(UUID value) { this.serialId = value; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
}
