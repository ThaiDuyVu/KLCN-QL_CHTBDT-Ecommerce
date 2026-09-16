package com.example.backend.warehouse.service;

import com.example.backend.warehouse.dto.request.CreateWarehouseRequest;
import com.example.backend.warehouse.dto.request.UpdateWarehouseRequest;
import com.example.backend.warehouse.dto.response.WarehousePageResponse;
import com.example.backend.warehouse.dto.response.WarehouseResponse;

import java.util.UUID;

public interface WarehouseService {

    WarehousePageResponse getWarehouses(int page, int size);

    WarehouseResponse getWarehouseById(UUID warehouseId);

    WarehouseResponse createWarehouse(CreateWarehouseRequest request);

    WarehouseResponse updateWarehouse(
            UUID warehouseId,
            UpdateWarehouseRequest request
    );
}
