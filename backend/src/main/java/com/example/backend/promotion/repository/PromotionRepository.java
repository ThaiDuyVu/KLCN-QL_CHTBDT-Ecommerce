package com.example.backend.promotion.repository;
import com.example.backend.promotion.entity.Promotion;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PromotionRepository extends JpaRepository<Promotion,UUID>, JpaSpecificationExecutor<Promotion> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Promotion p where p.promotionId = :id")
    Optional<Promotion> lockById(UUID id);
}
