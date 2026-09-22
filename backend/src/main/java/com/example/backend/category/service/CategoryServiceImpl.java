package com.example.backend.category.service;

import com.example.backend.category.CategoryRepository;
import com.example.backend.category.dto.CategoryRequest;
import com.example.backend.category.dto.CategoryResponse;
import com.example.backend.category.entity.Category;
import com.example.backend.category.exception.CategoryInUseException;
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
import java.util.stream.Collectors;

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
        return categoryRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
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
        Category category = findCategoryById(id);
        applyRequest(category, request);
        return mapToResponse(saveCategory(category));
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
        category.setCategoryName(request.categoryName() == null ? null : request.categoryName().trim());
        category.setDescription(request.description() == null ? null : request.description().trim());
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
