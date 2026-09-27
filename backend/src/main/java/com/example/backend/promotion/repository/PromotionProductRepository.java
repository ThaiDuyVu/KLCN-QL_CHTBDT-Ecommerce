package com.example.backend.promotion.repository;
import com.example.backend.promotion.entity.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.OffsetDateTime;
public interface PromotionProductRepository extends JpaRepository<PromotionProduct,PromotionProductId> {
    @EntityGraph(attributePaths={"product"})
    List<PromotionProduct> findById_PromotionIdIn(Collection<UUID> promotionIds);
    void deleteById_PromotionId(UUID promotionId);
    @EntityGraph(attributePaths={"promotion", "product"})
    @Query("select pp from PromotionProduct pp where pp.id.productId in :productIds and pp.promotion.status = :status and pp.promotion.startDate <= :now and pp.promotion.endDate >= :now")
    List<PromotionProduct> findApplicable(Collection<UUID> productIds, PromotionStatus status, OffsetDateTime now);
    @EntityGraph(attributePaths={"promotion", "product"})
    @Query("select pp from PromotionProduct pp join fetch pp.promotion p where pp.id.productId in :productIds and p.status = :status and p.startDate <= :endDate and p.endDate >= :startDate")
    List<PromotionProduct> findOverlapping(Collection<UUID> productIds, PromotionStatus status, OffsetDateTime startDate, OffsetDateTime endDate);
}
