package com.example.backend.invoice.dto;

import com.example.backend.invoice.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceSummary(UUID invoiceId, String invoiceNumber, UUID orderId, String orderCode, String customerName,
                             OffsetDateTime issuedDate, BigDecimal totalAmount, InvoiceStatus status) {}
