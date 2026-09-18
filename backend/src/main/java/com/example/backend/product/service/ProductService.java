package com.example.backend.product.service;

import com.example.backend.product.entity.ProductStatus;
import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.dto.ProductPageResponse;
import java.util.UUID;

public interface ProductService {
    ProductResponse createProduct(ProductRequest request);
    ProductPageResponse getProducts(int page, int size);
    ProductPageResponse getProducts(int page, int size, String keyword, UUID categoryId, UUID brandId, ProductStatus status);
    ProductResponse getProductById(UUID id);
    ProductResponse updateProduct(UUID id, ProductRequest request);
    void deleteProduct(UUID id);
}
