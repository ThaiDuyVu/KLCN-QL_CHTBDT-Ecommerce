package com.example.backend.product.service;

import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.ProductStatus;
import com.example.backend.product.entity.Brand;
import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.dto.ProductPageResponse;
import com.example.backend.product.exception.ProductInUseException;
import com.example.backend.product.exception.ProductNotFoundException;
import com.example.backend.product.exception.ProductReferenceNotFoundException;
import com.example.backend.product.exception.InvalidProductPaginationException;
import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.repository.BrandRepository;
import com.example.backend.product.repository.ProductSpecifications;
import com.example.backend.category.CategoryRepository;
import com.example.backend.category.entity.Category;
import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.dto.ProductPageResponse;
import com.example.backend.product.entity.Brand;
import com.example.backend.product.entity.Product;
import com.example.backend.product.exception.ProductInUseException;
import com.example.backend.product.exception.ProductNotFoundException;
import com.example.backend.product.exception.ProductReferenceNotFoundException;
import com.example.backend.product.exception.InvalidProductPaginationException;
import com.example.backend.product.repository.ProductSpecifications;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        return mapToResponse(saveProduct(product));
    }

    @Override
    public ProductPageResponse getProducts(int page, int size) {
        return getProducts(page, size, null, null, null, null);
    }

    @Override
    public ProductPageResponse getProducts(int page, int size, String keyword, UUID categoryId, UUID brandId, ProductStatus status) {
        if (page < 0) {
            throw new InvalidProductPaginationException("Page không được nhỏ hơn 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidProductPaginationException("Size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        // JPA setFirstResult accepts an int, even though Pageable uses a long offset.
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new InvalidProductPaginationException("Page vượt quá giới hạn offset được hỗ trợ");
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("productId")
        ));
        String normalizedKeyword = normalizeFilter(keyword);
        Page<Product> productPage = normalizedKeyword == null && categoryId == null
                && brandId == null && status == null
                ? productRepository.findAll(pageable)
                : productRepository.findAll(ProductSpecifications.withFilters(
                        normalizedKeyword, categoryId, brandId, status
                ), pageable);

        return new ProductPageResponse(
                productPage.getContent().stream().map(this::mapToResponse).toList(),
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages()
        );
    }

    @Override
    public ProductResponse getProductById(UUID id) {
        return mapToResponse(findProductById(id));
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = findProductById(id);
        applyRequest(product, request);
        return mapToResponse(saveProduct(product));
    }

    @Override
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProductById(id);
        try {
            productRepository.delete(product);
            // Detect FK RESTRICT failures here rather than at transaction commit.
            productRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ProductInUseException(
                    "Không thể xóa sản phẩm đang được dữ liệu khác tham chiếu: " + id,
                    exception
            );
        }
    }

    private Product findProductById(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(
                "Không tìm thấy sản phẩm với ID: " + id
        ));
    }

    private void applyRequest(Product product, ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ProductReferenceNotFoundException(
                        "Không tìm thấy danh mục với ID: " + request.categoryId()
                ));
        Brand brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new ProductReferenceNotFoundException(
                        "Không tìm thấy thương hiệu với ID: " + request.brandId()
                ));

        product.setProductName(request.productName().trim());
        product.setDescription(request.description());
        product.setCategory(category);
        product.setBrand(brand);
        // Preserve the existing default-on-create / keep-on-update behavior.
        if (request.status() != null) {
            product.setStatus(request.status());
        }
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Product saveProduct(Product product) {
        try {
            Product saved = productRepository.save(product);
            // Flush lifecycle timestamps and detect references removed concurrently before mapping.
            productRepository.flush();
            return saved;
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && ("fk_products_category".equals(constraint.getConstraintName())
                        || "fk_products_brand".equals(constraint.getConstraintName()))) {
                    throw new ProductReferenceNotFoundException(
                            "Danh mục hoặc thương hiệu được tham chiếu không còn tồn tại", exception
                    );
                }
            }
            throw exception;
        }
    }

    private ProductResponse mapToResponse(Product product) {
        return new ProductResponse(
                product.getProductId(),
                product.getProductName(),
                product.getDescription(),
                product.getCategory().getCategoryId(),
                product.getBrand().getBrandId(),
                product.getStatus(),
                product.getCategory().getCategoryName(),
                product.getBrand().getBrandName(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
