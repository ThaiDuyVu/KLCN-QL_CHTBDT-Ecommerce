package com.example.backend.goodsreceipt.service;

import com.example.backend.auth.entity.Employee;
import com.example.backend.auth.repository.EmployeeRepository;
import com.example.backend.goodsreceipt.dto.request.CreateGoodsReceiptItemRequest;
import com.example.backend.goodsreceipt.dto.request.CreateGoodsReceiptRequest;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptPageResponse;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptResponse;
import com.example.backend.goodsreceipt.entity.GoodsReceipt;
import com.example.backend.goodsreceipt.entity.GoodsReceiptItem;
import com.example.backend.goodsreceipt.entity.GoodsReceiptStatus;
import com.example.backend.goodsreceipt.exception.InvalidGoodsReceiptStatusTransitionException;
import com.example.backend.goodsreceipt.exception.GoodsReceiptNotFoundException;
import com.example.backend.goodsreceipt.exception.GoodsReceiptReferenceNotFoundException;
import com.example.backend.goodsreceipt.exception.GoodsReceiptTotalAmountExceededException;
import com.example.backend.goodsreceipt.exception.ReceiptCodeAlreadyExistsException;
import com.example.backend.goodsreceipt.repository.GoodsReceiptRepository;
import com.example.backend.inventory.repository.InventoryRepository;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.supplier.entity.Supplier;
import com.example.backend.supplier.exception.SupplierNotFoundException;
import com.example.backend.supplier.repository.SupplierRepository;
import com.example.backend.warehouse.entity.Warehouse;
import com.example.backend.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsReceiptServiceImplTest {

    @Mock
    private GoodsReceiptRepository goodsReceiptRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private GoodsReceiptServiceImpl goodsReceiptService;

    @Test
    void getGoodsReceipts_shouldReturnMappedHeaderPage() {
        GoodsReceipt receipt = receiptWithItems();
        PageRequest pageable = PageRequest.of(0, 20);
        when(goodsReceiptRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(receipt), pageable, 1));

        GoodsReceiptPageResponse response = goodsReceiptService.getGoodsReceipts(0, 20);

        assertEquals(1, response.getContent().size());
        assertEquals("GR-001", response.getContent().get(0).getReceiptCode());
        assertEquals(new BigDecimal("350000.00"), response.getContent().get(0).getTotalAmount());
        assertEquals(GoodsReceiptStatus.DRAFT, response.getContent().get(0).getStatus());
        verify(goodsReceiptRepository).findAll(pageable);
    }

    @Test
    void getGoodsReceiptById_shouldReturnHeaderAndItems() {
        GoodsReceipt receipt = receiptWithItems();
        when(goodsReceiptRepository.findById(receipt.getReceiptId()))
                .thenReturn(Optional.of(receipt));

        GoodsReceiptResponse response = goodsReceiptService.getGoodsReceiptById(receipt.getReceiptId());

        assertEquals(receipt.getReceiptId(), response.getReceiptId());
        assertEquals(2, response.getItems().size());
        assertEquals(2, response.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("100000.00"), response.getItems().get(0).getUnitCost());
    }

    @Test
    void getGoodsReceiptById_shouldThrowWhenReceiptDoesNotExist() {
        UUID receiptId = UUID.randomUUID();
        when(goodsReceiptRepository.findById(receiptId)).thenReturn(Optional.empty());

        assertThrows(
                GoodsReceiptNotFoundException.class,
                () -> goodsReceiptService.getGoodsReceiptById(receiptId)
        );
    }

    @Test
    void createGoodsReceipt_shouldPersistAggregateAndCalculateTotal() {
        UUID supplierId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID firstVariantId = UUID.randomUUID();
        UUID secondVariantId = UUID.randomUUID();
        CreateGoodsReceiptRequest request = createRequest(
                supplierId,
                warehouseId,
                employeeId,
                List.of(
                        itemRequest(firstVariantId, 2, "100000.00"),
                        itemRequest(secondVariantId, 3, "50000.00")
                )
        );

        when(goodsReceiptRepository.existsByReceiptCode("GR-001")).thenReturn(false);
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier(supplierId)));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId)));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee(employeeId)));
        when(productVariantRepository.findById(firstVariantId))
                .thenReturn(Optional.of(productVariant(firstVariantId)));
        when(productVariantRepository.findById(secondVariantId))
                .thenReturn(Optional.of(productVariant(secondVariantId)));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> {
            GoodsReceipt receipt = invocation.getArgument(0);
            receipt.setReceiptId(UUID.randomUUID());
            receipt.getItems().forEach(item -> item.setReceiptItemId(UUID.randomUUID()));
            return receipt;
        });

        GoodsReceiptResponse response = goodsReceiptService.createGoodsReceipt(request);

        ArgumentCaptor<GoodsReceipt> receiptCaptor = ArgumentCaptor.forClass(GoodsReceipt.class);
        verify(goodsReceiptRepository).save(receiptCaptor.capture());
        GoodsReceipt persistedReceipt = receiptCaptor.getValue();
        assertEquals(new BigDecimal("350000.00"), persistedReceipt.getTotalAmount());
        assertEquals(2, persistedReceipt.getItems().size());
        assertSame(persistedReceipt, persistedReceipt.getItems().get(0).getReceipt());
        assertSame(persistedReceipt, persistedReceipt.getItems().get(1).getReceipt());
        assertEquals(GoodsReceiptStatus.DRAFT, persistedReceipt.getStatus());
        assertEquals(GoodsReceiptStatus.DRAFT, response.getStatus());
        assertEquals(new BigDecimal("350000.00"), response.getTotalAmount());
        assertEquals(2, response.getItems().size());
        assertNotNull(response.getReceiptDate());
    }

    @Test
    void createGoodsReceipt_shouldAllowZeroItemsAndUseZeroTotal() {
        UUID supplierId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        CreateGoodsReceiptRequest request = createRequest(
                supplierId, warehouseId, employeeId, List.of()
        );

        when(goodsReceiptRepository.existsByReceiptCode("GR-001")).thenReturn(false);
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier(supplierId)));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId)));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee(employeeId)));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GoodsReceiptResponse response = goodsReceiptService.createGoodsReceipt(request);

        assertEquals(BigDecimal.ZERO, response.getTotalAmount());
        assertEquals(0, response.getItems().size());
        verify(productVariantRepository, never()).findById(any());
    }

    @Test
    void createGoodsReceipt_shouldKeepDuplicateVariantLinesSeparate() {
        UUID supplierId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        CreateGoodsReceiptRequest request = createRequest(
                supplierId,
                warehouseId,
                employeeId,
                List.of(itemRequest(variantId, 1, "100.00"), itemRequest(variantId, 2, "100.00"))
        );

        when(goodsReceiptRepository.existsByReceiptCode("GR-001")).thenReturn(false);
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier(supplierId)));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId)));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee(employeeId)));
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(productVariant(variantId)));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GoodsReceiptResponse response = goodsReceiptService.createGoodsReceipt(request);

        assertEquals(2, response.getItems().size());
        assertEquals(new BigDecimal("300.00"), response.getTotalAmount());
        verify(productVariantRepository, times(2)).findById(variantId);
    }

    @Test
    void createGoodsReceipt_shouldRejectTotalThatExceedsNumericCapacity() {
        UUID supplierId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        CreateGoodsReceiptRequest request = createRequest(
                supplierId,
                warehouseId,
                employeeId,
                List.of(itemRequest(variantId, 2, "9999999999999.99"))
        );
        when(goodsReceiptRepository.existsByReceiptCode("GR-001")).thenReturn(false);
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier(supplierId)));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId)));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee(employeeId)));
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(productVariant(variantId)));

        assertThrows(
                GoodsReceiptTotalAmountExceededException.class,
                () -> goodsReceiptService.createGoodsReceipt(request)
        );

        verify(goodsReceiptRepository, never()).save(any(GoodsReceipt.class));
    }

    @Test
    void createGoodsReceipt_shouldThrowConflictWithoutSavingWhenCodeAlreadyExists() {
        CreateGoodsReceiptRequest request = createRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of()
        );
        when(goodsReceiptRepository.existsByReceiptCode("GR-001")).thenReturn(true);

        assertThrows(
                ReceiptCodeAlreadyExistsException.class,
                () -> goodsReceiptService.createGoodsReceipt(request)
        );

        verify(goodsReceiptRepository, never()).save(any(GoodsReceipt.class));
    }

    @Test
    void createGoodsReceipt_shouldThrowNotFoundWithoutSavingWhenSupplierDoesNotExist() {
        UUID supplierId = UUID.randomUUID();
        CreateGoodsReceiptRequest request = createRequest(
                supplierId, UUID.randomUUID(), UUID.randomUUID(), List.of()
        );
        when(goodsReceiptRepository.existsByReceiptCode("GR-001")).thenReturn(false);
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.empty());

        assertThrows(
                SupplierNotFoundException.class,
                () -> goodsReceiptService.createGoodsReceipt(request)
        );

        verify(goodsReceiptRepository, never()).save(any(GoodsReceipt.class));
    }

    @Test
    void createGoodsReceipt_shouldThrowNotFoundWithoutSavingWhenVariantDoesNotExist() {
        UUID supplierId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        CreateGoodsReceiptRequest request = createRequest(
                supplierId, warehouseId, employeeId, List.of(itemRequest(variantId, 1, "100.00"))
        );
        when(goodsReceiptRepository.existsByReceiptCode("GR-001")).thenReturn(false);
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier(supplierId)));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId)));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee(employeeId)));
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.empty());

        assertThrows(
                GoodsReceiptReferenceNotFoundException.class,
                () -> goodsReceiptService.createGoodsReceipt(request)
        );

        verify(goodsReceiptRepository, never()).save(any(GoodsReceipt.class));
    }

    @Test
    void updateGoodsReceiptStatus_shouldAllowDraftToConfirmed() {
        GoodsReceipt receipt = receiptWithStatus(GoodsReceiptStatus.DRAFT);
        when(goodsReceiptRepository.findByReceiptIdForUpdate(receipt.getReceiptId()))
                .thenReturn(Optional.of(receipt));
        when(goodsReceiptRepository.save(receipt)).thenReturn(receipt);

        GoodsReceiptResponse response = goodsReceiptService.updateGoodsReceiptStatus(
                receipt.getReceiptId(), GoodsReceiptStatus.CONFIRMED
        );

        assertEquals(GoodsReceiptStatus.CONFIRMED, response.getStatus());
        assertEquals(GoodsReceiptStatus.CONFIRMED, receipt.getStatus());
        verify(inventoryRepository).incrementQuantity(
                receipt.getWarehouse().getWarehouseId(),
                receipt.getItems().get(0).getVariant().getVariantId(),
                2
        );
        verify(inventoryRepository).incrementQuantity(
                receipt.getWarehouse().getWarehouseId(),
                receipt.getItems().get(1).getVariant().getVariantId(),
                3
        );
        verify(goodsReceiptRepository).save(receipt);
    }

    @Test
    void updateGoodsReceiptStatus_shouldAggregateDuplicateVariantQuantities() {
        GoodsReceipt receipt = receiptWithStatus(GoodsReceiptStatus.DRAFT);
        UUID variantId = receipt.getItems().get(0).getVariant().getVariantId();
        receipt.getItems().get(1).setVariant(productVariant(variantId));
        when(goodsReceiptRepository.findByReceiptIdForUpdate(receipt.getReceiptId()))
                .thenReturn(Optional.of(receipt));
        when(goodsReceiptRepository.save(receipt)).thenReturn(receipt);

        goodsReceiptService.updateGoodsReceiptStatus(
                receipt.getReceiptId(), GoodsReceiptStatus.CONFIRMED
        );

        verify(inventoryRepository).incrementQuantity(
                receipt.getWarehouse().getWarehouseId(), variantId, 5
        );
    }

    @Test
    void updateGoodsReceiptStatus_shouldAllowDraftToCancelled() {
        GoodsReceipt receipt = receiptWithStatus(GoodsReceiptStatus.DRAFT);
        when(goodsReceiptRepository.findByReceiptIdForUpdate(receipt.getReceiptId()))
                .thenReturn(Optional.of(receipt));
        when(goodsReceiptRepository.save(receipt)).thenReturn(receipt);

        GoodsReceiptResponse response = goodsReceiptService.updateGoodsReceiptStatus(
                receipt.getReceiptId(), GoodsReceiptStatus.CANCELLED
        );

        assertEquals(GoodsReceiptStatus.CANCELLED, response.getStatus());
        verifyNoInteractions(inventoryRepository);
        verify(goodsReceiptRepository).save(receipt);
    }

    @Test
    void updateGoodsReceiptStatus_shouldRejectUnsupportedTransitionsWithoutSaving() {
        assertUnsupportedTransition(GoodsReceiptStatus.DRAFT, GoodsReceiptStatus.DRAFT);
        assertUnsupportedTransition(GoodsReceiptStatus.CONFIRMED, GoodsReceiptStatus.DRAFT);
        assertUnsupportedTransition(GoodsReceiptStatus.CONFIRMED, GoodsReceiptStatus.CANCELLED);
        assertUnsupportedTransition(GoodsReceiptStatus.CANCELLED, GoodsReceiptStatus.DRAFT);
        assertUnsupportedTransition(GoodsReceiptStatus.CANCELLED, GoodsReceiptStatus.CONFIRMED);
    }

    @Test
    void updateGoodsReceiptStatus_shouldThrowWhenReceiptDoesNotExist() {
        UUID receiptId = UUID.randomUUID();
        when(goodsReceiptRepository.findByReceiptIdForUpdate(receiptId)).thenReturn(Optional.empty());

        assertThrows(
                GoodsReceiptNotFoundException.class,
                () -> goodsReceiptService.updateGoodsReceiptStatus(receiptId, GoodsReceiptStatus.CONFIRMED)
        );
        verify(goodsReceiptRepository, never()).save(any(GoodsReceipt.class));
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void updateGoodsReceiptStatus_shouldNotCompleteWhenInventoryUpdateFails() {
        GoodsReceipt receipt = receiptWithStatus(GoodsReceiptStatus.DRAFT);
        GoodsReceiptItem firstItem = receipt.getItems().get(0);
        when(goodsReceiptRepository.findByReceiptIdForUpdate(receipt.getReceiptId()))
                .thenReturn(Optional.of(receipt));
        when(inventoryRepository.incrementQuantity(
                receipt.getWarehouse().getWarehouseId(),
                firstItem.getVariant().getVariantId(),
                firstItem.getQuantity()
        )).thenThrow(new IllegalStateException("inventory write failed"));

        assertThrows(
                IllegalStateException.class,
                () -> goodsReceiptService.updateGoodsReceiptStatus(
                        receipt.getReceiptId(), GoodsReceiptStatus.CONFIRMED
                )
        );

        assertEquals(GoodsReceiptStatus.DRAFT, receipt.getStatus());
        verify(goodsReceiptRepository, never()).save(receipt);
    }

    private GoodsReceipt receiptWithItems() {
        Supplier supplier = supplier(UUID.randomUUID());
        Warehouse warehouse = warehouse(UUID.randomUUID());
        Employee employee = employee(UUID.randomUUID());
        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setReceiptId(UUID.randomUUID());
        receipt.setReceiptCode("GR-001");
        receipt.setSupplier(supplier);
        receipt.setWarehouse(warehouse);
        receipt.setEmployee(employee);
        receipt.setReceiptDate(OffsetDateTime.parse("2026-09-12T10:00:00+07:00"));
        receipt.setTotalAmount(new BigDecimal("350000.00"));
        receipt.setStatus(GoodsReceiptStatus.DRAFT);
        receipt.addItem(receiptItem(productVariant(UUID.randomUUID()), 2, "100000.00"));
        receipt.addItem(receiptItem(productVariant(UUID.randomUUID()), 3, "50000.00"));
        return receipt;
    }

    private GoodsReceiptItem receiptItem(ProductVariant variant, int quantity, String unitCost) {
        GoodsReceiptItem item = new GoodsReceiptItem();
        item.setReceiptItemId(UUID.randomUUID());
        item.setVariant(variant);
        item.setQuantity(quantity);
        item.setUnitCost(new BigDecimal(unitCost));
        return item;
    }

    private CreateGoodsReceiptRequest createRequest(
            UUID supplierId,
            UUID warehouseId,
            UUID employeeId,
            List<CreateGoodsReceiptItemRequest> items
    ) {
        CreateGoodsReceiptRequest request = new CreateGoodsReceiptRequest();
        request.setReceiptCode("GR-001");
        request.setSupplierId(supplierId);
        request.setWarehouseId(warehouseId);
        request.setEmployeeId(employeeId);
        request.setItems(items);
        return request;
    }

    private GoodsReceipt receiptWithStatus(GoodsReceiptStatus status) {
        GoodsReceipt receipt = receiptWithItems();
        receipt.setStatus(status);
        return receipt;
    }

    private void assertUnsupportedTransition(
            GoodsReceiptStatus currentStatus,
            GoodsReceiptStatus targetStatus
    ) {
        GoodsReceipt receipt = receiptWithStatus(currentStatus);
        when(goodsReceiptRepository.findByReceiptIdForUpdate(receipt.getReceiptId()))
                .thenReturn(Optional.of(receipt));

        assertThrows(
                InvalidGoodsReceiptStatusTransitionException.class,
                () -> goodsReceiptService.updateGoodsReceiptStatus(receipt.getReceiptId(), targetStatus)
        );
        assertEquals(currentStatus, receipt.getStatus());
        verify(goodsReceiptRepository, never()).save(receipt);
        verifyNoInteractions(inventoryRepository);
    }

    private CreateGoodsReceiptItemRequest itemRequest(UUID variantId, int quantity, String unitCost) {
        CreateGoodsReceiptItemRequest request = new CreateGoodsReceiptItemRequest();
        request.setVariantId(variantId);
        request.setQuantity(quantity);
        request.setUnitCost(new BigDecimal(unitCost));
        return request;
    }

    private Supplier supplier(UUID supplierId) {
        Supplier supplier = new Supplier();
        supplier.setSupplierId(supplierId);
        return supplier;
    }

    private Warehouse warehouse(UUID warehouseId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseId(warehouseId);
        return warehouse;
    }

    private Employee employee(UUID employeeId) {
        Employee employee = new Employee();
        employee.setEmployeeId(employeeId);
        return employee;
    }

    private ProductVariant productVariant(UUID variantId) {
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(variantId);
        return variant;
    }
}
