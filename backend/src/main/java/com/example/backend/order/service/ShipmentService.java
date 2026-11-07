package com.example.backend.order.service;

import com.example.backend.order.dto.DeliveryStaffResponse;
import com.example.backend.order.dto.ShipmentResponse;
import com.example.backend.order.dto.UpdateOrderStatusRequest;
import com.example.backend.order.entity.Order;
import com.example.backend.order.entity.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The delivery leg of an order: who carries it, with which carrier, and when it arrived or failed. */
public interface ShipmentService {
    /** PROCESSING → SHIPPED. */
    Shipment dispatch(Order order, UUID actorUserId, UpdateOrderStatusRequest request);
    /** SHIPPED → DELIVERED. ownOnly: a delivery employee may only close their own shipment. */
    void delivered(Order order, UUID actorUserId, boolean ownOnly);
    /** SHIPPED → CANCELLED (delivery failed). */
    void failed(Order order, UUID actorUserId, boolean ownOnly);
    Optional<ShipmentResponse> forOrder(UUID orderId);
    Map<UUID, ShipmentResponse> forOrders(Collection<UUID> orderIds);
    /** Shipments assigned to the calling delivery employee, newest first. */
    Page<Shipment> assignedTo(UUID actorUserId, Pageable pageable);
    java.util.List<DeliveryStaffResponse> deliveryStaff();
}
