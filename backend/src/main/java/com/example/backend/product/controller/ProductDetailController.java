package com.example.backend.product.controller;

import com.example.backend.product.dto.ProductDetailResponse;
import com.example.backend.product.service.ProductDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductDetailController {

    private final ProductDetailService productDetailService;

    public ProductDetailController(ProductDetailService productDetailService) {
        this.productDetailService = productDetailService;
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<ProductDetailResponse> getDetail(@PathVariable UUID id,
                                                           @RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(productDetailService.getProductDetail(id, warehouseId));
    }
}
