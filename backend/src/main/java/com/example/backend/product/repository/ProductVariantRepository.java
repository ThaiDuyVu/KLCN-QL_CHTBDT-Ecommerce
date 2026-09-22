package com.example.backend.product.repository;

import com.example.backend.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;

import java.util.UUID;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    boolean existsBySku(String sku);

    @EntityGraph(attributePaths = "product")
    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySkuAndVariantIdNot(String sku, UUID variantId);

    @Override
    @EntityGraph(attributePaths = "product")
    Optional<ProductVariant> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = "product")
    Page<ProductVariant> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "product")
    Page<ProductVariant> findByProduct_ProductId(UUID productId, Pageable pageable);

    @EntityGraph(attributePaths = "product")
    List<ProductVariant> findByProduct_ProductId(UUID productId, Sort sort);
}
