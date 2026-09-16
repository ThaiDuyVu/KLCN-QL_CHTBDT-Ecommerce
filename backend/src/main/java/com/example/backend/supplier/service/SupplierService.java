package com.example.backend.supplier.service;

import com.example.backend.supplier.dto.request.CreateSupplierRequest;
import com.example.backend.supplier.dto.request.UpdateSupplierRequest;
import com.example.backend.supplier.dto.response.SupplierPageResponse;
import com.example.backend.supplier.dto.response.SupplierResponse;

import java.util.UUID;

public interface SupplierService {

    SupplierPageResponse getSuppliers(int page, int size);

    SupplierResponse getSupplierById(UUID supplierId);

    SupplierResponse createSupplier(CreateSupplierRequest request);

    SupplierResponse updateSupplier(
            UUID supplierId,
            UpdateSupplierRequest request
    );
}
