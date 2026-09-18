package com.example.backend.product.service;

import com.example.backend.product.ProductRepository;
import com.example.backend.product.dto.SpecificationRequest;
import com.example.backend.product.dto.SpecificationResponse;
import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.Specification;
import com.example.backend.product.exception.ProductNotFoundException;
import com.example.backend.product.exception.ProductReferenceNotFoundException;
import com.example.backend.product.exception.SpecificationNotFoundException;
import com.example.backend.product.repository.SpecificationRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SpecificationServiceImpl implements SpecificationService {

    private final ProductRepository productRepository;
    private final SpecificationRepository specificationRepository;

    public SpecificationServiceImpl(ProductRepository productRepository, SpecificationRepository specificationRepository) {
        this.productRepository = productRepository;
        this.specificationRepository = specificationRepository;
    }

    @Override
    @Transactional
    public SpecificationResponse createSpecification(UUID productId, SpecificationRequest request) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(
                "Không tìm thấy sản phẩm với ID: " + productId
        ));
        Specification specification = new Specification();
        specification.setProduct(product);
        applyRequest(specification, request);
        return mapToResponse(saveSpecification(specification));
    }

    @Override
    public List<SpecificationResponse> getSpecifications(UUID productId) {
        ensureProductExists(productId);
        return specificationRepository.findByProduct_ProductId(productId, Sort.by(
                Sort.Order.asc("specKey"), Sort.Order.asc("specificationId")
        )).stream().map(this::mapToResponse).toList();
    }

    @Override
    public SpecificationResponse getSpecificationById(UUID productId, UUID specificationId) {
        ensureProductExists(productId);
        return mapToResponse(findSpecification(productId, specificationId));
    }

    @Override
    @Transactional
    public SpecificationResponse updateSpecification(UUID productId, UUID specificationId, SpecificationRequest request) {
        ensureProductExists(productId);
        Specification specification = findSpecification(productId, specificationId);
        applyRequest(specification, request);
        return mapToResponse(saveSpecification(specification));
    }

    @Override
    @Transactional
    public void deleteSpecification(UUID productId, UUID specificationId) {
        ensureProductExists(productId);
        Specification specification = findSpecification(productId, specificationId);
        specificationRepository.delete(specification);
        specificationRepository.flush();
    }

    private void ensureProductExists(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException("Không tìm thấy sản phẩm với ID: " + productId);
        }
    }

    private Specification findSpecification(UUID productId, UUID specificationId) {
        return specificationRepository.findBySpecificationIdAndProduct_ProductId(specificationId, productId)
                .orElseThrow(() -> new SpecificationNotFoundException(
                        "Không tìm thấy thông số với ID " + specificationId + " thuộc sản phẩm: " + productId
                ));
    }

    private void applyRequest(Specification specification, SpecificationRequest request) {
        // Preserve input exactly: the schema does not require trimming, key uniqueness or allowed keys.
        specification.setSpecKey(request.getSpecKey());
        specification.setSpecValue(request.getSpecValue());
    }

    private Specification saveSpecification(Specification specification) {
        try {
            return specificationRepository.saveAndFlush(specification);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "fk_specifications_product".equals(constraint.getConstraintName())) {
                    throw new ProductReferenceNotFoundException(
                            "Sản phẩm được tham chiếu không còn tồn tại", exception
                    );
                }
            }
            throw exception;
        }
    }

    private SpecificationResponse mapToResponse(Specification specification) {
        return new SpecificationResponse(
                specification.getSpecificationId(), specification.getProduct().getProductId(),
                specification.getSpecKey(), specification.getSpecValue()
        );
    }
}
