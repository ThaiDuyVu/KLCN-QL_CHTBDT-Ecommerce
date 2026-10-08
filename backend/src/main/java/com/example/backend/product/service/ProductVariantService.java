package com.example.backend.product.service;

import com.example.backend.product.dto.ProductVariantPageResponse;
import com.example.backend.product.dto.ProductVariantRequest;
import com.example.backend.product.dto.ProductVariantResponse;

import java.util.UUID;
import java.util.List;
import com.example.backend.product.entity.ProductTrackingType;

public interface ProductVariantService {
    ProductVariantResponse createVariant(ProductVariantRequest request);
    ProductVariantPageResponse getVariants(UUID productId, int page, int size);
    ProductVariantPageResponse searchVariants(int page, int size, String keyword, UUID categoryId,
                                              UUID brandId, ProductTrackingType trackingType);
    List<ProductVariantResponse> getVariantsByProductId(UUID productId);
    ProductVariantResponse getVariantById(UUID id);
    ProductVariantResponse updateVariant(UUID id, ProductVariantRequest request);
    void deleteVariant(UUID id);
}
