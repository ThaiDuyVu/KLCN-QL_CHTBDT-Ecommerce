package com.example.backend.order.service;
import com.example.backend.order.dto.*;
import com.example.backend.order.entity.OrderStatus;
import java.util.Collection;
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
}
