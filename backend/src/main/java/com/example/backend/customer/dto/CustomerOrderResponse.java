package com.example.backend.customer.dto;

import java.util.UUID;
import java.time.OffsetDateTime;
import com.example.backend.order.entity.OrderStatus;
import java.math.BigDecimal;

public class CustomerOrderResponse {
    private final UUID orderId;
    private final String orderCode;
    private final OffsetDateTime orderDate;
    private OrderStatus status;
    private final BigDecimal totalAmount;
    private final UUID warehouseId;
    private final String warehouseName;

    public CustomerOrderResponse(UUID orderId, String orderCode, OffsetDateTime orderDate, OrderStatus status, BigDecimal totalAmount, UUID warehouseId, String warehouseName) {
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.orderDate = orderDate;
        this.status = status;
        this.totalAmount = totalAmount;
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
    }

    public UUID getOrderId() { return orderId; }
    public String getOrderCode() { return orderCode; }
    public OffsetDateTime getOrderDate() { return orderDate; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getWarehouseName() { return warehouseName; }
}
