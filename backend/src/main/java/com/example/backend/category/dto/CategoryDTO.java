package com.example.backend.category.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
// trả catagory cho api, ko trả entity trực tiếp để tránh lỗi infinite recursion khi serialize JSON.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryDTO {
    private UUID categoryId;
    private String categoryName;
    private String description;
    private UUID parentId;
    private String status;
}
