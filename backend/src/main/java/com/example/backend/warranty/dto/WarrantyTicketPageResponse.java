package com.example.backend.warranty.dto;

import java.util.List;

public record WarrantyTicketPageResponse(
        List<WarrantyTicketResponse> content, int page, int size, long totalElements, int totalPages
) {}
