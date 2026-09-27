package com.example.backend.order.payment.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class VnpayPaymentUrlResponse {
    private final UUID orderId;
    private final UUID paymentId;
    private final String paymentUrl;
    private final OffsetDateTime expiresAt;
    public VnpayPaymentUrlResponse(UUID orderId, UUID paymentId, String paymentUrl, OffsetDateTime expiresAt) {
        this.orderId = orderId; this.paymentId = paymentId; this.paymentUrl = paymentUrl; this.expiresAt = expiresAt;
    }
    public UUID getOrderId() { return orderId; }
    public UUID getPaymentId() { return paymentId; }
    public String getPaymentUrl() { return paymentUrl; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
}
