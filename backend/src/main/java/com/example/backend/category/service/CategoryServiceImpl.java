package com.example.backend.category.service;

import java.util.UUID;
import com.example.backend.category.entity.Category;
import com.example.backend.category.CategoryRepository;
import com.example.backend.category.dto.CategoryRequest;
import com.example.backend.category.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    @Override
    public CategoryResponse createCategory(CategoryRequest request) {
        Category newCategory = new Category();
        newCategory.setCategoryName(request.categoryName());
        newCategory.setDescription(request.description());
        if (request.parentId() != null) {
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục cha"));
            newCategory.setParent(parent);
        }

        Category savedCategory = categoryRepository.save(newCategory);
        UUID parentIdToReturn = (savedCategory.getParent() != null) ? savedCategory.getParent().getCategoryId() : null;
        
        return new CategoryResponse(
                savedCategory.getCategoryId(),
                savedCategory.getCategoryName(),
                savedCategory.getDescription(),
                parentIdToReturn
        );
    }
    @Override
    public List<CategoryResponse> getAllCategories() {
        return null; 
    }
}