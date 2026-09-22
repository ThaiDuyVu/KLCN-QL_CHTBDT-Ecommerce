package com.example.backend.product.dto;

import com.example.backend.product.entity.ProductVariantStatus;
import com.example.backend.product.entity.ProductTrackingType;
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
    private final ProductTrackingType trackingType;
    private final Integer warrantyMonths;
    private final UUID warehouseId;
    private final Long availableQuantity;

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
            ProductVariantStatus status,
            ProductTrackingType trackingType
    ) {
        this(variantId, productId, productName, sku, price, costPrice, color, storage, ram, status,
                trackingType, 0, null, null);
    }

    public ProductVariantResponse(
            UUID variantId, UUID productId, String productName, String sku,
            BigDecimal price, BigDecimal costPrice, String color, String storage, String ram,
            ProductVariantStatus status, ProductTrackingType trackingType,
            UUID warehouseId, Long availableQuantity
    ) {
        this(variantId, productId, productName, sku, price, costPrice, color, storage, ram, status,
                trackingType, 0, warehouseId, availableQuantity);
    }

    public ProductVariantResponse(
            UUID variantId, UUID productId, String productName, String sku,
            BigDecimal price, BigDecimal costPrice, String color, String storage, String ram,
            ProductVariantStatus status, ProductTrackingType trackingType, Integer warrantyMonths,
            UUID warehouseId, Long availableQuantity
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
        this.trackingType = trackingType;
        this.warrantyMonths = warrantyMonths;
        this.warehouseId = warehouseId;
        this.availableQuantity = availableQuantity;
    }

    public ProductVariantResponse(UUID variantId, UUID productId, String productName, String sku,
            BigDecimal price, BigDecimal costPrice, String color, String storage, String ram,
            ProductVariantStatus status) {
        this(variantId, productId, productName, sku, price, costPrice, color, storage, ram, status,
                ProductTrackingType.NONE);
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

    public ProductTrackingType getTrackingType() { return trackingType; }
    public Integer getWarrantyMonths() { return warrantyMonths; }
    public UUID getWarehouseId() { return warehouseId; }
    public Long getAvailableQuantity() { return availableQuantity; }
}
