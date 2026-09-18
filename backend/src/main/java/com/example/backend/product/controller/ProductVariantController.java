package com.example.backend.product.controller;

import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.product.dto.ProductVariantPageResponse;
import com.example.backend.product.dto.ProductVariantRequest;
import com.example.backend.product.dto.ProductVariantResponse;
import com.example.backend.product.service.ProductVariantService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/product-variants")
public class ProductVariantController {

    private final ProductVariantService variantService;

    public ProductVariantController(ProductVariantService variantService) {
        this.variantService = variantService;
    }

    @PostMapping
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<ProductVariantResponse> create(@Valid @RequestBody ProductVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(variantService.createVariant(request));
    }

    @GetMapping(params = "!productId")
    public ResponseEntity<ProductVariantPageResponse> getVariants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(variantService.getVariants(null, page, size));
    }

    @GetMapping(params = "productId")
    public ResponseEntity<List<ProductVariantResponse>> getVariantsByProductId(@RequestParam UUID productId) {
        return ResponseEntity.ok(variantService.getVariantsByProductId(productId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductVariantResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(variantService.getVariantById(id));
    }

    @PutMapping("/{id}")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<ProductVariantResponse> update(
            @PathVariable UUID id, @Valid @RequestBody ProductVariantRequest request
    ) {
        return ResponseEntity.ok(variantService.updateVariant(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<String> delete(@PathVariable UUID id) {
        variantService.deleteVariant(id);
        return ResponseEntity.ok("Xóa biến thể sản phẩm thành công!");
    }
}
