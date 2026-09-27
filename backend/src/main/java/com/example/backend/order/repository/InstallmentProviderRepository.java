package com.example.backend.order.repository;

import com.example.backend.order.entity.InstallmentProvider;
import com.example.backend.order.entity.InstallmentProviderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallmentProviderRepository extends JpaRepository<InstallmentProvider, UUID> {
    List<InstallmentProvider> findByStatusOrderByProviderNameAsc(InstallmentProviderStatus status);
    List<InstallmentProvider> findAllByOrderByProviderNameAsc();
    Optional<InstallmentProvider> findByProviderCodeIgnoreCase(String providerCode);
}
