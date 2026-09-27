package com.example.backend.order.repository;

import com.example.backend.order.entity.InstallmentPayment;
import com.example.backend.order.entity.InstallmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface InstallmentPaymentRepository extends JpaRepository<InstallmentPayment, UUID> {
    Optional<InstallmentPayment> findByPaymentId(UUID paymentId);
    Page<InstallmentPayment> findByStatus(InstallmentStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InstallmentPayment i where i.installmentId = :id")
    Optional<InstallmentPayment> lockById(@Param("id") UUID id);
}
