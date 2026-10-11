package com.example.backend.product.repository;

import com.example.backend.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;

import java.util.UUID;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID>, JpaSpecificationExecutor<ProductVariant> {
    @Override
    @EntityGraph(attributePaths = {"product", "product.category", "product.brand"})
    Page<ProductVariant> findAll(Specification<ProductVariant> spec, Pageable pageable);
    @EntityGraph(attributePaths = {"product", "product.category", "product.brand"})
    List<ProductVariant> findByVariantIdIn(java.util.Collection<UUID> ids);
    @EntityGraph(attributePaths = {"product", "product.category", "product.brand"})
    List<ProductVariant> findByProduct_ProductIdInAndStatus(java.util.Collection<UUID> productIds, com.example.backend.product.entity.ProductVariantStatus status);
    boolean existsBySku(String sku);

    @EntityGraph(attributePaths = {"product", "product.category", "product.brand"})
    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySkuAndVariantIdNot(String sku, UUID variantId);

    @Override
    @EntityGraph(attributePaths = {"product", "product.category", "product.brand"})
    Optional<ProductVariant> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"product", "product.category", "product.brand"})
    Page<ProductVariant> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"product", "product.category", "product.brand"})
    Page<ProductVariant> findByProduct_ProductId(UUID productId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "product.category", "product.brand"})
    List<ProductVariant> findByProduct_ProductId(UUID productId, Sort sort);
}
