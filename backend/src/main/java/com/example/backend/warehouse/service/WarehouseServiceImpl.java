package com.example.backend.warehouse.service;

import com.example.backend.warehouse.dto.request.CreateWarehouseRequest;
import com.example.backend.warehouse.dto.request.UpdateWarehouseRequest;
import com.example.backend.warehouse.dto.response.WarehousePageResponse;
import com.example.backend.warehouse.dto.response.WarehouseResponse;
import com.example.backend.warehouse.entity.Warehouse;
import com.example.backend.warehouse.exception.WarehouseNotFoundException;
import com.example.backend.warehouse.repository.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseServiceImpl(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Override
    public WarehousePageResponse getWarehouses(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Warehouse> warehousePage = warehouseRepository.findAll(pageable);

        var content = warehousePage.getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        return new WarehousePageResponse(
                content,
                warehousePage.getNumber(),
                warehousePage.getSize(),
                warehousePage.getTotalElements(),
                warehousePage.getTotalPages()
        );
    }

    @Override
    public WarehouseResponse getWarehouseById(UUID warehouseId) {
        return toResponse(findWarehouseById(warehouseId));
    }

    @Override
    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest request) {
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseName(request.getWarehouseName());
        warehouse.setAddress(request.getAddress());

        return toResponse(warehouseRepository.save(warehouse));
    }

    @Override
    @Transactional
    public WarehouseResponse updateWarehouse(
            UUID warehouseId,
            UpdateWarehouseRequest request
    ) {
        Warehouse warehouse = findWarehouseById(warehouseId);
        warehouse.setWarehouseName(request.getWarehouseName());
        warehouse.setAddress(request.getAddress());

        return toResponse(warehouseRepository.save(warehouse));
    }

    private Warehouse findWarehouseById(UUID warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new WarehouseNotFoundException(
                        "Không tìm thấy kho với ID: " + warehouseId
                ));
    }

    private WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getWarehouseId(),
                warehouse.getWarehouseName(),
                warehouse.getAddress(),
                warehouse.getStatus()
        );
    }
}
