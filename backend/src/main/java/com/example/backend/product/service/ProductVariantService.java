package com.example.backend.product.service;

import com.example.backend.product.dto.ProductVariantPageResponse;
import com.example.backend.product.dto.ProductVariantRequest;
import com.example.backend.product.dto.ProductVariantResponse;

import java.util.UUID;
import java.util.List;

public interface ProductVariantService {
    ProductVariantResponse createVariant(ProductVariantRequest request);
    ProductVariantPageResponse getVariants(UUID productId, int page, int size);
    List<ProductVariantResponse> getVariantsByProductId(UUID productId);
    ProductVariantResponse getVariantById(UUID id);
    ProductVariantResponse updateVariant(UUID id, ProductVariantRequest request);
    void deleteVariant(UUID id);
}
