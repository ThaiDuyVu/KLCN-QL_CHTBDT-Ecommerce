package com.example.backend.order.repository;
import com.example.backend.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderIdOrderByVariantIdAscOrderItemIdAsc(UUID orderId);
}
