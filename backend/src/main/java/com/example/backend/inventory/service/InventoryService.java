package com.example.backend.inventory.service;

import com.example.backend.inventory.dto.InventoryPageResponse;
import com.example.backend.inventory.dto.InventoryResponse;
import java.util.UUID;

public interface InventoryService {
    InventoryPageResponse getInventory(String keyword, UUID warehouseId, UUID variantId, int page, int size);
    InventoryResponse getInventoryById(UUID inventoryId);
}
