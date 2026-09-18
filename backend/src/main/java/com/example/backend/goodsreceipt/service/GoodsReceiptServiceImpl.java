package com.example.backend.goodsreceipt.service;

import com.example.backend.auth.entity.Employee;
import com.example.backend.auth.repository.EmployeeRepository;
import com.example.backend.goodsreceipt.dto.request.CreateGoodsReceiptItemRequest;
import com.example.backend.goodsreceipt.dto.request.CreateGoodsReceiptRequest;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptItemResponse;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptPageResponse;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptResponse;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptSummaryResponse;
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
import com.example.backend.warehouse.exception.WarehouseNotFoundException;
import com.example.backend.warehouse.repository.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GoodsReceiptServiceImpl implements GoodsReceiptService {

    private static final BigDecimal MAX_TOTAL_AMOUNT = new BigDecimal("9999999999999.99");

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final EmployeeRepository employeeRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;

    public GoodsReceiptServiceImpl(
            GoodsReceiptRepository goodsReceiptRepository,
            SupplierRepository supplierRepository,
            WarehouseRepository warehouseRepository,
            EmployeeRepository employeeRepository,
            ProductVariantRepository productVariantRepository,
            InventoryRepository inventoryRepository
    ) {
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.supplierRepository = supplierRepository;
        this.warehouseRepository = warehouseRepository;
        this.employeeRepository = employeeRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public GoodsReceiptPageResponse getGoodsReceipts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<GoodsReceipt> receiptPage = goodsReceiptRepository.findAll(pageable);

        List<GoodsReceiptSummaryResponse> content = receiptPage.getContent()
                .stream()
                .map(this::toSummaryResponse)
                .toList();

        return new GoodsReceiptPageResponse(
                content,
                receiptPage.getNumber(),
                receiptPage.getSize(),
                receiptPage.getTotalElements(),
                receiptPage.getTotalPages()
        );
    }

    @Override
    public GoodsReceiptResponse getGoodsReceiptById(UUID receiptId) {
        return toResponse(findGoodsReceiptById(receiptId));
    }

    @Override
    @Transactional
    public GoodsReceiptResponse createGoodsReceipt(CreateGoodsReceiptRequest request) {
        if (goodsReceiptRepository.existsByReceiptCode(request.getReceiptCode())) {
            throw new ReceiptCodeAlreadyExistsException(
                    "Mã phiếu nhập đã tồn tại: " + request.getReceiptCode()
            );
        }

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new SupplierNotFoundException(
                        "Không tìm thấy nhà cung cấp với ID: " + request.getSupplierId()
                ));
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new WarehouseNotFoundException(
                        "Không tìm thấy kho với ID: " + request.getWarehouseId()
                ));
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new GoodsReceiptReferenceNotFoundException(
                        "Không tìm thấy nhân viên với ID: " + request.getEmployeeId()
                ));

        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setReceiptCode(request.getReceiptCode());
        receipt.setSupplier(supplier);
        receipt.setWarehouse(warehouse);
        receipt.setEmployee(employee);
        receipt.setReceiptDate(OffsetDateTime.now());
        receipt.setStatus(GoodsReceiptStatus.DRAFT);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<CreateGoodsReceiptItemRequest> requestedItems = request.getItems() == null
                ? List.of()
                : request.getItems();

        for (CreateGoodsReceiptItemRequest itemRequest : requestedItems) {
            ProductVariant variant = productVariantRepository.findById(itemRequest.getVariantId())
                    .orElseThrow(() -> new GoodsReceiptReferenceNotFoundException(
                            "Không tìm thấy biến thể sản phẩm với ID: " + itemRequest.getVariantId()
                    ));

            GoodsReceiptItem item = new GoodsReceiptItem();
            item.setVariant(variant);
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitCost(itemRequest.getUnitCost());
            receipt.addItem(item);

            totalAmount = totalAmount.add(
                    itemRequest.getUnitCost().multiply(BigDecimal.valueOf(itemRequest.getQuantity()))
            );
        }

        if (totalAmount.compareTo(MAX_TOTAL_AMOUNT) > 0) {
            throw new GoodsReceiptTotalAmountExceededException(
                    "Tổng tiền phiếu nhập vượt quá NUMERIC(15,2)"
            );
        }

        receipt.setTotalAmount(totalAmount);
        return toResponse(goodsReceiptRepository.save(receipt));
    }

    @Override
    @Transactional
    public GoodsReceiptResponse updateGoodsReceiptStatus(
            UUID receiptId,
            GoodsReceiptStatus targetStatus
    ) {
        GoodsReceipt receipt = findGoodsReceiptByIdForUpdate(receiptId);
        GoodsReceiptStatus currentStatus = receipt.getStatus();

        if (currentStatus != GoodsReceiptStatus.DRAFT
                || (targetStatus != GoodsReceiptStatus.CONFIRMED
                && targetStatus != GoodsReceiptStatus.CANCELLED)) {
            throw new InvalidGoodsReceiptStatusTransitionException(
                    "Không thể chuyển trạng thái phiếu nhập từ " + currentStatus + " sang " + targetStatus
            );
        }

        if (targetStatus == GoodsReceiptStatus.CONFIRMED) {
            incrementInventory(receipt);
        }

        receipt.setStatus(targetStatus);
        return toResponse(goodsReceiptRepository.save(receipt));
    }

    private void incrementInventory(GoodsReceipt receipt) {
        UUID warehouseId = receipt.getWarehouse().getWarehouseId();
        Map<UUID, Integer> quantitiesByVariant = new LinkedHashMap<>();

        for (GoodsReceiptItem item : receipt.getItems()) {
            quantitiesByVariant.merge(
                    item.getVariant().getVariantId(),
                    item.getQuantity(),
                    Math::addExact
            );
        }

        quantitiesByVariant.forEach((variantId, quantity) ->
                inventoryRepository.incrementQuantity(warehouseId, variantId, quantity)
        );
    }

    private GoodsReceipt findGoodsReceiptById(UUID receiptId) {
        return goodsReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new GoodsReceiptNotFoundException(
                        "Không tìm thấy phiếu nhập với ID: " + receiptId
                ));
    }

    private GoodsReceipt findGoodsReceiptByIdForUpdate(UUID receiptId) {
        return goodsReceiptRepository.findByReceiptIdForUpdate(receiptId)
                .orElseThrow(() -> new GoodsReceiptNotFoundException(
                        "Không tìm thấy phiếu nhập với ID: " + receiptId
                ));
    }

    private GoodsReceiptSummaryResponse toSummaryResponse(GoodsReceipt receipt) {
        return new GoodsReceiptSummaryResponse(
                receipt.getReceiptId(),
                receipt.getReceiptCode(),
                receipt.getSupplier().getSupplierId(),
                receipt.getWarehouse().getWarehouseId(),
                receipt.getEmployee().getEmployeeId(),
                receipt.getReceiptDate(),
                receipt.getTotalAmount(),
                receipt.getStatus()
        );
    }

    private GoodsReceiptResponse toResponse(GoodsReceipt receipt) {
        List<GoodsReceiptItemResponse> items = receipt.getItems()
                .stream()
                .map(item -> new GoodsReceiptItemResponse(
                        item.getReceiptItemId(),
                        item.getVariant().getVariantId(),
                        item.getQuantity(),
                        item.getUnitCost()
                ))
                .toList();

        return new GoodsReceiptResponse(
                receipt.getReceiptId(),
                receipt.getReceiptCode(),
                receipt.getSupplier().getSupplierId(),
                receipt.getWarehouse().getWarehouseId(),
                receipt.getEmployee().getEmployeeId(),
                receipt.getReceiptDate(),
                receipt.getTotalAmount(),
                receipt.getStatus(),
                items
        );
    }
}
