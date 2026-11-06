package com.example.backend.order.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class Shipment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "shipment_id")
    private UUID shipmentId;
    @Column(name = "order_id")
    private UUID orderId;
    /** The employee responsible for the delivery (shipments.employee_id is NOT NULL). */
    @Column(name = "employee_id")
    private UUID employeeId;
    @Column(name = "tracking_number")
    private String trackingNumber;
    @Column(name = "shipping_provider")
    private String shippingProvider;
    @Column(name = "shipped_date")
    private OffsetDateTime shippedDate;
    @Column(name = "delivered_date")
    private OffsetDateTime deliveredDate;
    @Enumerated(EnumType.STRING) @Column(name = "status")
    private ShipmentStatus status;

    public UUID getShipmentId() { return shipmentId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID value) { this.orderId = value; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID value) { this.employeeId = value; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String value) { this.trackingNumber = value; }
    public String getShippingProvider() { return shippingProvider; }
    public void setShippingProvider(String value) { this.shippingProvider = value; }
    public OffsetDateTime getShippedDate() { return shippedDate; }
    public void setShippedDate(OffsetDateTime value) { this.shippedDate = value; }
    public OffsetDateTime getDeliveredDate() { return deliveredDate; }
    public void setDeliveredDate(OffsetDateTime value) { this.deliveredDate = value; }
    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus value) { this.status = value; }
}
