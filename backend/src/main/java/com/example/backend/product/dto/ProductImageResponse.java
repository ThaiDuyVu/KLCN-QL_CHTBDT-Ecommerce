package com.example.backend.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class ProductImageResponse {

    private final UUID imageId;
    private final UUID productId;
    private final String imageUrl;
    @JsonProperty("isPrimary")
    private final Boolean isPrimary;

    public ProductImageResponse(UUID imageId, UUID productId, String imageUrl, Boolean isPrimary) {
        this.imageId = imageId;
        this.productId = productId;
        this.imageUrl = imageUrl;
        this.isPrimary = isPrimary;
    }

    public UUID getImageId() {
        return imageId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }
}
