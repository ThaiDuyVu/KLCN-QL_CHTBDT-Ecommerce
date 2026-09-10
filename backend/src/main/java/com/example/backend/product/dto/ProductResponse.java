package com.example.backend.product.dto;

import java.util.UUID;

public record ProductResponse(
    UUID productId,
    String productName,
    String description,
    UUID categoryId
) {}