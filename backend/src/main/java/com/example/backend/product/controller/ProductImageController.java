package com.example.backend.product.controller;

import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.product.dto.ProductImageRequest;
import com.example.backend.product.dto.ProductImageResponse;
import com.example.backend.product.service.ProductImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService imageService;

    public ProductImageController(ProductImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<ProductImageResponse> create(
            @PathVariable UUID productId, @Valid @RequestBody ProductImageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(imageService.createImage(productId, request));
    }

    @GetMapping
    public ResponseEntity<List<ProductImageResponse>> getImages(@PathVariable UUID productId) {
        return ResponseEntity.ok(imageService.getImages(productId));
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<ProductImageResponse> getById(@PathVariable UUID productId, @PathVariable UUID imageId) {
        return ResponseEntity.ok(imageService.getImageById(productId, imageId));
    }

    @PutMapping("/{imageId}")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<ProductImageResponse> update(
            @PathVariable UUID productId, @PathVariable UUID imageId, @Valid @RequestBody ProductImageRequest request
    ) {
        return ResponseEntity.ok(imageService.updateImage(productId, imageId, request));
    }

    @DeleteMapping("/{imageId}")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<String> delete(@PathVariable UUID productId, @PathVariable UUID imageId) {
        imageService.deleteImage(productId, imageId);
        return ResponseEntity.ok("Xóa ảnh sản phẩm thành công!");
    }
}
