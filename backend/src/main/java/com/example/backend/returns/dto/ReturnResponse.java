package com.example.backend.returns.dto;

import com.example.backend.returns.entity.ReturnStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReturnResponse(UUID returnId, String returnCode, UUID orderId, String orderCode, UUID customerId,
                             String customerName, String reason, OffsetDateTime requestDate, OffsetDateTime processedAt,
                             BigDecimal refundAmount, ReturnStatus status, String employeeName,
                             List<Line> items, List<ReturnStatus> allowedStatuses) {
    public record Line(UUID returnItemId, UUID orderItemId, String productName, String sku, String serialNumber,
                       int quantity, BigDecimal unitPrice, String reason) {}
}
