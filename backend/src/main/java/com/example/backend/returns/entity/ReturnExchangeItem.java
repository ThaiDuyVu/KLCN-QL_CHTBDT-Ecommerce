package com.example.backend.returns.entity;

import jakarta.persistence.*;
import java.util.UUID;

/** A product the customer wants instead of the returned goods (V14). */
@Entity
@Table(name = "return_exchange_items")
public class ReturnExchangeItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "exchange_item_id")
    private UUID exchangeItemId;
    @Column(name = "return_id") private UUID returnId;
    @Column(name = "variant_id") private UUID variantId;
    @Column(name = "quantity") private Integer quantity;

    public UUID getExchangeItemId() { return exchangeItemId; }
    public UUID getReturnId() { return returnId; }
    public void setReturnId(UUID value) { this.returnId = value; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID value) { this.variantId = value; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
}
