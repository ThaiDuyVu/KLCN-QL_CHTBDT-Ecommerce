package com.example.backend.category;

import com.example.backend.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    boolean existsByParent_CategoryId(UUID parentId);

    Optional<Category> findFirstByCategoryNameAndParentIsNullOrderByCategoryIdAsc(String categoryName);

    Optional<Category> findFirstByCategoryNameAndParent_CategoryIdOrderByCategoryIdAsc(
            String categoryName, UUID parentId
    );

    Optional<Category> findFirstByCategoryNameAndParent_CategoryNameAndParent_ParentIsNullOrderByCategoryIdAsc(
            String categoryName, String parentName
    );

    Optional<Category> findFirstByCategoryNameAndParentIsNotNullOrderByCategoryIdAsc(String categoryName);
}
