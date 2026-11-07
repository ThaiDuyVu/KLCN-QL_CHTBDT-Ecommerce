package com.example.backend.returns.repository;

import com.example.backend.returns.entity.ReturnRequest;
import com.example.backend.returns.entity.ReturnStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.returnId = :id")
    Optional<ReturnRequest> lockById(@Param("id") UUID id);
    Page<ReturnRequest> findByCustomerId(UUID customerId, Pageable pageable);
    Page<ReturnRequest> findByStatus(ReturnStatus status, Pageable pageable);
    @Query("select r from ReturnRequest r where r.orderId in (select o.orderId from Order o where o.warehouseId = :warehouseId)"
            + " and (:status is null or r.status = :status)")
    Page<ReturnRequest> findByBranch(@Param("warehouseId") UUID warehouseId, @Param("status") ReturnStatus status, Pageable pageable);
    List<ReturnRequest> findByOrderIdOrderByRequestDateDesc(UUID orderId);
    boolean existsByReturnCode(String returnCode);
    Optional<ReturnRequest> findByExchangeOrderId(UUID exchangeOrderId);
    /** Value of goods returned from the order and settled (refunded or exchanged). */
    @Query("select coalesce(sum(r.refundAmount), 0) from ReturnRequest r where r.orderId = :orderId and r.status in "
            + "(com.example.backend.returns.entity.ReturnStatus.REFUNDED, com.example.backend.returns.entity.ReturnStatus.EXCHANGED)")
    java.math.BigDecimal refundedAmount(@Param("orderId") UUID orderId);
}
