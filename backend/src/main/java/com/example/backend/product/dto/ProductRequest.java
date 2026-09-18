package com.example.backend.product.dto;

import com.example.backend.product.entity.ProductStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProductRequest(
    @NotBlank(message = "Tên sản phẩm là bắt buộc")
    @Size(max = 255, message = "Tên sản phẩm không được vượt quá 255 ký tự")
    String productName,
    String description,
    @NotNull(message = "categoryId là bắt buộc")
    UUID categoryId,
    @NotNull(message = "brandId là bắt buộc")
    UUID brandId,
    ProductStatus status
) {}
