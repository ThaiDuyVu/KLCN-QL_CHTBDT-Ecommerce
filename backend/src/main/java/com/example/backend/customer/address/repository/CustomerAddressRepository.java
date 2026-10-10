package com.example.backend.customer.address.repository;

import com.example.backend.customer.address.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
    List<CustomerAddress> findByCustomerIdOrderByCreatedAtAscAddressIdAsc(UUID customerId);
    Optional<CustomerAddress> findByAddressIdAndCustomerId(UUID addressId, UUID customerId);
}
