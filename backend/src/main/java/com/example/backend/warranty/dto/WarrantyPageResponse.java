package com.example.backend.warranty.dto;

import java.util.List;

public record WarrantyPageResponse(
        List<WarrantyResponse> content, int page, int size, long totalElements, int totalPages
) {}
