package com.example.backend.inventory.serial.dto;

import com.example.backend.inventory.serial.entity.SerialStatus;
import com.example.backend.product.entity.ProductTrackingType;
import java.util.List;
import java.util.UUID;

public record SerialResponse(UUID serialId, String serialNumber, SerialStatus status,
        UUID variantId, String sku, String productName, ProductTrackingType trackingType,
        UUID warehouseId, String warehouseName, List<SerialImeiResponse> imeis) {}
