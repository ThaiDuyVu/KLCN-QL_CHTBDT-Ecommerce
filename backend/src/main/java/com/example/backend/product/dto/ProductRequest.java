package com.example.backend.product.dto;

import java.util.UUID;

public record ProductRequest(
    String productName,
    String description,
    UUID categoryId
) {}