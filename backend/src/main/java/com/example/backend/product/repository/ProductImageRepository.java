package com.example.backend.product.repository;

import com.example.backend.product.entity.ProductImage;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
    @EntityGraph(attributePaths = "product")
    List<ProductImage> findByProduct_ProductId(UUID productId, Sort sort);

    @EntityGraph(attributePaths = "product")
    Optional<ProductImage> findByImageIdAndProduct_ProductId(UUID imageId, UUID productId);

    Optional<ProductImage> findByProduct_ProductIdAndPrimaryTrue(UUID productId);

    boolean existsByProduct_ProductIdAndImageUrl(UUID productId, String imageUrl);

    boolean existsByProduct_ProductIdAndPrimaryTrue(UUID productId);
}
