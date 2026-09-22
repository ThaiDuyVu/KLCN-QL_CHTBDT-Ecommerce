package com.example.backend.product.service;

import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.dto.ProductVariantPageResponse;
import com.example.backend.product.dto.ProductVariantRequest;
import com.example.backend.product.dto.ProductVariantResponse;
import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.exception.InvalidProductVariantPaginationException;
import com.example.backend.product.exception.ProductNotFoundException;
import com.example.backend.product.exception.ProductReferenceNotFoundException;
import com.example.backend.product.exception.ProductVariantInUseException;
import com.example.backend.product.exception.ProductVariantNotFoundException;
import com.example.backend.product.exception.ProductVariantSkuAlreadyExistsException;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.inventory.repository.InventoryRepository;
import com.example.backend.inventory.serial.repository.SerialNumberRepository;
import com.example.backend.inventory.exception.InventoryConflictException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductVariantServiceImpl implements ProductVariantService {

    private static final int MAX_PAGE_SIZE = 100;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SerialNumberRepository serialNumberRepository;

    public ProductVariantServiceImpl(ProductVariantRepository variantRepository, ProductRepository productRepository,
            InventoryRepository inventoryRepository, SerialNumberRepository serialNumberRepository) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.serialNumberRepository = serialNumberRepository;
    }

    @Override
    @Transactional
    public ProductVariantResponse createVariant(ProductVariantRequest request) {
        ProductVariant variant = new ProductVariant();
        applyRequest(variant, request);
        return mapToResponse(saveVariant(variant));
    }

    @Override
    public ProductVariantPageResponse getVariants(UUID productId, int page, int size) {
        if (page < 0) {
            throw new InvalidProductVariantPaginationException("Page không được nhỏ hơn 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidProductVariantPaginationException("Size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new InvalidProductVariantPaginationException("Page vượt quá giới hạn offset được hỗ trợ");
        }
        if (productId != null && !productRepository.existsById(productId)) {
            throw new ProductNotFoundException("Không tìm thấy sản phẩm với ID: " + productId);
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.asc("sku"), Sort.Order.asc("variantId")
        ));
        Page<ProductVariant> variants = productId == null
                ? variantRepository.findAll(pageable)
                : variantRepository.findByProduct_ProductId(productId, pageable);
        return new ProductVariantPageResponse(
                variants.getContent().stream().map(this::mapToResponse).toList(),
                variants.getNumber(), variants.getSize(), variants.getTotalElements(), variants.getTotalPages()
        );
    }

    @Override
    public ProductVariantResponse getVariantById(UUID id) {
        return mapToResponse(findVariantById(id));
    }

    @Override
    public List<ProductVariantResponse> getVariantsByProductId(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException("Không tìm thấy sản phẩm với ID: " + productId);
        }
        return variantRepository.findByProduct_ProductId(productId, Sort.by(
                Sort.Order.asc("sku"), Sort.Order.asc("variantId")
        )).stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public ProductVariantResponse updateVariant(UUID id, ProductVariantRequest request) {
        ProductVariant variant = findVariantById(id);
        applyRequest(variant, request);
        return mapToResponse(saveVariant(variant));
    }

    @Override
    @Transactional
    public void deleteVariant(UUID id) {
        ProductVariant variant = findVariantById(id);
        try {
            variantRepository.delete(variant);
            // Preserve FK RESTRICT and translate failures before the transaction commits.
            variantRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ProductVariantInUseException(
                    "Không thể xóa biến thể đang được dữ liệu khác tham chiếu: " + id, exception
            );
        }
    }

    private ProductVariant findVariantById(UUID id) {
        return variantRepository.findById(id).orElseThrow(() -> new ProductVariantNotFoundException(
                "Không tìm thấy biến thể sản phẩm với ID: " + id
        ));
    }

    private void applyRequest(ProductVariant variant, ProductVariantRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(
                        "Không tìm thấy sản phẩm với ID: " + request.getProductId()
                ));
        String sku = request.getSku().trim();
        // Match the case-sensitive UNIQUE constraint; unchanged SKU on update is allowed.
        boolean duplicate = variant.getVariantId() == null
                ? variantRepository.existsBySku(sku)
                : variantRepository.existsBySkuAndVariantIdNot(sku, variant.getVariantId());
        if (duplicate) {
            throw new ProductVariantSkuAlreadyExistsException("SKU đã tồn tại: " + sku);
        }
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setPrice(request.getPrice());
        variant.setCostPrice(request.getCostPrice());
        variant.setColor(request.getColor());
        variant.setStorage(request.getStorage());
        variant.setRam(request.getRam());
        // Status is optional: retain ACTIVE on create or the persisted value on update.
        if (request.getStatus() != null) {
            variant.setStatus(request.getStatus());
        }
        if (request.getTrackingType() != null) {
            if (variant.getVariantId() != null && variant.getTrackingType() != request.getTrackingType()
                    && (serialNumberRepository.existsByVariant_VariantId(variant.getVariantId())
                    || inventoryRepository.existsByVariant_VariantIdAndQuantityGreaterThan(variant.getVariantId(), 0))) {
                throw new InventoryConflictException("Không thể đổi trackingType khi biến thể đã có tồn kho hoặc serial");
            }
            variant.setTrackingType(request.getTrackingType());
        }
        if (request.getWarrantyMonths() != null) {
            variant.setWarrantyMonths(request.getWarrantyMonths());
        }
    }

    private ProductVariant saveVariant(ProductVariant variant) {
        try {
            return variantRepository.saveAndFlush(variant);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint) {
                    if ("uq_product_variants_sku".equals(constraint.getConstraintName())) {
                        throw new ProductVariantSkuAlreadyExistsException(
                                "SKU đã tồn tại: " + variant.getSku(), exception
                        );
                    }
                    if ("fk_product_variants_product".equals(constraint.getConstraintName())) {
                        throw new ProductReferenceNotFoundException(
                                "Sản phẩm được tham chiếu không còn tồn tại: " + variant.getProduct().getProductId(),
                                exception
                        );
                    }
                }
            }
            throw exception;
        }
    }

    private ProductVariantResponse mapToResponse(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getVariantId(), variant.getProduct().getProductId(), variant.getProduct().getProductName(),
                variant.getSku(), variant.getPrice(), variant.getCostPrice(), variant.getColor(),
                variant.getStorage(), variant.getRam(), variant.getStatus(), variant.getTrackingType(),
                variant.getWarrantyMonths(), null, null
        );
    }
}
