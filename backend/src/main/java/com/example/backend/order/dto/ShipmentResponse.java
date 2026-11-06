package com.example.backend.order.dto;

import com.example.backend.order.entity.ShipmentStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ShipmentResponse(UUID shipmentId, ShipmentStatus status, UUID employeeId, String employeeName,
                               String employeeCode, String employeePhone, String shippingProvider, String trackingNumber,
                               OffsetDateTime shippedDate, OffsetDateTime deliveredDate) {}
