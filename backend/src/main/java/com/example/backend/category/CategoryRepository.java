package com.example.backend.category;

import com.example.backend.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByParentIsNull(); // truy van lay danh muc goc khong parent

    List<Category> findByParent_CategoryId(UUID parentId); // truy van lay danh muc con theo id của parent

    boolean existsByParent_CategoryId(UUID parentId); // kiem tra rang buoc xem danh muc theo id cua parent de biet no co dang lam cha cua danh muc khac hay khong

    Optional<Category> findFirstByCategoryNameAndParentIsNullOrderByCategoryIdAsc(String categoryName);

    Optional<Category> findFirstByCategoryNameAndParent_CategoryIdOrderByCategoryIdAsc(
            String categoryName, UUID parentId
    );

    Optional<Category> findFirstByCategoryNameAndParent_CategoryNameAndParent_ParentIsNullOrderByCategoryIdAsc(
            String categoryName, String parentName
    );

    Optional<Category> findFirstByCategoryNameAndParentIsNotNullOrderByCategoryIdAsc(String categoryName);
}
