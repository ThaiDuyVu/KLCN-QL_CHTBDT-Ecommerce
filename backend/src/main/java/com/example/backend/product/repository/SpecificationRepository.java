package com.example.backend.product.repository;

import com.example.backend.product.entity.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface SpecificationRepository extends JpaRepository<Specification, UUID> {
    @EntityGraph(attributePaths = "product")
    List<Specification> findByProduct_ProductId(UUID productId, Sort sort);

    @EntityGraph(attributePaths = "product")
    Optional<Specification> findBySpecificationIdAndProduct_ProductId(UUID specificationId, UUID productId);

    // Exact tuple lookup is used only to make dev seeding reusable; it is not a uniqueness rule.
    boolean existsByProduct_ProductIdAndSpecKeyAndSpecValue(UUID productId, String specKey, String specValue);

    // Seed-only lookup: leave an existing customized value untouched; CRUD still allows duplicate keys.
    boolean existsByProduct_ProductIdAndSpecKey(UUID productId, String specKey);
}
