package com.example.backend.cart.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class SelectCartWarehouseRequest {
    @NotNull(message = "warehouseId là bắt buộc")
    private UUID warehouseId;
    private boolean clearItems;

    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }
    public boolean isClearItems() { return clearItems; }
    public void setClearItems(boolean clearItems) { this.clearItems = clearItems; }
}
