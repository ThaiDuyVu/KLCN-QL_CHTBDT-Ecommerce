package com.example.backend.warranty.repository;

import com.example.backend.warranty.entity.Warranty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface WarrantyRepository extends JpaRepository<Warranty, UUID> {
    boolean existsBySerialId(UUID serialId);
    Optional<Warranty> findBySerialId(UUID serialId);
    Optional<Warranty> findByWarrantyIdAndCustomerId(UUID warrantyId, UUID customerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Warranty w where w.warrantyId = :warrantyId and w.customerId = :customerId")
    Optional<Warranty> lockByWarrantyIdAndCustomerId(@Param("warrantyId") UUID warrantyId,
            @Param("customerId") UUID customerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Warranty w where w.warrantyId = :warrantyId")
    Optional<Warranty> lockById(@Param("warrantyId") UUID warrantyId);
    Page<Warranty> findByCustomerId(UUID customerId, Pageable pageable);
    List<Warranty> findByWarrantyIdIn(Collection<UUID> warrantyIds);
}
