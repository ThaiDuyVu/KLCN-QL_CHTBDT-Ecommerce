package com.example.backend.product.controller;

import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.product.dto.SpecificationRequest;
import com.example.backend.product.dto.SpecificationResponse;
import com.example.backend.product.service.SpecificationService;
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
@RequestMapping("/api/v1/products/{productId}/specifications")
public class SpecificationController {

    private final SpecificationService specificationService;

    public SpecificationController(SpecificationService specificationService) {
        this.specificationService = specificationService;
    }

    @PostMapping
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<SpecificationResponse> create(
            @PathVariable UUID productId, @Valid @RequestBody SpecificationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(specificationService.createSpecification(productId, request));
    }

    @GetMapping
    public ResponseEntity<List<SpecificationResponse>> getSpecifications(@PathVariable UUID productId) {
        return ResponseEntity.ok(specificationService.getSpecifications(productId));
    }

    @GetMapping("/{specificationId}")
    public ResponseEntity<SpecificationResponse> getById(
            @PathVariable UUID productId, @PathVariable UUID specificationId
    ) {
        return ResponseEntity.ok(specificationService.getSpecificationById(productId, specificationId));
    }

    @PutMapping("/{specificationId}")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<SpecificationResponse> update(
            @PathVariable UUID productId, @PathVariable UUID specificationId,
            @Valid @RequestBody SpecificationRequest request
    ) {
        return ResponseEntity.ok(specificationService.updateSpecification(productId, specificationId, request));
    }

    @DeleteMapping("/{specificationId}")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<String> delete(@PathVariable UUID productId, @PathVariable UUID specificationId) {
        specificationService.deleteSpecification(productId, specificationId);
        return ResponseEntity.ok("Xóa thông số sản phẩm thành công!");
    }
}
