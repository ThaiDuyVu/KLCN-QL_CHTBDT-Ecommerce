package com.example.backend.product.controller;

import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.ProductSpecificationsRequest;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.service.ProductUploadService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
public class ProductUploadController {
    private final ProductUploadService service;
    public ProductUploadController(ProductUploadService service) { this.service = service; }

    @PostMapping(value = "/api/v1/products/with-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<ProductResponse> create(@Valid @RequestPart("product") ProductRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(defaultValue = "0") int primaryImageIndex,
            @Valid @RequestPart(value = "specifications", required = false) ProductSpecificationsRequest specifications) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, images, primaryImageIndex,
                specifications == null ? List.of() : specifications.getSpecifications()));
    }

    @GetMapping("/api/product-media/{filename}")
    public ResponseEntity<Resource> read(@PathVariable String filename) {
        Resource resource = service.read(filename);
        return ResponseEntity.ok().contentType(filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7))).body(resource);
    }
}
