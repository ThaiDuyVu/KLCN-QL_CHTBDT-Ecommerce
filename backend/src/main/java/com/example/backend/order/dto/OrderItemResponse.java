package com.example.backend.order.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class OrderItemResponse {
    private UUID orderItemId;
    private UUID variantId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal finalUnitPrice;
    private String sku;
    private String productName;
    private UUID serialId;
    private String serialNumber;
    private List<String> imeiNumbers;
    private com.example.backend.product.entity.ProductTrackingType trackingType;
    public OrderItemResponse() {}
    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID value) { this.orderItemId = value; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID value) { this.variantId = value; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { this.unitPrice = value; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal value) { this.discountAmount = value; }
    public BigDecimal getFinalUnitPrice() { return finalUnitPrice; }
    public void setFinalUnitPrice(BigDecimal value) { this.finalUnitPrice = value; }
    public String getSku() { return sku; }
    public void setSku(String value) { this.sku = value; }
    public String getProductName() { return productName; }
    public void setProductName(String value) { this.productName = value; }
    public UUID getSerialId() { return serialId; }
    public void setSerialId(UUID value) { this.serialId = value; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String value) { this.serialNumber = value; }
    public List<String> getImeiNumbers() { return imeiNumbers; }
    public void setImeiNumbers(List<String> value) { this.imeiNumbers = value; }
    public com.example.backend.product.entity.ProductTrackingType getTrackingType() { return trackingType; }
    public void setTrackingType(com.example.backend.product.entity.ProductTrackingType value) { this.trackingType = value; }
}
