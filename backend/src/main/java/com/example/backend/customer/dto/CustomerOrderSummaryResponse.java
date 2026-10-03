package com.example.backend.customer.dto;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.OffsetDateTime;
import com.example.backend.order.entity.OrderStatus;

public class CustomerOrderSummaryResponse {
    private final long totalOrders;
    private final long deliveredOrders;
    private final long cancelledOrders;
    private final BigDecimal totalSpend;
    private final UUID latestOrderId;
    private final String latestOrderCode;
    private final OffsetDateTime latestOrderDate;
    private final OrderStatus latestOrderStatus;

    public CustomerOrderSummaryResponse(long totalOrders, long deliveredOrders, long cancelledOrders, BigDecimal totalSpend, UUID latestOrderId, String latestOrderCode, OffsetDateTime latestOrderDate, OrderStatus latestOrderStatus) {
        this.totalOrders = totalOrders;
        this.deliveredOrders = deliveredOrders;
        this.cancelledOrders = cancelledOrders;
        this.totalSpend = totalSpend;
        this.latestOrderId = latestOrderId;
        this.latestOrderCode = latestOrderCode;
        this.latestOrderDate = latestOrderDate;
        this.latestOrderStatus = latestOrderStatus;
    }

    public long getTotalOrders() { return totalOrders; }
    public long getDeliveredOrders() { return deliveredOrders; }
    public long getCancelledOrders() { return cancelledOrders; }
    public BigDecimal getTotalSpend() { return totalSpend; }
    public UUID getLatestOrderId() { return latestOrderId; }
    public String getLatestOrderCode() { return latestOrderCode; }
    public OffsetDateTime getLatestOrderDate() { return latestOrderDate; }
    public OrderStatus getLatestOrderStatus() { return latestOrderStatus; }
}
