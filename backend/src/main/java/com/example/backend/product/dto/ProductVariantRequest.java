package com.example.backend.product.dto;

import com.example.backend.product.entity.ProductVariantStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class ProductVariantRequest {

    @NotNull(message = "productId là bắt buộc")
    private UUID productId;

    @NotBlank(message = "SKU là bắt buộc")
    @Size(max = 100, message = "SKU không được vượt quá 100 ký tự")
    private String sku;

    @NotNull(message = "Giá bán là bắt buộc")
    @DecimalMin(value = "0", message = "Giá bán phải lớn hơn hoặc bằng 0")
    @Digits(integer = 13, fraction = 2, message = "Giá bán tối đa 13 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal price;

    @NotNull(message = "Giá vốn là bắt buộc")
    @DecimalMin(value = "0", message = "Giá vốn phải lớn hơn hoặc bằng 0")
    @Digits(integer = 13, fraction = 2, message = "Giá vốn tối đa 13 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal costPrice;

    @Size(max = 100, message = "Màu sắc không được vượt quá 100 ký tự")
    private String color;

    @Size(max = 100, message = "Dung lượng không được vượt quá 100 ký tự")
    private String storage;

    @Size(max = 100, message = "RAM không được vượt quá 100 ký tự")
    private String ram;

    private ProductVariantStatus status;

    public ProductVariantRequest() {
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
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
}
