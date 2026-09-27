package com.example.backend.promotion.dto;
import java.util.UUID;
public class PromotionProductResponse {
    
    private UUID productId;
    
    private String productName;
    public UUID getProductId() { return productId; }
    public void setProductId(UUID value) { this.productId = value; }
    public String getProductName() { return productName; }
    public void setProductName(String value) { this.productName = value; }
}
