package com.example.backend.promotion.dto;
import java.util.*;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import com.example.backend.promotion.entity.*;
public class PromotionSummaryResponse {
    
    private UUID promotionId;
    
    private String promotionName;
    
    private DiscountType discountType;
    
    private BigDecimal discountValue;
    
    private OffsetDateTime startDate;
    
    private OffsetDateTime endDate;
    public UUID getPromotionId() { return promotionId; }
    public void setPromotionId(UUID value) { this.promotionId = value; }
    public String getPromotionName() { return promotionName; }
    public void setPromotionName(String value) { this.promotionName = value; }
    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType value) { this.discountType = value; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal value) { this.discountValue = value; }
    public OffsetDateTime getStartDate() { return startDate; }
    public void setStartDate(OffsetDateTime value) { this.startDate = value; }
    public OffsetDateTime getEndDate() { return endDate; }
    public void setEndDate(OffsetDateTime value) { this.endDate = value; }
}
