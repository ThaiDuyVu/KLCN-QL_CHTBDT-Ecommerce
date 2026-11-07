package com.example.backend.invoice.dto;

import java.util.List;

public record InvoicePageResponse(List<InvoiceSummary> content, int page, int size, long totalElements, int totalPages) {}
