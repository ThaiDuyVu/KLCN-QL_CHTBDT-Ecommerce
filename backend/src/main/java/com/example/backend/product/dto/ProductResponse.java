package com.example.backend.product.dto;

import com.example.backend.product.entity.ProductStatus;
import java.util.UUID;
import java.time.OffsetDateTime;

public record ProductResponse(
    UUID productId,
    String productName,
    String description,
    UUID categoryId,
    UUID brandId,
    ProductStatus status,
    String categoryName,
    String brandName,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    UUID warehouseId,
    Long availableQuantity
) {
    public ProductResponse(UUID productId, String productName, String description, UUID categoryId, UUID brandId,
                           ProductStatus status, String categoryName, String brandName,
                           OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(productId, productName, description, categoryId, brandId, status, categoryName, brandName,
                createdAt, updatedAt, null, null);
    }
    public ProductResponse(UUID productId, String productName, String description,
                           UUID categoryId, UUID brandId, ProductStatus status) {
        this(productId, productName, description, categoryId, brandId, status, null, null, null, null, null, null);
    }
}
