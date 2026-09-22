package com.example.backend.product.entity;

import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "variant_id", nullable = false, updatable = false)
    private UUID variantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;

    @Column(
            name = "sku",
            nullable = false,
            unique = true,
            length = 100
    )
    private String sku;

    @Column(
            name = "price",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal price;

    @Column(
            name = "cost_price",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal costPrice;

    @Column(name = "color", length = 100)
    private String color;

    @Column(name = "storage", length = 100)
    private String storage;

    @Column(name = "ram", length = 100)
    private String ram;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ProductVariantStatus status = ProductVariantStatus.ACTIVE;

    @Column(name = "tracking_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ProductTrackingType trackingType = ProductTrackingType.NONE;

    @Column(name = "warranty_months", nullable = false)
    private Integer warrantyMonths = 0;

    public ProductVariant() {
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public String getRam() {
        return ram;
    }

    public void setRam(String ram) {
        this.ram = ram;
    }

    public ProductVariantStatus getStatus() {
        return status;
    }

    public void setStatus(ProductVariantStatus status) {
        this.status = status;
    }

    public ProductTrackingType getTrackingType() { return trackingType; }
    public void setTrackingType(ProductTrackingType trackingType) { this.trackingType = trackingType; }
    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }


}
