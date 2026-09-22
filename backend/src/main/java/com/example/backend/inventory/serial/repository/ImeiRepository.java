package com.example.backend.inventory.serial.repository;

import com.example.backend.inventory.serial.entity.Imei;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ImeiRepository extends JpaRepository<Imei, UUID> {
    boolean existsByImeiNumber(String imeiNumber);
    Optional<Imei> findByImeiNumber(String imeiNumber);
}
