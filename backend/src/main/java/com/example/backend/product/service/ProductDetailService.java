package com.example.backend.product.service;

import com.example.backend.product.dto.ProductDetailResponse;

import java.util.UUID;

public interface ProductDetailService {
    ProductDetailResponse getProductDetail(UUID productId);
}
