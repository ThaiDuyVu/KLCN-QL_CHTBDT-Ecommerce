package com.example.backend.product.service;

import com.example.backend.category.CategoryRepository;
import com.example.backend.category.entity.Category;
import com.example.backend.product.dto.ProductPageResponse;
import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.entity.Brand;
import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.ProductStatus;
import com.example.backend.product.entity.ProductImage;
import com.example.backend.product.exception.InvalidProductPaginationException;
import com.example.backend.product.exception.ProductInUseException;
import com.example.backend.product.exception.ProductNotFoundException;
import com.example.backend.product.exception.ProductReferenceNotFoundException;
import com.example.backend.product.repository.BrandRepository;
import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.repository.ProductSpecifications;
import com.example.backend.product.repository.ProductImageRepository;
import com.example.backend.inventory.repository.InventoryRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final com.example.backend.promotion.service.PromotionService promotions;
    private final com.example.backend.product.repository.ProductVariantRepository variants;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            InventoryRepository inventoryRepository,
            ProductImageRepository productImageRepository,
            com.example.backend.promotion.service.PromotionService promotions,
            com.example.backend.product.repository.ProductVariantRepository variants
    ) {
        this.promotions=promotions; this.variants=variants;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.inventoryRepository = inventoryRepository;
        this.productImageRepository = productImageRepository;
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
        return getProducts(page, size, keyword, categoryId, brandId, status, null);
    }

    @Override
    public ProductPageResponse getProducts(int page, int size, String keyword, UUID categoryId, UUID brandId,
                                           ProductStatus status, UUID warehouseId) {
        return getProducts(page, size, keyword, categoryId, brandId, status, warehouseId, false);
    }

    @Override
    public ProductPageResponse getProducts(int page, int size, String keyword, UUID categoryId, UUID brandId,
                                           ProductStatus status, UUID warehouseId, boolean onSale) {
        if (page < 0) {
            throw new InvalidProductPaginationException("Page không được nhỏ hơn 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidProductPaginationException("Size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new InvalidProductPaginationException("Page vượt quá giới hạn offset được hỗ trợ");
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("productId")
        ));
        String normalizedKeyword = normalizeFilter(keyword);
        var filters = ProductSpecifications.withFilters(normalizedKeyword, categoryId, brandId, status);
        if (onSale) filters = filters.and(ProductSpecifications.onSale(OffsetDateTime.now()));
        Page<Product> productPage = productRepository.findAll(filters, pageable);

        var pricing = productPricing(productPage.getContent());
        Map<UUID, Long> availability = productAvailability(warehouseId, productPage.getContent());
        Map<UUID, String> primaryImages = primaryImages(productPage.getContent());
        return new ProductPageResponse(
                productPage.getContent().stream().map(product -> mapToResponse(
                        product, warehouseId, warehouseId == null ? null : availability.getOrDefault(product.getProductId(), 0L),
                        primaryImages.get(product.getProductId())
                ).withPricing(pricing.get(product.getProductId()))).toList(),
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages()
        );
    }

    @Override
    public ProductResponse getProductById(UUID id) {
        return getProductById(id, null);
    }

    @Override
    public ProductResponse getProductById(UUID id, UUID warehouseId) {
        Product product = findProductById(id);
        Map<UUID, Long> availability = productAvailability(warehouseId, java.util.List.of(product));
        String primaryImage = primaryImages(java.util.List.of(product)).get(product.getProductId());
        return mapToResponse(product, warehouseId,
                warehouseId == null ? null : availability.getOrDefault(product.getProductId(), 0L), primaryImage)
                .withPricing(productPricing(java.util.List.of(product)).get(product.getProductId()));
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
        return mapToResponse(product, null, null,
                primaryImages(java.util.List.of(product)).get(product.getProductId()))
                .withPricing(productPricing(java.util.List.of(product)).get(product.getProductId()));
    }

    private ProductResponse mapToResponse(Product product, UUID warehouseId, Long availableQuantity,
                                          String primaryImageUrl) {
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
                product.getUpdatedAt(),
                warehouseId,
                availableQuantity,
                primaryImageUrl
        );
    }

    private Map<UUID, com.example.backend.promotion.service.PromotionPrice> productPricing(java.util.List<Product> rows) {
        if (rows.isEmpty()) return Map.of();
        var ids=rows.stream().map(Product::getProductId).toList();
        var resolved=promotions.resolve(ids,java.time.OffsetDateTime.now());
        Map<UUID, com.example.backend.promotion.service.PromotionPrice> result=new java.util.HashMap<>();
        for (var variant:variants.findByProduct_ProductIdInAndStatus(ids,com.example.backend.product.entity.ProductVariantStatus.ACTIVE)) {
            var productId=variant.getProduct().getProductId();
            var price=promotions.calculate(variant.getPrice(),resolved.get(productId));
            result.merge(productId,price,(a,b)-> {
                int comparison=a.getFinalUnitPrice().compareTo(b.getFinalUnitPrice());
                return comparison<0 || (comparison==0 && a.getUnitPrice().compareTo(b.getUnitPrice())<=0)?a:b;
            });
        }
        return result;
    }

    private Map<UUID, Long> productAvailability(UUID warehouseId, java.util.List<Product> products) {
        if (warehouseId == null || products.isEmpty()) return Map.of();
        return inventoryRepository.findProductAvailability(warehouseId,
                        products.stream().map(Product::getProductId).toList())
                .stream().collect(Collectors.toMap(
                        InventoryRepository.ProductAvailability::getProductId,
                        InventoryRepository.ProductAvailability::getAvailableQuantity
                ));
    }

    private Map<UUID, String> primaryImages(java.util.List<Product> products) {
        if (products.isEmpty()) return Map.of();
        return productImageRepository.findByProduct_ProductIdInAndPrimaryTrue(
                        products.stream().map(Product::getProductId).toList())
                .stream().collect(Collectors.toMap(
                        image -> image.getProduct().getProductId(),
                        ProductImage::getImageUrl,
                        (first, ignored) -> first
                ));
    }
}
