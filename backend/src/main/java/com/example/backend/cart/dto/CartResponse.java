package com.example.backend.cart.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class CartResponse {
    private UUID cartId;
    private UUID warehouseId;
    private String warehouseName;
    private List<CartItemResponse> items;
    private BigDecimal subtotal;
    public CartResponse() {}
    public UUID getCartId() { return cartId; }
    public void setCartId(UUID value) { this.cartId = value; }
    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID value) { this.warehouseId = value; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String value) { this.warehouseName = value; }
    public List<CartItemResponse> getItems() { return items; }
    public void setItems(List<CartItemResponse> value) { this.items = value; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal value) { this.subtotal = value; }
}
