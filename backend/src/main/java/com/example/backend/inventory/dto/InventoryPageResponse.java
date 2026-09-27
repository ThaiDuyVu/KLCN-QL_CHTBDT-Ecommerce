package com.example.backend.inventory.dto;

import java.util.List;

public record InventoryPageResponse(
        List<InventoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
