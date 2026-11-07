package com.example.backend.order.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "history_id")
    private UUID historyId;
    @Column(name = "order_id") private UUID orderId;
    @Enumerated(EnumType.STRING) @Column(name = "from_status") private OrderStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status") private OrderStatus toStatus;
    @Column(name = "changed_by_user_id") private UUID changedByUserId;
    @Column(name = "note") private String note;
    @Column(name = "changed_at") private OffsetDateTime changedAt;

    public UUID getHistoryId() { return historyId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID value) { this.orderId = value; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(OrderStatus value) { this.fromStatus = value; }
    public OrderStatus getToStatus() { return toStatus; }
    public void setToStatus(OrderStatus value) { this.toStatus = value; }
    public UUID getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(UUID value) { this.changedByUserId = value; }
    public String getNote() { return note; }
    public void setNote(String value) { this.note = value; }
    public OffsetDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(OffsetDateTime value) { this.changedAt = value; }
}
