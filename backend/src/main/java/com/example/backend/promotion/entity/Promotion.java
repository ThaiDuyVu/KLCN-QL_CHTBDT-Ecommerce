package com.example.backend.promotion.entity;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
@Entity @Table(name="promotions")
public class Promotion {
    @Id @GeneratedValue(strategy=GenerationType.UUID) @Column(name="promotion_id", nullable=false, updatable=false)
    private UUID promotionId;
    @Column(name="promotion_name", nullable=false, length=255)
    private String promotionName;
    @Column(columnDefinition="TEXT")
    private String description;
    @Enumerated(EnumType.STRING) @Column(name="discount_type", nullable=false, length=30)
    private DiscountType discountType;
    @Column(name="discount_value", nullable=false, precision=15, scale=2)
    private BigDecimal discountValue;
    @Column(name="start_date", nullable=false)
    private OffsetDateTime startDate;
    @Column(name="end_date", nullable=false)
    private OffsetDateTime endDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30)
    private PromotionStatus status;
    public UUID getPromotionId() { return promotionId; }
    public void setPromotionId(UUID value) { this.promotionId = value; }
    public String getPromotionName() { return promotionName; }
    public void setPromotionName(String value) { this.promotionName = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType value) { this.discountType = value; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal value) { this.discountValue = value; }
    public OffsetDateTime getStartDate() { return startDate; }
    public void setStartDate(OffsetDateTime value) { this.startDate = value; }
    public OffsetDateTime getEndDate() { return endDate; }
    public void setEndDate(OffsetDateTime value) { this.endDate = value; }
    public PromotionStatus getStatus() { return status; }
    public void setStatus(PromotionStatus value) { this.status = value; }
}
