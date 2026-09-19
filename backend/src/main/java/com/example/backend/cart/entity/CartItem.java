package com.example.backend.cart.entity;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;
import jakarta.persistence.*;

@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "cart_item_id")
    private UUID cartItemId;
    @Column(name = "cart_id")
    private UUID cartId;
    @Column(name = "variant_id")
    private UUID variantId;
    @Column(name = "quantity")
    private Integer quantity;
    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;
    public CartItem() {}
    public UUID getCartItemId() { return cartItemId; }
    public void setCartItemId(UUID value) { this.cartItemId = value; }
    public UUID getCartId() { return cartId; }
    public void setCartId(UUID value) { this.cartId = value; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID value) { this.variantId = value; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { this.unitPrice = value; }
}
