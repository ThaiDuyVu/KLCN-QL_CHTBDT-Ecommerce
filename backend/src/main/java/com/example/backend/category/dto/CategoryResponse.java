package com.example.backend.category.dto;

import java.util.UUID;

public record CategoryResponse(
    UUID categoryId,
    String categoryName,
    String description,
    UUID parentId
)
{}