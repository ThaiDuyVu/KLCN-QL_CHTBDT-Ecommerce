package com.example.backend.cart.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class CartItemResponse {
    private UUID cartItemId;
    private UUID variantId;
    private String productName;
    private String sku;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private Integer availableQuantity;
    public CartItemResponse() {}
    public UUID getCartItemId() { return cartItemId; }
    public void setCartItemId(UUID value) { this.cartItemId = value; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID value) { this.variantId = value; }
    public String getProductName() { return productName; }
    public void setProductName(String value) { this.productName = value; }
    public String getSku() { return sku; }
    public void setSku(String value) { this.sku = value; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { this.unitPrice = value; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal value) { this.lineTotal = value; }
    public Integer getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Integer value) { this.availableQuantity = value; }
}
