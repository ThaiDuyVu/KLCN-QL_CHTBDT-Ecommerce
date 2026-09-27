package com.example.backend.inventory.controller;

import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.inventory.dto.InventoryPageResponse;
import com.example.backend.inventory.dto.InventoryResponse;
import com.example.backend.inventory.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventoryService;
    public InventoryController(InventoryService inventoryService) { this.inventoryService = inventoryService; }

    @GetMapping
    @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public ResponseEntity<InventoryPageResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inventoryService.getInventory(keyword, warehouseId, variantId, page, size));
    }

    @GetMapping("/{inventoryId}")
    @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public ResponseEntity<InventoryResponse> detail(@PathVariable UUID inventoryId) {
        return ResponseEntity.ok(inventoryService.getInventoryById(inventoryId));
    }
}
