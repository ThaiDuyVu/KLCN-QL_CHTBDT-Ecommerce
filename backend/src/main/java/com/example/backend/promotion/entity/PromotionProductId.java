package com.example.backend.promotion.entity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.*;
@Embeddable
public class PromotionProductId implements Serializable {
    @Column(name="promotion_id") private UUID promotionId;
    @Column(name="product_id") private UUID productId;
    public PromotionProductId() {}
    public PromotionProductId(UUID promotionId, UUID productId) { this.promotionId=promotionId; this.productId=productId; }
    public UUID getPromotionId() { return promotionId; }
    public UUID getProductId() { return productId; }
    @Override public boolean equals(Object value) { return value instanceof PromotionProductId other && Objects.equals(promotionId,other.promotionId) && Objects.equals(productId,other.productId); }
    @Override public int hashCode() { return Objects.hash(promotionId,productId); }
}
