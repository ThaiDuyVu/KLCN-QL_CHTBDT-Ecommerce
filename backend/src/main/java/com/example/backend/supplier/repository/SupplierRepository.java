package com.example.backend.supplier.repository;

import com.example.backend.supplier.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    boolean existsBySupplierCode(String supplierCode);

    boolean existsBySupplierCodeAndSupplierIdNot(
            String supplierCode,
            UUID supplierId
    );
}
