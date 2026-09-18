package com.example.backend.product.service;

import com.example.backend.product.dto.SpecificationRequest;
import com.example.backend.product.dto.SpecificationResponse;

import java.util.UUID;
import java.util.List;

public interface SpecificationService {
    SpecificationResponse createSpecification(UUID productId, SpecificationRequest request);
    List<SpecificationResponse> getSpecifications(UUID productId);
    SpecificationResponse getSpecificationById(UUID productId, UUID specificationId);
    SpecificationResponse updateSpecification(UUID productId, UUID specificationId, SpecificationRequest request);
    void deleteSpecification(UUID productId, UUID specificationId);
}
