package com.example.backend.category.dto;

import com.example.backend.category.entity.CategoryStatus;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Tên danh mục là bắt buộc")
        @Size(max = 255, message = "Tên danh mục không được vượt quá 255 ký tự")
        String categoryName,
        String description,
        UUID parentId,
        CategoryStatus status
) {}
