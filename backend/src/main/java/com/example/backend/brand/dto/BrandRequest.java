package com.example.backend.brand.dto;

import com.example.backend.product.entity.BrandStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BrandRequest {

    @NotBlank(message = "Tên thương hiệu là bắt buộc")
    @Size(max = 255, message = "Tên thương hiệu không được vượt quá 255 ký tự")
    private String brandName;

    private String description;

    private BrandStatus status;

    public BrandRequest() {
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BrandStatus getStatus() {
        return status;
    }

    public void setStatus(BrandStatus status) {
        this.status = status;
    }
}
