package com.example.backend.category.controller;

import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.category.dto.CategoryRequest;
import com.example.backend.category.dto.CategoryResponse;
import com.example.backend.category.service.CategoryService;
import org.springframework.web.bind.annotation.CrossOrigin;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@CrossOrigin(origins = "*")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.createCategory(request));
    }
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAll() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }
    //api xem chi tiet danh mục theo ID
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    //api cap nhat danh mục
    @PutMapping("/{id}")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    //api xóa danh mục
    @DeleteMapping("/{id}")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<String> delete(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok("Xóa danh mục thành công!");
    }

    // Lấy danh mục gốc (parent IS NULL)
    @GetMapping("/roots")
    public ResponseEntity<java.util.List<CategoryResponse>> getRootCategories() {
        return ResponseEntity.ok(categoryService.getRootCategories());
    }

    // Lấy danh mục con của 1 danh mục
    @GetMapping("/{id}/children")
    public ResponseEntity<java.util.List<CategoryResponse>> getCategoryChildren(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.getCategoryChildren(id));
    }
}
