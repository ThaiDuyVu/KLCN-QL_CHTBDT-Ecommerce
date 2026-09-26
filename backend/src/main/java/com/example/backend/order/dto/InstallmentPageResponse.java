package com.example.backend.order.dto;

import java.util.List;

public record InstallmentPageResponse(
        List<InstallmentResponse> content, int page, int size,
        long totalElements, int totalPages
) {}
