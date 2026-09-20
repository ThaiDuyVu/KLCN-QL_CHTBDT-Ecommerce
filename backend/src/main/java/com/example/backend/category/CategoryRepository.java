package com.example.backend.category;

import com.example.backend.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByParentIsNull(); // truy van lay danh muc goc khong parent 
    List<Category> findByParent_CategoryId(UUID parentId); // truy van lay danh muc con theo id của parent
    //block/delete category if it has child categories
    boolean existsByParent_CategoryId(UUID parentId); // kiem tra rang buoc xem danh muc theo id cua parent de biet no co dang lam cha cua danh muc khac hay khong    
    
}