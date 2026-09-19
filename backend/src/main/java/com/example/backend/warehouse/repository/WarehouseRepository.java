package com.example.backend.warehouse.repository;

import com.example.backend.warehouse.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    Optional<Warehouse> findFirstByWarehouseNameOrderByWarehouseIdAsc(String warehouseName);
}
