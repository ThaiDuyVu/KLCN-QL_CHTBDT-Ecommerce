package com.example.backend.product.controller;

import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.createProduct(request));
    }
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAll() {
        return ResponseEntity.ok(productService.getAllProducts());
    }
    //chi tiết sản phẩm theo ID
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    //sửa
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable UUID id, @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    //xóa
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok("sXóa sản phẩm thành công!");
    }
}
