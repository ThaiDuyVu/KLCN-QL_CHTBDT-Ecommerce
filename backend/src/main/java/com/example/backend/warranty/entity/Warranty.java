package com.example.backend.warranty.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "warranties")
public class Warranty {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "warranty_id", nullable = false, updatable = false)
    private UUID warrantyId;
    @Column(name = "serial_id", nullable = false, unique = true)
    private UUID serialId;
    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WarrantyStatus status;

    public UUID getWarrantyId() { return warrantyId; }
    public void setWarrantyId(UUID warrantyId) { this.warrantyId = warrantyId; }
    public UUID getSerialId() { return serialId; }
    public void setSerialId(UUID serialId) { this.serialId = serialId; }
    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID orderItemId) { this.orderItemId = orderItemId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public WarrantyStatus getStatus() { return status; }
    public void setStatus(WarrantyStatus status) { this.status = status; }
}
