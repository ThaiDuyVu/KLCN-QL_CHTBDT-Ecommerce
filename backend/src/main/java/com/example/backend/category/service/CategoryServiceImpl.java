package com.example.backend.category.service;

import com.example.backend.category.entity.Category;
import com.example.backend.category.CategoryRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.category.dto.CategoryRequest;
import com.example.backend.category.dto.CategoryResponse;
import com.example.backend.product.repository.ProductRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
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
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy danh mục cha"));
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
    
    @Override
    public List<CategoryResponse> getRootCategories() {
        return categoryRepository.findByParentIsNull().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponse> getCategoryChildren(UUID parentId) {
        return categoryRepository.findByParent_CategoryId(parentId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    //Xem chi tiết danh mục theo ID
    @Override
    public CategoryResponse getCategoryById(UUID id) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new BadRequestException("Không tìm thấy danh mục này!"));
        return mapToResponse(category);
    }

    //cập nhật danh mục
    @Override
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category existingCategory = categoryRepository.findById(id)
            .orElseThrow(() -> new BadRequestException("Không tìm thấy danh mục để sửa!"));

        existingCategory.setCategoryName(request.categoryName());
        existingCategory.setDescription(request.description());

        if (request.status() != null && !request.status().isEmpty()) {
            existingCategory.setStatus(request.status());
        }
        if (request.parentId() != null) {
                Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy danh mục cha!"));
            // THuật toán chống vòng lặp like A -> B -> A in Service
                validateParentCycle(existingCategory, parent);
            existingCategory.setParent(parent);
        } else {
            existingCategory.setParent(null); // Nếu khách muốn gỡ danh mục cha
        }

        Category updated = categoryRepository.save(existingCategory);
        return mapToResponse(updated);
    }

    // Kiểm tra vòng lặp khi thay đổi parent: nếu parent mới là con của currentCategory thì lỗi
    private void validateParentCycle(Category currentCategory, Category newParent) {
        if (newParent == null) return; // nếu chuyển thành root (update thành danh mục gốc) => an toàn

        // Nếu parent mới chính là bản thân nó
        if (currentCategory.getCategoryId().equals(newParent.getCategoryId())) {
            throw new BadRequestException("Không thể chọn danh mục hiện tại làm danh mục cha.");
        }

        // Truy ngược lên các cấp parent để check vòng lặp. Sử dụng repository để đảm bảo fetch khi LAZY.
        Category checkNode = newParent;
        while (checkNode.getParent() != null) {
            UUID parentId = checkNode.getParent().getCategoryId();
            if (parentId.equals(currentCategory.getCategoryId())) {
                throw new BadRequestException("Phát hiện vòng lặp: Không thể chọn danh mục con làm danh mục cha.");
            }
            // Tiến cấp tiếp parent trên DB để đảm bảo trường parent được khởi tạo (phòng LAZY)
            final Category next = categoryRepository.findById(parentId).orElse(null);
            if (next == null) break;
            checkNode = next;
        }
    }

    //Xóa danh mục
    @Override
    public void deleteCategory(UUID id) {
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy danh mục để xóa!"));
        if(categoryRepository.existsByParent_CategoryId(id)) {
            throw new BadRequestException("Không thể xóa danh mục này vì nó có danh mục con!");
        }
        if(productRepository.existsByCategory_CategoryId(id)) {
            throw new BadRequestException("Không thể xóa danh mục này vì nó có sản phẩm liên quan!");
        }
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
