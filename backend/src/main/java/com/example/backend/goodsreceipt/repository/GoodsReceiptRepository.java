package com.example.backend.goodsreceipt.repository;

import com.example.backend.goodsreceipt.entity.GoodsReceipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {
    @Override
    @EntityGraph(attributePaths = {"supplier", "warehouse", "employee"})
    Page<GoodsReceipt> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"supplier", "warehouse", "employee", "items", "items.variant", "items.variant.product"})
    Optional<GoodsReceipt> findById(UUID receiptId);

    Optional<GoodsReceipt> findByReceiptCode(String receiptCode);

    boolean existsByReceiptCode(String receiptCode);
    boolean existsByReceiptCodeAndReceiptIdNot(String receiptCode, UUID receiptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select receipt
            from GoodsReceipt receipt
            where receipt.receiptId = :receiptId
            """)
    Optional<GoodsReceipt> findByReceiptIdForUpdate(
            @Param("receiptId") UUID receiptId
    );
}
