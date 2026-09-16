package com.example.backend.supplier.service;

import com.example.backend.supplier.dto.request.CreateSupplierRequest;
import com.example.backend.supplier.dto.request.UpdateSupplierRequest;
import com.example.backend.supplier.dto.response.SupplierPageResponse;
import com.example.backend.supplier.dto.response.SupplierResponse;
import com.example.backend.supplier.entity.Supplier;
import com.example.backend.supplier.entity.SupplierStatus;
import com.example.backend.supplier.exception.SupplierCodeAlreadyExistsException;
import com.example.backend.supplier.exception.SupplierNotFoundException;
import com.example.backend.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private SupplierServiceImpl supplierService;

    @Test
    void getSuppliers_shouldReturnMappedPage() {
        Supplier supplier = supplier("SUP-001", "Nhà cung cấp A", SupplierStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20);

        when(supplierRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(supplier), pageable, 1));

        SupplierPageResponse response = supplierService.getSuppliers(0, 20);

        assertEquals(1, response.getContent().size());
        assertEquals("SUP-001", response.getContent().get(0).getSupplierCode());
        assertEquals(SupplierStatus.ACTIVE, response.getContent().get(0).getStatus());
        assertEquals(1, response.getTotalElements());
        verify(supplierRepository).findAll(pageable);
    }

    @Test
    void getSupplierById_shouldThrowWhenSupplierDoesNotExist() {
        UUID supplierId = UUID.randomUUID();
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.empty());

        assertThrows(
                SupplierNotFoundException.class,
                () -> supplierService.getSupplierById(supplierId)
        );
    }

    @Test
    void createSupplier_shouldPersistRequestFieldsAndUseSchemaDefaultStatus() {
        CreateSupplierRequest request = createRequest("SUP-001", "Nhà cung cấp A");
        UUID supplierId = UUID.randomUUID();

        when(supplierRepository.existsBySupplierCode("SUP-001")).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier supplier = invocation.getArgument(0);
            supplier.setSupplierId(supplierId);
            return supplier;
        });

        SupplierResponse response = supplierService.createSupplier(request);

        assertEquals(supplierId, response.getSupplierId());
        assertEquals("SUP-001", response.getSupplierCode());
        assertEquals("Nhà cung cấp A", response.getSupplierName());
        assertEquals(SupplierStatus.ACTIVE, response.getStatus());
        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    void createSupplier_shouldThrowWhenSupplierCodeAlreadyExists() {
        CreateSupplierRequest request = createRequest("SUP-001", "Nhà cung cấp A");
        when(supplierRepository.existsBySupplierCode("SUP-001")).thenReturn(true);

        assertThrows(
                SupplierCodeAlreadyExistsException.class,
                () -> supplierService.createSupplier(request)
        );

        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    @Test
    void updateSupplier_shouldUpdateEditableFieldsAndPreserveStatus() {
        UUID supplierId = UUID.randomUUID();
        Supplier supplier = supplier("SUP-001", "Tên cũ", SupplierStatus.INACTIVE);
        supplier.setSupplierId(supplierId);
        UpdateSupplierRequest request = updateRequest("SUP-002", "Tên mới");

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsBySupplierCodeAndSupplierIdNot(
                "SUP-002", supplierId
        )).thenReturn(false);
        when(supplierRepository.save(supplier)).thenReturn(supplier);

        SupplierResponse response = supplierService.updateSupplier(supplierId, request);

        assertEquals("SUP-002", response.getSupplierCode());
        assertEquals("Tên mới", response.getSupplierName());
        assertEquals(SupplierStatus.INACTIVE, response.getStatus());
        assertEquals(SupplierStatus.INACTIVE, supplier.getStatus());
        verify(supplierRepository).save(supplier);
    }

    @Test
    void updateSupplier_shouldThrowWhenSupplierDoesNotExist() {
        UUID supplierId = UUID.randomUUID();
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.empty());

        assertThrows(
                SupplierNotFoundException.class,
                () -> supplierService.updateSupplier(
                        supplierId,
                        updateRequest("SUP-002", "Tên mới")
                )
        );

        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    @Test
    void updateSupplier_shouldThrowWhenSupplierCodeBelongsToAnotherSupplier() {
        UUID supplierId = UUID.randomUUID();
        Supplier supplier = supplier("SUP-001", "Nhà cung cấp A", SupplierStatus.ACTIVE);

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsBySupplierCodeAndSupplierIdNot(
                "SUP-002", supplierId
        )).thenReturn(true);

        assertThrows(
                SupplierCodeAlreadyExistsException.class,
                () -> supplierService.updateSupplier(
                        supplierId,
                        updateRequest("SUP-002", "Nhà cung cấp B")
                )
        );

        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    private Supplier supplier(String code, String name, SupplierStatus status) {
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(code);
        supplier.setSupplierName(name);
        supplier.setPhone("0900000000");
        supplier.setEmail("supplier@example.com");
        supplier.setAddress("Quận 1, TP. Hồ Chí Minh");
        supplier.setStatus(status);
        return supplier;
    }

    private CreateSupplierRequest createRequest(String code, String name) {
        CreateSupplierRequest request = new CreateSupplierRequest();
        request.setSupplierCode(code);
        request.setSupplierName(name);
        request.setPhone("0900000000");
        request.setEmail("supplier@example.com");
        request.setAddress("Quận 1, TP. Hồ Chí Minh");
        return request;
    }

    private UpdateSupplierRequest updateRequest(String code, String name) {
        UpdateSupplierRequest request = new UpdateSupplierRequest();
        request.setSupplierCode(code);
        request.setSupplierName(name);
        request.setPhone("0911111111");
        request.setEmail("updated@example.com");
        request.setAddress("Quận 3, TP. Hồ Chí Minh");
        return request;
    }
}
