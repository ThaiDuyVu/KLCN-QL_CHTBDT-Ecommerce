package com.example.backend.customer.dto;

import java.util.UUID;
import java.time.LocalDate;
import com.example.backend.warranty.entity.WarrantyStatus;
import java.util.List;

public class CustomerWarrantyResponse {
    private final UUID warrantyId;
    private final UUID serialId;
    private final String serialNumber;
    private final String sku;
    private final String productName;
    private final UUID orderId;
    private final String orderCode;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private WarrantyStatus status;
    private List<String> imeiNumbers;

    public CustomerWarrantyResponse(UUID warrantyId, UUID serialId, String serialNumber, String sku, String productName, UUID orderId, String orderCode, LocalDate startDate, LocalDate endDate, WarrantyStatus status, List<String> imeiNumbers) {
        this.warrantyId = warrantyId;
        this.serialId = serialId;
        this.serialNumber = serialNumber;
        this.sku = sku;
        this.productName = productName;
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.imeiNumbers = imeiNumbers;
    }

    public UUID getWarrantyId() { return warrantyId; }
    public UUID getSerialId() { return serialId; }
    public String getSerialNumber() { return serialNumber; }
    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public UUID getOrderId() { return orderId; }
    public String getOrderCode() { return orderCode; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public WarrantyStatus getStatus() { return status; }
    public void setStatus(WarrantyStatus status) { this.status = status; }
    public List<String> getImeiNumbers() { return imeiNumbers; }
    public void setImeiNumbers(List<String> imeiNumbers) { this.imeiNumbers = imeiNumbers; }
}
