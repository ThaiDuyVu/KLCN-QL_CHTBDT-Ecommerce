package com.example.backend.product;

import com.example.backend.product.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    Optional<Brand> findByBrandName(String brandName);

    boolean existsByBrandName(String brandName);

    boolean existsByBrandNameAndBrandIdNot(String brandName, UUID brandId);
}
