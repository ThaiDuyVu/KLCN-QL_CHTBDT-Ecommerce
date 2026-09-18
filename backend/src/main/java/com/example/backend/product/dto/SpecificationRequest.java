package com.example.backend.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SpecificationRequest {

    @NotNull(message = "specKey là bắt buộc")
    @Size(max = 100, message = "Tên thông số không được vượt quá 100 ký tự")
    private String specKey;

    @NotNull(message = "specValue là bắt buộc")
    @Size(max = 1000, message = "Giá trị thông số không được vượt quá 1000 ký tự")
    private String specValue;

    public SpecificationRequest() {
    }

    public String getSpecKey() {
        return specKey;
    }

    public void setSpecKey(String specKey) {
        this.specKey = specKey;
    }

    public String getSpecValue() {
        return specValue;
    }

    public void setSpecValue(String specValue) {
        this.specValue = specValue;
    }
}
