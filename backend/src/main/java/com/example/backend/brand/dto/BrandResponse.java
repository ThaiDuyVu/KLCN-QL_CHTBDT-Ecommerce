package com.example.backend.brand.dto;

import com.example.backend.product.entity.BrandStatus;
import java.util.UUID;

public class BrandResponse {

    private final UUID brandId;
    private final String brandName;
    private final String description;
    private final BrandStatus status;

    public BrandResponse(UUID brandId, String brandName, String description, BrandStatus status) {
        this.brandId = brandId;
        this.brandName = brandName;
        this.description = description;
        this.status = status;
    }

    public UUID getBrandId() {
        return brandId;
    }

    public String getBrandName() {
        return brandName;
    }

    public String getDescription() {
        return description;
    }

    public BrandStatus getStatus() {
        return status;
    }
}
