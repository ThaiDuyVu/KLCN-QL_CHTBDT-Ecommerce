package com.example.backend.category.service;

import com.example.backend.category.dto.CategoryRequest;
import com.example.backend.category.dto.CategoryResponse;
import java.util.List;

public interface CategoryService {
    CategoryResponse createCategory(CategoryRequest request); 
    
    List<CategoryResponse> getAllCategories();
}