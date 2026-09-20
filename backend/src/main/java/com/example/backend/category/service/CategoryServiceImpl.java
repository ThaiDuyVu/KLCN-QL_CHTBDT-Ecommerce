package com.example.backend.category.service;

import com.example.backend.category.CategoryRepository;
import com.example.backend.category.dto.CategoryRequest;
import com.example.backend.category.dto.CategoryResponse;
import com.example.backend.category.entity.Category;
import com.example.backend.category.exception.CategoryInUseException;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.category.exception.CategoryNotFoundException;
import com.example.backend.category.exception.InvalidCategoryParentException;
import com.example.backend.product.repository.ProductRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = new Category();
        applyRequest(category, request);
        return mapToResponse(saveCategory(category));
    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    @Override
    public List<CategoryResponse> getRootCategories() {
        return categoryRepository.findByParentIsNull().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<CategoryResponse> getCategoryChildren(UUID parentId) {
        return categoryRepository.findByParent_CategoryId(parentId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public CategoryResponse getCategoryById(UUID id) {
        return mapToResponse(findCategoryById(id));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category existingCategory = categoryRepository.findById(id)
            .orElseThrow(() -> new BadRequestException("Không tìm thấy danh mục để sửa!"));

        existingCategory.setCategoryName(request.categoryName());
        existingCategory.setDescription(request.description());

        if (request.status() != null) {
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

    @Override
    @Transactional
    public void deleteCategory(UUID id) {
        Category category = findCategoryById(id);
        if (productRepository.existsByCategory_CategoryId(id)) {
            throw new CategoryInUseException("Không thể xóa danh mục đang được sản phẩm tham chiếu: " + id);
        }
        if (categoryRepository.existsByParent_CategoryId(id)) {
            throw new CategoryInUseException("Không thể xóa danh mục đang có danh mục con: " + id);
        }
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new CategoryInUseException(
                    "Không thể xóa danh mục đang được dữ liệu khác tham chiếu: " + id, exception
            );
        }
    }

    private Category saveCategory(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "fk_categories_parent".equals(constraint.getConstraintName())) {
                    throw new CategoryNotFoundException("Danh mục cha được tham chiếu không còn tồn tại");
                }
            }
            throw exception;
        }
    }

    private Category findCategoryById(UUID id) {
        return categoryRepository.findById(id).orElseThrow(() -> new CategoryNotFoundException(
                "Không tìm thấy danh mục với ID: " + id
        ));
    }

    private void applyRequest(Category category, CategoryRequest request) {
        Category parent = request.parentId() == null ? null : findCategoryById(request.parentId());
        validateParent(category, parent);
        category.setCategoryName(request.categoryName().trim());
        category.setDescription(request.description());
        category.setParent(parent);
        if (request.status() != null) {
            category.setStatus(request.status());
        }
    }

    private void validateParent(Category category, Category parent) {
        Set<UUID> visited = new HashSet<>();
        for (Category ancestor = parent; ancestor != null; ancestor = ancestor.getParent()) {
            UUID ancestorId = ancestor.getCategoryId();
            if (ancestorId.equals(category.getCategoryId()) || !visited.add(ancestorId)) {
                throw new InvalidCategoryParentException(
                        "Danh mục không thể làm cha của chính nó hoặc tạo chu trình cha/con"
                );
            }
        }
    }

    private CategoryResponse mapToResponse(Category category) {
        UUID parentId = category.getParent() == null ? null : category.getParent().getCategoryId();
        return new CategoryResponse(
                category.getCategoryId(), category.getCategoryName(), category.getDescription(),
                parentId, category.getStatus()
        );
    }
}
