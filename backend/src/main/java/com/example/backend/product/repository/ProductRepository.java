package com.example.backend.product.repository;

import com.example.backend.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    boolean existsByCategory_CategoryId(UUID categoryId); // kiểm tra xem có sản phẩm nào thuộc danh mục này không
}