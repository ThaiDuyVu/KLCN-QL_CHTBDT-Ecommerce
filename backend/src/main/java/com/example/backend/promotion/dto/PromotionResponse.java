package com.example.backend.promotion.dto;
import java.util.*;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import com.example.backend.promotion.entity.*;
public class PromotionResponse {
    
    private UUID promotionId;
    
    private String promotionName;
    
    private String description;
    
    private DiscountType discountType;
    
    private BigDecimal discountValue;
    
    private OffsetDateTime startDate;
    
    private OffsetDateTime endDate;
    
    private PromotionStatus status;
    
    private List<PromotionProductResponse> products;
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
    public List<PromotionProductResponse> getProducts() { return products; }
    public void setProducts(List<PromotionProductResponse> value) { this.products = value; }
}
