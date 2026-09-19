package com.example.backend.cart.repository;
import com.example.backend.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findFirstByCustomerIdAndStatusOrderByCreatedAtAscCartIdAsc(UUID customerId, String status);
}
