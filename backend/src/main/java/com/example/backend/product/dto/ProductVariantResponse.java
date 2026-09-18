package com.example.backend.product.dto;

import com.example.backend.product.entity.ProductVariantStatus;
import java.math.BigDecimal;
import java.util.UUID;

public class ProductVariantResponse {

    private final UUID variantId;
    private final UUID productId;
    private final String productName;
    private final String sku;
    private final BigDecimal price;
    private final BigDecimal costPrice;
    private final String color;
    private final String storage;
    private final String ram;
    private final ProductVariantStatus status;

    public ProductVariantResponse(
            UUID variantId,
            UUID productId,
            String productName,
            String sku,
            BigDecimal price,
            BigDecimal costPrice,
            String color,
            String storage,
            String ram,
            ProductVariantStatus status
    ) {
        this.variantId = variantId;
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.price = price;
        this.costPrice = costPrice;
        this.color = color;
        this.storage = storage;
        this.ram = ram;
        this.status = status;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public String getColor() {
        return color;
    }

    public String getStorage() {
        return storage;
    }

    public String getRam() {
        return ram;
    }

    public ProductVariantStatus getStatus() {
        return status;
    }
}
