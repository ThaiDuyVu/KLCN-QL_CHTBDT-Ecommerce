package com.example.backend.warehouse.service;

import com.example.backend.warehouse.dto.request.CreateWarehouseRequest;
import com.example.backend.warehouse.dto.request.UpdateWarehouseRequest;
import com.example.backend.warehouse.dto.response.WarehousePageResponse;
import com.example.backend.warehouse.dto.response.WarehouseResponse;
import com.example.backend.warehouse.entity.Warehouse;
import com.example.backend.warehouse.entity.WarehouseStatus;
import com.example.backend.warehouse.exception.WarehouseNotFoundException;
import com.example.backend.warehouse.repository.WarehouseRepository;
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
class WarehouseServiceImplTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseServiceImpl warehouseService;

    @Test
    void getWarehouses_shouldReturnMappedPage() {
        Warehouse warehouse = warehouse("Kho trung tâm", WarehouseStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20);

        when(warehouseRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(warehouse), pageable, 1));

        WarehousePageResponse response = warehouseService.getWarehouses(0, 20);

        assertEquals(1, response.getContent().size());
        assertEquals("Kho trung tâm", response.getContent().get(0).getWarehouseName());
        assertEquals(WarehouseStatus.ACTIVE, response.getContent().get(0).getStatus());
        assertEquals(1, response.getTotalElements());
        verify(warehouseRepository).findAll(pageable);
    }

    @Test
    void getWarehouseById_shouldReturnMappedResponse() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = warehouse("Kho trung tâm", WarehouseStatus.ACTIVE);
        warehouse.setWarehouseId(warehouseId);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        WarehouseResponse response = warehouseService.getWarehouseById(warehouseId);

        assertEquals(warehouseId, response.getWarehouseId());
        assertEquals("Kho trung tâm", response.getWarehouseName());
        assertEquals("Quận 1, TP. Hồ Chí Minh", response.getAddress());
        assertEquals(WarehouseStatus.ACTIVE, response.getStatus());
    }

    @Test
    void getWarehouseById_shouldThrowWhenWarehouseDoesNotExist() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        assertThrows(
                WarehouseNotFoundException.class,
                () -> warehouseService.getWarehouseById(warehouseId)
        );
    }

    @Test
    void createWarehouse_shouldPersistRequestFieldsAndUseSchemaDefaultStatus() {
        UUID warehouseId = UUID.randomUUID();
        CreateWarehouseRequest request = createRequest("Kho trung tâm");

        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> {
            Warehouse warehouse = invocation.getArgument(0);
            warehouse.setWarehouseId(warehouseId);
            return warehouse;
        });

        WarehouseResponse response = warehouseService.createWarehouse(request);

        assertEquals(warehouseId, response.getWarehouseId());
        assertEquals("Kho trung tâm", response.getWarehouseName());
        assertEquals("Quận 1, TP. Hồ Chí Minh", response.getAddress());
        assertEquals(WarehouseStatus.ACTIVE, response.getStatus());
        verify(warehouseRepository).save(any(Warehouse.class));
    }

    @Test
    void updateWarehouse_shouldUpdateEditableFieldsAndPreserveStatus() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = warehouse("Kho cũ", WarehouseStatus.INACTIVE);
        warehouse.setWarehouseId(warehouseId);
        UpdateWarehouseRequest request = updateRequest("Kho mới");

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(warehouse)).thenReturn(warehouse);

        WarehouseResponse response = warehouseService.updateWarehouse(warehouseId, request);

        assertEquals("Kho mới", response.getWarehouseName());
        assertEquals("Quận 3, TP. Hồ Chí Minh", response.getAddress());
        assertEquals(WarehouseStatus.INACTIVE, response.getStatus());
        assertEquals(WarehouseStatus.INACTIVE, warehouse.getStatus());
        verify(warehouseRepository).save(warehouse);
    }

    @Test
    void updateWarehouse_shouldThrowWhenWarehouseDoesNotExist() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        assertThrows(
                WarehouseNotFoundException.class,
                () -> warehouseService.updateWarehouse(warehouseId, updateRequest("Kho mới"))
        );

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    private Warehouse warehouse(String name, WarehouseStatus status) {
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseName(name);
        warehouse.setAddress("Quận 1, TP. Hồ Chí Minh");
        warehouse.setStatus(status);
        return warehouse;
    }

    private CreateWarehouseRequest createRequest(String name) {
        CreateWarehouseRequest request = new CreateWarehouseRequest();
        request.setWarehouseName(name);
        request.setAddress("Quận 1, TP. Hồ Chí Minh");
        return request;
    }

    private UpdateWarehouseRequest updateRequest(String name) {
        UpdateWarehouseRequest request = new UpdateWarehouseRequest();
        request.setWarehouseName(name);
        request.setAddress("Quận 3, TP. Hồ Chí Minh");
        return request;
    }
}
