package com.example.backend.warranty.repository;

import com.example.backend.warranty.entity.Warranty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WarrantyRepository extends JpaRepository<Warranty, UUID> {
    boolean existsBySerialId(UUID serialId);
    Optional<Warranty> findBySerialId(UUID serialId);
    Optional<Warranty> findByWarrantyIdAndCustomerId(UUID warrantyId, UUID customerId);
    Page<Warranty> findByCustomerId(UUID customerId, Pageable pageable);
    List<Warranty> findByWarrantyIdIn(Collection<UUID> warrantyIds);
}
