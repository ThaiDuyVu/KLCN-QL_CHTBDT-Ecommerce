package com.example.backend.cart.entity;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;
import jakarta.persistence.*;

@Entity
@Table(name = "carts")
public class Cart {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "cart_id")
    private UUID cartId;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    @Column(name = "status")
    private String status;
    public Cart() {}
    public UUID getCartId() { return cartId; }
    public void setCartId(UUID value) { this.cartId = value; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID value) { this.customerId = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { this.updatedAt = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
}
