package com.example.backend.supplier.dto.response;

import com.example.backend.supplier.entity.SupplierStatus;
import java.util.UUID;

public class SupplierResponse {

    private UUID supplierId;
    private String supplierCode;
    private String supplierName;
    private String phone;
    private String email;
    private String address;
    private SupplierStatus status;

    public SupplierResponse() {
    }

    public SupplierResponse(
            UUID supplierId,
            String supplierCode,
            String supplierName,
            String phone,
            String email,
            String address,
            SupplierStatus status
    ) {
        this.supplierId = supplierId;
        this.supplierCode = supplierCode;
        this.supplierName = supplierName;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.status = status;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public String getSupplierCode() {
        return supplierCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public SupplierStatus getStatus() {
        return status;
    }
}
