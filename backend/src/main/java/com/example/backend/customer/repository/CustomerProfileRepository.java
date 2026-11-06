package com.example.backend.customer.repository;

import com.example.backend.auth.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Write access to customer profiles; read models stay in CustomerManagementRepository. */
public interface CustomerProfileRepository extends JpaRepository<Customer, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c join fetch c.user where c.customerId = :id")
    Optional<Customer> lockById(@Param("id") UUID id);

    /** The phone number identifies a customer at the counter, so two customers may not share it. */
    @Query("select count(c) > 0 from Customer c where c.user.phone = :phone and (:exclude is null or c.customerId <> :exclude)")
    boolean phoneTaken(@Param("phone") String phone, @Param("exclude") UUID excludeCustomerId);
}
