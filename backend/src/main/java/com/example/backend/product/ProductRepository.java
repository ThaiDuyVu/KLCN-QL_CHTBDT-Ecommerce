package com.example.backend.product;

import com.example.backend.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    
}