package com.example.backend.warehouse.controller;

import com.example.backend.warehouse.dto.request.CreateWarehouseRequest;
import com.example.backend.warehouse.dto.request.UpdateWarehouseRequest;
import com.example.backend.warehouse.dto.response.WarehousePageResponse;
import com.example.backend.warehouse.dto.response.WarehouseResponse;
import com.example.backend.warehouse.service.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    public ResponseEntity<WarehousePageResponse> getWarehouses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("Page không được nhỏ hơn 0");
        }

        if (size <= 0) {
            throw new IllegalArgumentException("Size phải lớn hơn 0");
        }

        return ResponseEntity.ok(warehouseService.getWarehouses(page, size));
    }

    @GetMapping("/{warehouseId}")
    public ResponseEntity<WarehouseResponse> getWarehouseById(
            @PathVariable UUID warehouseId
    ) {
        return ResponseEntity.ok(warehouseService.getWarehouseById(warehouseId));
    }

    @PostMapping
    public ResponseEntity<WarehouseResponse> createWarehouse(
            @Valid @RequestBody CreateWarehouseRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(warehouseService.createWarehouse(request));
    }

    @PutMapping("/{warehouseId}")
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @PathVariable UUID warehouseId,
            @Valid @RequestBody UpdateWarehouseRequest request
    ) {
        return ResponseEntity.ok(warehouseService.updateWarehouse(warehouseId, request));
    }
}
