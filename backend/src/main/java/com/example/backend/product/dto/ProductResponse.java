package com.example.backend.product.dto;

import com.example.backend.product.entity.ProductStatus;
import java.util.UUID;
import java.math.BigDecimal;
import com.example.backend.promotion.dto.PromotionSummaryResponse;
import com.example.backend.promotion.service.PromotionPrice;
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
    Long availableQuantity,
    String primaryImageUrl,
    BigDecimal originalPrice,
    BigDecimal effectivePrice,
    BigDecimal discountAmount,
    PromotionSummaryResponse promotion
) {
    public ProductResponse(UUID productId, String productName, String description, UUID categoryId, UUID brandId,
                           ProductStatus status, String categoryName, String brandName, OffsetDateTime createdAt,
                           OffsetDateTime updatedAt, UUID warehouseId, Long availableQuantity, String primaryImageUrl) {
        this(productId, productName, description, categoryId, brandId, status, categoryName, brandName, createdAt,
                updatedAt, warehouseId, availableQuantity, primaryImageUrl, null, null, null, null);
    }
    public ProductResponse withPricing(PromotionPrice value) {
        if (value == null) return this;
        return new ProductResponse(productId, productName, description, categoryId, brandId, status, categoryName,
                brandName, createdAt, updatedAt, warehouseId, availableQuantity, primaryImageUrl,
                value.getUnitPrice(), value.getFinalUnitPrice(), value.getDiscountAmount(), value.getPromotion());
    }
    public ProductResponse(UUID productId, String productName, String description, UUID categoryId, UUID brandId,
                           ProductStatus status, String categoryName, String brandName,
                           OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(productId, productName, description, categoryId, brandId, status, categoryName, brandName,
                createdAt, updatedAt, null, null, null);
    }
    public ProductResponse(UUID productId, String productName, String description, UUID categoryId, UUID brandId,
                           ProductStatus status, String categoryName, String brandName,
                           OffsetDateTime createdAt, OffsetDateTime updatedAt,
                           UUID warehouseId, Long availableQuantity) {
        this(productId, productName, description, categoryId, brandId, status, categoryName, brandName,
                createdAt, updatedAt, warehouseId, availableQuantity, null);
    }
    public ProductResponse(UUID productId, String productName, String description,
                           UUID categoryId, UUID brandId, ProductStatus status) {
        this(productId, productName, description, categoryId, brandId, status, null, null, null, null, null, null, null);
    }
}
