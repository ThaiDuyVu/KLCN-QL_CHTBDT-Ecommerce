package com.example.backend.product;

import com.example.backend.product.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
}