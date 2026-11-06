package com.example.backend.order.repository;

import com.example.backend.order.entity.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findFirstByOrderIdOrderByShippedDateDesc(UUID orderId);
    List<Shipment> findByOrderIdIn(Collection<UUID> orderIds);
    Page<Shipment> findByEmployeeId(UUID employeeId, Pageable pageable);
    boolean existsByTrackingNumberIgnoreCase(String trackingNumber);
}
