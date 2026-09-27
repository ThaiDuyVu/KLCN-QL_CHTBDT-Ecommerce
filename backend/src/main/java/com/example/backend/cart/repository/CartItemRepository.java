package com.example.backend.cart.repository;
import com.example.backend.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findByCartIdOrderByVariantIdAsc(UUID cartId);
    Optional<CartItem> findByCartIdAndVariantId(UUID cartId, UUID variantId);
    long countByCartId(UUID cartId);
    void deleteByCartId(UUID cartId);
}
