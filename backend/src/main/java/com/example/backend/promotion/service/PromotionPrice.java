package com.example.backend.promotion.service;
import com.example.backend.promotion.dto.PromotionSummaryResponse;
import java.math.BigDecimal;
public class PromotionPrice {
    private final BigDecimal unitPrice, discountAmount, finalUnitPrice;
    private final PromotionSummaryResponse promotion;
    public PromotionPrice(BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal finalUnitPrice, PromotionSummaryResponse promotion) {
        this.unitPrice=unitPrice; this.discountAmount=discountAmount; this.finalUnitPrice=finalUnitPrice; this.promotion=promotion;
    }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getFinalUnitPrice() { return finalUnitPrice; }
    public PromotionSummaryResponse getPromotion() { return promotion; }
}
