package com.example.backend.category.service;

import java.util.UUID;
import java.util.stream.Collectors;

import com.example.backend.category.entity.Category;
import com.example.backend.category.CategoryRepository;
import com.example.backend.category.dto.CategoryRequest;
import com.example.backend.category.dto.CategoryResponse;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = new Category();
        category.setCategoryName(request.categoryName());
        category.setDescription(request.description());
        
        if (request.status() != null && !request.status().isEmpty()) {
            category.setStatus(request.status());
        }
        if (request.parentId() != null) {
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục cha"));
            category.setParent(parent);
        }

        Category saved = categoryRepository.save(category);
        return mapToResponse(saved);
    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList()); 
    }
    //Xem chi tiết danh mục theo ID
    @Override
    public CategoryResponse getCategoryById(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục này!"));
        return mapToResponse(category);
    }

    //cập nhật danh mục
    @Override
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục để sửa!"));

        existingCategory.setCategoryName(request.categoryName());
        existingCategory.setDescription(request.description());

        if (request.status() != null && !request.status().isEmpty()) {
            existingCategory.setStatus(request.status());
        }
        if (request.parentId() != null) {
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục cha!"));
            existingCategory.setParent(parent);
        } else {
            existingCategory.setParent(null); // Nếu khách muốn gỡ danh mục cha
        }

        Category updated = categoryRepository.save(existingCategory);
        return mapToResponse(updated);
    }

    //Xóa danh mục
    @Override
    public void deleteCategory(UUID id) {
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục để xóa!"));
        
        categoryRepository.delete(existingCategory);
    }
    private CategoryResponse mapToResponse(Category category) {
        UUID parentId = (category.getParent() != null) ? category.getParent().getCategoryId() : null;
        return new CategoryResponse(
                category.getCategoryId(),
                category.getCategoryName(),
                category.getDescription(),
                parentId,
                category.getStatus()
        );
    }
}
