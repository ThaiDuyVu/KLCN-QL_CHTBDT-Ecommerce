package com.example.backend.supplier.service;

import com.example.backend.supplier.dto.request.CreateSupplierRequest;
import com.example.backend.supplier.dto.request.UpdateSupplierRequest;
import com.example.backend.supplier.dto.response.SupplierPageResponse;
import com.example.backend.supplier.dto.response.SupplierResponse;
import com.example.backend.supplier.entity.Supplier;
import com.example.backend.supplier.exception.SupplierCodeAlreadyExistsException;
import com.example.backend.supplier.exception.SupplierNotFoundException;
import com.example.backend.supplier.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierServiceImpl(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Override
    public SupplierPageResponse getSuppliers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Supplier> supplierPage = supplierRepository.findAll(pageable);

        var content = supplierPage.getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        return new SupplierPageResponse(
                content,
                supplierPage.getNumber(),
                supplierPage.getSize(),
                supplierPage.getTotalElements(),
                supplierPage.getTotalPages()
        );
    }

    @Override
    public SupplierResponse getSupplierById(UUID supplierId) {
        return toResponse(findSupplierById(supplierId));
    }

    @Override
    @Transactional
    public SupplierResponse createSupplier(CreateSupplierRequest request) {
        if (supplierRepository.existsBySupplierCode(request.getSupplierCode())) {
            throw new SupplierCodeAlreadyExistsException(
                    "Mã nhà cung cấp đã tồn tại: " + request.getSupplierCode()
            );
        }

        Supplier supplier = new Supplier();
        supplier.setSupplierCode(request.getSupplierCode());
        supplier.setSupplierName(request.getSupplierName());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());

        return toResponse(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(
            UUID supplierId,
            UpdateSupplierRequest request
    ) {
        Supplier supplier = findSupplierById(supplierId);

        if (supplierRepository.existsBySupplierCodeAndSupplierIdNot(
                request.getSupplierCode(),
                supplierId
        )) {
            throw new SupplierCodeAlreadyExistsException(
                    "Mã nhà cung cấp đã tồn tại: " + request.getSupplierCode()
            );
        }

        supplier.setSupplierCode(request.getSupplierCode());
        supplier.setSupplierName(request.getSupplierName());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());

        return toResponse(supplierRepository.save(supplier));
    }

    private Supplier findSupplierById(UUID supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierNotFoundException(
                        "Không tìm thấy nhà cung cấp với ID: " + supplierId
                ));
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getSupplierId(),
                supplier.getSupplierCode(),
                supplier.getSupplierName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getStatus()
        );
    }
}
