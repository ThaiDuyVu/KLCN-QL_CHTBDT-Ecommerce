package com.example.backend.warehouse.dto.response;

import com.example.backend.warehouse.entity.WarehouseStatus;
import java.util.UUID;

public class WarehouseResponse {

    private UUID warehouseId;
    private String warehouseName;
    private String address;
    private WarehouseStatus status;

    public WarehouseResponse() {
    }

    public WarehouseResponse(
            UUID warehouseId,
            String warehouseName,
            String address,
            WarehouseStatus status
    ) {
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
        this.address = address;
        this.status = status;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public String getAddress() {
        return address;
    }

    public WarehouseStatus getStatus() {
        return status;
    }
}
