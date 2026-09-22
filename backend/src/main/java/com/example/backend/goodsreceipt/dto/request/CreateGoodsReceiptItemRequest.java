package com.example.backend.goodsreceipt.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class CreateGoodsReceiptItemRequest {

    @NotNull(message = "Variant ID is required")
    private UUID variantId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than 0")
    private Integer quantity;

    @NotNull(message = "Unit cost is required")
    @DecimalMin(value = "0.00", message = "Unit cost must not be negative")
    @Digits(integer = 13, fraction = 2, message = "Unit cost must fit NUMERIC(15,2)")
    private BigDecimal unitCost;

    private List<@Valid ReceiptDeviceRequest> devices = new ArrayList<>();

    public CreateGoodsReceiptItemRequest() {
    }

    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public List<ReceiptDeviceRequest> getDevices() { return devices; }
    public void setDevices(List<ReceiptDeviceRequest> devices) { this.devices = devices; }
}
