package com.example.backend.promotion.entity;
import com.example.backend.product.entity.Product;
import jakarta.persistence.*;
@Entity @Table(name="promotion_products")
public class PromotionProduct {
    @EmbeddedId private PromotionProductId id;
    @MapsId("promotionId") @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="promotion_id", nullable=false)
    private Promotion promotion;
    @MapsId("productId") @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="product_id", nullable=false)
    private Product product;
    public PromotionProduct() {}
    public PromotionProduct(Promotion promotion, Product product) {
        this.promotion=promotion; this.product=product; this.id=new PromotionProductId(promotion.getPromotionId(), product.getProductId());
    }
    public PromotionProductId getId() { return id; }
    public Promotion getPromotion() { return promotion; }
    public Product getProduct() { return product; }
}
