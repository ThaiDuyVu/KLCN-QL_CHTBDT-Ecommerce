package com.example.backend.product.repository;

import com.example.backend.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    @EntityGraph(attributePaths = {"category", "category.parent", "brand"})
    @Query("select p from Product p where p.productId = :id")
    Optional<Product> findDetailById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = {"category", "brand"})
    Page<Product> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"category", "brand"})
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"category", "brand"})
    Optional<Product> findById(UUID id);

    boolean existsByProductNameAndCategory_CategoryIdAndBrand_BrandId(
            String productName, UUID categoryId, UUID brandId
    );

    Optional<Product> findFirstByProductNameAndBrand_BrandNameAndCategory_CategoryNameOrderByProductIdAsc(
            String productName, String brandName, String categoryName
    );

    Optional<Product> findFirstByProductNameAndBrand_BrandNameAndCategory_CategoryNameAndCategory_Parent_CategoryNameAndCategory_Parent_ParentIsNullOrderByProductIdAsc(
            String productName, String brandName, String categoryName, String parentCategoryName
    );

    boolean existsByCategory_CategoryId(UUID categoryId); // kiem tra xem có sản phẩm nào thuộc danh mục này hay không, để tránh xóa danh mục đang được sử dụng

    boolean existsByBrand_BrandId(UUID brandId);
}
