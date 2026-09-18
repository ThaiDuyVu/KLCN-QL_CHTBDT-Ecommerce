package com.example.backend.category.dto;

import com.example.backend.category.entity.CategoryStatus;
import java.util.UUID;

public record CategoryResponse(
    UUID categoryId,
    String categoryName,
    String description,
    UUID parentId,
    CategoryStatus status
)
{}