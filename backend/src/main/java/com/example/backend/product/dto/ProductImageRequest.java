package com.example.backend.product.dto;

import com.example.backend.product.validation.ValidProductImageUrl;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProductImageRequest {

    @NotBlank(message = "Đường dẫn ảnh là bắt buộc")
    @Size(max = 1000, message = "Đường dẫn ảnh không được vượt quá 1000 ký tự")
    @ValidProductImageUrl
    private String imageUrl;

    @JsonProperty("isPrimary")
    private Boolean isPrimary;

    public ProductImageRequest() {
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }
}
