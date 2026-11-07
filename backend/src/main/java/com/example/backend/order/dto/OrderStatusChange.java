package com.example.backend.order.dto;

import com.example.backend.order.entity.OrderStatus;
import java.time.OffsetDateTime;

/** actorType: STAFF, CUSTOMER or SYSTEM (no user, e.g. VNPAY expiry or data before history started). */
public record OrderStatusChange(OrderStatus fromStatus, OrderStatus toStatus, OffsetDateTime changedAt,
                                String actorName, String actorType, String note) {}
