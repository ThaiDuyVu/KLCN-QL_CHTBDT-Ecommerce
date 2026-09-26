package com.example.backend.order.dto;

import com.example.backend.order.entity.InstallmentStatus;
import com.example.backend.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InstallmentResponse(
        UUID installmentId, UUID orderId, String orderCode, OrderStatus orderStatus,
        UUID customerId, String customerName, UUID providerId, String providerName,
        BigDecimal totalAmount, BigDecimal downPayment, BigDecimal remainingAmount,
        Integer termMonths, InstallmentStatus status, List<InstallmentStatus> allowedStatuses
) {}
