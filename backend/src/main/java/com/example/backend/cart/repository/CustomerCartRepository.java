package com.example.backend.cart.repository;
import com.example.backend.auth.entity.Customer;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface CustomerCartRepository extends JpaRepository<Customer, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.user.userId = :userId")
    Optional<Customer> lockByUserId(@Param("userId") UUID userId);
    Optional<Customer> findByUser_UserId(UUID userId);
}
