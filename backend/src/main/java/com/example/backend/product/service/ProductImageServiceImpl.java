package com.example.backend.product.service;

import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.dto.ProductImageRequest;
import com.example.backend.product.dto.ProductImageResponse;
import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.ProductImage;
import com.example.backend.product.exception.ProductImageConflictException;
import com.example.backend.product.exception.ProductImageNotFoundException;
import com.example.backend.product.exception.ProductNotFoundException;
import com.example.backend.product.exception.ProductReferenceNotFoundException;
import com.example.backend.product.repository.ProductImageRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;

    public ProductImageServiceImpl(ProductRepository productRepository, ProductImageRepository imageRepository) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
    }

    @Override
    @Transactional
    public ProductImageResponse createImage(UUID productId, ProductImageRequest request) {
        Product product = lockProduct(productId);
        ProductImage image = new ProductImage();
        image.setProduct(product);
        applyRequest(image, request);
        return mapToResponse(saveImage(image));
    }

    @Override
    public List<ProductImageResponse> getImages(UUID productId) {
        ensureProductExists(productId);
        return imageRepository.findByProduct_ProductId(productId, Sort.by(
                Sort.Order.desc("primary"), Sort.Order.asc("imageId")
        )).stream().map(this::mapToResponse).toList();
    }

    @Override
    public ProductImageResponse getImageById(UUID productId, UUID imageId) {
        ensureProductExists(productId);
        return mapToResponse(findImage(productId, imageId));
    }

    @Override
    @Transactional
    public ProductImageResponse updateImage(UUID productId, UUID imageId, ProductImageRequest request) {
        lockProduct(productId);
        ProductImage image = findImage(productId, imageId);
        applyRequest(image, request);
        return mapToResponse(saveImage(image));
    }

    @Override
    @Transactional
    public void deleteImage(UUID productId, UUID imageId) {
        lockProduct(productId);
        ProductImage image = findImage(productId, imageId);
        try {
            imageRepository.delete(image);
            imageRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ProductImageConflictException("Không thể xóa ảnh đang được dữ liệu khác tham chiếu", exception);
        }
    }

    private void applyRequest(ProductImage image, ProductImageRequest request) {
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            imageRepository.findByProduct_ProductIdAndPrimaryTrue(image.getProduct().getProductId())
                    .filter(current -> !current.getImageId().equals(image.getImageId()))
                    .ifPresent(current -> {
                        current.setPrimary(false);
                        // Flush the old primary BEFORE promoting the new one to satisfy the partial unique index.
                        saveImage(current);
                    });
        }
        image.setImageUrl(request.getImageUrl());
        // Missing/null isPrimary means false on create and preserves the existing value on update.
        if (request.getIsPrimary() != null) {
            image.setPrimary(request.getIsPrimary());
        }
    }

    private Product lockProduct(UUID productId) {
        try {
            // Serialize all image writes for a product, even when it has no images yet.
            return productRepository.findByIdForUpdate(productId).orElseThrow(() -> new ProductNotFoundException(
                    "Không tìm thấy sản phẩm với ID: " + productId
            ));
        } catch (PessimisticLockingFailureException exception) {
            throw new ProductImageConflictException("Ảnh của sản phẩm đang được cập nhật, vui lòng thử lại", exception);
        }
    }

    private void ensureProductExists(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException("Không tìm thấy sản phẩm với ID: " + productId);
        }
    }

    private ProductImage findImage(UUID productId, UUID imageId) {
        return imageRepository.findByImageIdAndProduct_ProductId(imageId, productId)
                .orElseThrow(() -> new ProductImageNotFoundException(
                        "Không tìm thấy ảnh với ID " + imageId + " thuộc sản phẩm: " + productId
                ));
    }

    private ProductImage saveImage(ProductImage image) {
        try {
            return imageRepository.saveAndFlush(image);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint) {
                    if ("uq_product_images_primary".equals(constraint.getConstraintName())) {
                        throw new ProductImageConflictException(
                                "Sản phẩm đã có ảnh chính, vui lòng tải lại dữ liệu và thử lại", exception
                        );
                    }
                    if ("fk_product_images_product".equals(constraint.getConstraintName())) {
                        throw new ProductReferenceNotFoundException(
                                "Sản phẩm được tham chiếu không còn tồn tại", exception
                        );
                    }
                }
            }
            throw exception;
        }
    }

    private ProductImageResponse mapToResponse(ProductImage image) {
        return new ProductImageResponse(
                image.getImageId(), image.getProduct().getProductId(), image.getImageUrl(), image.getPrimary()
        );
    }
}
