package com.example.backend.cart.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class CartItemRequest {
    @NotNull(message = "variantId là bắt buộc")
    private UUID variantId;
    @NotNull @Min(value = 1, message = "quantity phải >= 1")
    private Integer quantity;
    private UUID warehouseId;

    public CartItemRequest() {}
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID value) { this.variantId = value; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID value) { this.warehouseId = value; }
}
