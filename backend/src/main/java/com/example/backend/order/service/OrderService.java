package com.example.backend.order.service;
import com.example.backend.order.dto.*;
import com.example.backend.order.entity.OrderStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
public interface OrderService {
    OrderResponse checkout(UUID userId, CheckoutRequest request);
    OrderResponse checkout(UUID userId, CheckoutRequest request, String clientIp);
    OrderPageResponse list(UUID userId, boolean customer, int page, int size);
    OrderResponse detail(UUID userId, boolean customer, UUID orderId);
    OrderResponse status(UUID userId, boolean customer, UUID orderId, OrderStatus status);
    /** Staff views and transitions, limited to what the caller's job title may do (OrderStaffPolicy). */
    OrderResponse staffDetail(UUID orderId, Collection<String> authorities);
    OrderResponse staffStatus(UUID userId, UUID orderId, OrderStatus status, Collection<String> authorities);
    /** reason is stored with the order; it is mandatory when a manager cancels a PAID order (payment becomes REFUNDED). */
    OrderResponse staffStatus(UUID userId, UUID orderId, OrderStatus status, String reason, Collection<String> authorities);
    /** Full staff transition: reason (cancel) and delivery data (SHIPPED). */
    OrderResponse staffStatus(UUID userId, UUID orderId, UpdateOrderStatusRequest request, Collection<String> authorities);
    /** Unrestricted transition with full request data (internal callers and tests). */
    OrderResponse status(UUID userId, UUID orderId, UpdateOrderStatusRequest request);
    /** Orders whose delivery is assigned to the calling delivery employee. */
    OrderPageResponse deliveries(UUID userId, int page, int size);
    List<DeliveryStaffResponse> deliveryStaff();
    /** Orders still waiting for a VNPAY payment whose session started before the cutoff. */
    List<UUID> unpaidVnpayOrdersBefore(OffsetDateTime cutoff);
    /** Cancels one expired unpaid VNPAY order and releases its reserved stock; false when it no longer qualifies. */
    boolean expireUnpaidVnpay(UUID orderId, OffsetDateTime cutoff);
}
