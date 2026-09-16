package com.example.backend.goodsreceipt.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CreateGoodsReceiptRequest {

    @NotNull(message = "Receipt code is required")
    @Size(max = 50, message = "Receipt code must not exceed 50 characters")
    private String receiptCode;

    @NotNull(message = "Supplier ID is required")
    private UUID supplierId;

    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;

    @NotNull(message = "Employee ID is required")
    private UUID employeeId;

    private List<@Valid CreateGoodsReceiptItemRequest> items = new ArrayList<>();

    public CreateGoodsReceiptRequest() {
    }

    public String getReceiptCode() { return receiptCode; }
    public void setReceiptCode(String receiptCode) { this.receiptCode = receiptCode; }
    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }
    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public List<CreateGoodsReceiptItemRequest> getItems() { return items; }
    public void setItems(List<CreateGoodsReceiptItemRequest> items) { this.items = items; }
}
