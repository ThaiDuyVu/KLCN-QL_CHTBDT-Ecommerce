package com.example.backend.product.dto;

import java.util.UUID;

public class SpecificationResponse {

    private final UUID specificationId;
    private final UUID productId;
    private final String specKey;
    private final String specValue;

    public SpecificationResponse(UUID specificationId, UUID productId, String specKey, String specValue) {
        this.specificationId = specificationId;
        this.productId = productId;
        this.specKey = specKey;
        this.specValue = specValue;
    }

    public UUID getSpecificationId() {
        return specificationId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getSpecKey() {
        return specKey;
    }

    public String getSpecValue() {
        return specValue;
    }
}
