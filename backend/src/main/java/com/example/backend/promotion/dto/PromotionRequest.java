package com.example.backend.promotion.dto;
import java.util.*;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import com.example.backend.promotion.entity.*;
import jakarta.validation.constraints.*;
public class PromotionRequest {
    @NotBlank(message="Tên khuyến mãi là bắt buộc") @Size(max=255, message="Tên khuyến mãi tối đa 255 ký tự")
    private String promotionName;
    
    private String description;
    @NotNull(message="discountType là bắt buộc")
    private DiscountType discountType;
    @NotNull(message="discountValue là bắt buộc") @DecimalMin(value="0", message="discountValue phải >= 0") @Digits(integer=13, fraction=2, message="discountValue tối đa 13 chữ số nguyên và 2 số thập phân")
    private BigDecimal discountValue;
    @NotNull(message="startDate là bắt buộc")
    private OffsetDateTime startDate;
    @NotNull(message="endDate là bắt buộc")
    private OffsetDateTime endDate;
    @NotNull(message="status là bắt buộc")
    private PromotionStatus status;
    @NotNull(message="productIds là bắt buộc")
    private List<UUID> productIds;
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
    public List<UUID> getProductIds() { return productIds; }
    public void setProductIds(List<UUID> value) { this.productIds = value; }
}
