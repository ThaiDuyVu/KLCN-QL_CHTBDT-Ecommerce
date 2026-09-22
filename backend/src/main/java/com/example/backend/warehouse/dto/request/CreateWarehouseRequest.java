package com.example.backend.warehouse.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateWarehouseRequest {

    @NotNull(message = "Warehouse name is required")
    @Size(max = 255, message = "Warehouse name must not exceed 255 characters")
    private String warehouseName;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    public CreateWarehouseRequest() {
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
