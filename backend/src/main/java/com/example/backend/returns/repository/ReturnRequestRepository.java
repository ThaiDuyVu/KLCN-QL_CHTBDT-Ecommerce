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
    List<ReturnRequest> findByOrderIdOrderByRequestDateDesc(UUID orderId);
    boolean existsByReturnCode(String returnCode);
    @Query("select coalesce(sum(r.refundAmount), 0) from ReturnRequest r where r.orderId = :orderId and r.status = com.example.backend.returns.entity.ReturnStatus.REFUNDED")
    java.math.BigDecimal refundedAmount(@Param("orderId") UUID orderId);
}
