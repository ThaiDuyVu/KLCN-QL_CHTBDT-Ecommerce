package com.example.backend.product.service;

import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.ProductResponse;
import java.util.List;
import java.util.UUID;

public interface ProductService {
    ProductResponse createProduct(ProductRequest request);
    List<ProductResponse> getAllProducts();
    ProductResponse getProductById(UUID id);
    ProductResponse updateProduct(UUID id, ProductRequest request);
    void deleteProduct(UUID id);
}