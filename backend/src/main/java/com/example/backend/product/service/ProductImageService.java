package com.example.backend.product.service;

import com.example.backend.product.dto.ProductImageRequest;
import com.example.backend.product.dto.ProductImageResponse;

import java.util.UUID;
import java.util.List;

public interface ProductImageService {
    ProductImageResponse createImage(UUID productId, ProductImageRequest request);
    List<ProductImageResponse> getImages(UUID productId);
    ProductImageResponse getImageById(UUID productId, UUID imageId);
    ProductImageResponse updateImage(UUID productId, UUID imageId, ProductImageRequest request);
    void deleteImage(UUID productId, UUID imageId);
}
