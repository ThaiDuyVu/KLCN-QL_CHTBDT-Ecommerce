package com.example.backend.returns.dto;

import com.example.backend.returns.entity.ReturnCondition;
import com.example.backend.returns.entity.ReturnStatus;
import com.example.backend.returns.entity.ReturnType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReturnResponse(UUID returnId, String returnCode, UUID orderId, String orderCode, UUID customerId,
                             String customerName, String reason, OffsetDateTime requestDate, OffsetDateTime processedAt,
                             BigDecimal refundAmount, ReturnStatus status, String rejectionReason, String employeeName,
                             List<Line> items, List<ReturnStatus> allowedStatuses,
                             ReturnType requestType, List<ExchangeLine> exchangeItems, UUID exchangeOrderId,
                             String exchangeOrderCode, BigDecimal exchangeDifference) {
    /** currentPrice: today's selling price; the replacement order is priced when it is created. */
    public record ExchangeLine(UUID variantId, String productName, String sku, int quantity, BigDecimal currentPrice) {}
    public record Line(UUID returnItemId, UUID orderItemId, String productName, String sku, String serialNumber,
                       int quantity, BigDecimal unitPrice, String reason, ReturnCondition receivedCondition) {}
}
