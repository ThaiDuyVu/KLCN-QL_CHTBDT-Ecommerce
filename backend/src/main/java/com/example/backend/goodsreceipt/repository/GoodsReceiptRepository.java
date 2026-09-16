package com.example.backend.goodsreceipt.repository;

import com.example.backend.goodsreceipt.entity.GoodsReceipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {

    boolean existsByReceiptCode(String receiptCode);

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
