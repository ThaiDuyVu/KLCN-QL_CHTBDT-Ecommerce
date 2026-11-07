package com.example.backend.order.dto;

import java.util.UUID;

/** An active employee holding the DELIVERY_STAFF role, offered when an order is handed over for delivery. */
public record DeliveryStaffResponse(UUID employeeId, String employeeCode, String fullName, String phone) {}
