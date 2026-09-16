package com.example.backend.category.service;

import com.example.backend.category.dto.CategoryRequest;
import com.example.backend.category.dto.CategoryResponse;
import java.util.List;
import java.util.UUID;

public interface CategoryService {
    CategoryResponse createCategory(CategoryRequest request); 
    
    List<CategoryResponse> getAllCategories();
    CategoryResponse getCategoryById(UUID id);
    CategoryResponse updateCategory(UUID id, CategoryRequest request);
    void deleteCategory(UUID id);
}