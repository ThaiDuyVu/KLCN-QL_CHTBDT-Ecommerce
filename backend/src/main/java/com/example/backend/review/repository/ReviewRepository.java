package com.example.backend.review.repository;

import com.example.backend.review.entity.Review;
import com.example.backend.review.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Page<Review> findByProductIdAndStatus(UUID productId, ReviewStatus status, Pageable pageable);
    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);
    List<Review> findByCustomerIdAndOrderItemIdIn(UUID customerId, Collection<UUID> orderItemIds);
    boolean existsByCustomerIdAndOrderItemId(UUID customerId, UUID orderItemId);
    /** [average, count] of visible reviews. */
    @Query("select avg(r.rating), count(r) from Review r where r.productId = :productId and r.status = com.example.backend.review.entity.ReviewStatus.VISIBLE")
    List<Object[]> stats(@Param("productId") UUID productId);
}
