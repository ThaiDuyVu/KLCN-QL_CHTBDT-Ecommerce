package com.example.backend.invoice.service;

import com.example.backend.invoice.dto.InvoicePageResponse;
import com.example.backend.invoice.dto.InvoiceResponse;
import com.example.backend.invoice.dto.InvoiceSummary;
import com.example.backend.order.entity.Order;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceService {
    /** Issues the sales invoice of a delivered order (idempotent); runs inside the delivery transaction. */
    InvoiceSummary issueFor(Order order);
    Optional<InvoiceSummary> forOrder(UUID orderId);
    InvoicePageResponse list(String keyword, int page, int size);
    InvoiceResponse detail(UUID invoiceId);
    InvoiceResponse mineForOrder(UUID userId, UUID orderId);
}
