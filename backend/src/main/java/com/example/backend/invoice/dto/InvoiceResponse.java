package com.example.backend.invoice.dto;

import com.example.backend.invoice.entity.InvoiceStatus;
import com.example.backend.order.entity.PaymentMethod;
import com.example.backend.order.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Everything printed on the sales invoice; line prices are the order snapshot, never current prices. */
public record InvoiceResponse(UUID invoiceId, String invoiceNumber, InvoiceStatus status, OffsetDateTime issuedDate,
                              UUID orderId, String orderCode, OffsetDateTime orderDate,
                              String warehouseName, String warehouseAddress,
                              String customerName, String recipientName, String recipientPhone, String shippingAddress,
                              PaymentMethod paymentMethod, PaymentStatus paymentStatus,
                              BigDecimal subtotal, BigDecimal discountAmount, BigDecimal shippingFee, BigDecimal totalAmount,
                              BigDecimal refundedAmount, List<Line> lines) {
    public record Line(String productName, String sku, String serialNumber, List<String> imeiNumbers, int quantity,
                       BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal finalUnitPrice, BigDecimal lineTotal) {}
}
