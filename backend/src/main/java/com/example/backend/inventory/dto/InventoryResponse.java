package com.example.backend.inventory.dto;

import com.example.backend.product.entity.ProductTrackingType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryResponse(
        UUID inventoryId,
        UUID warehouseId,
        String warehouseName,
        UUID variantId,
        String sku,
        String productName,
        ProductTrackingType trackingType,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        OffsetDateTime updatedAt
) {}
