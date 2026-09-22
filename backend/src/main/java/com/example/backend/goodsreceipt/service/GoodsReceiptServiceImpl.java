package com.example.backend.goodsreceipt.service;

import com.example.backend.auth.entity.Employee;
import com.example.backend.auth.repository.EmployeeRepository;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.goodsreceipt.dto.request.*;
import com.example.backend.goodsreceipt.dto.response.*;
import com.example.backend.goodsreceipt.entity.*;
import com.example.backend.goodsreceipt.exception.*;
import com.example.backend.goodsreceipt.repository.*;
import com.example.backend.inventory.repository.InventoryRepository;
import com.example.backend.inventory.serial.entity.*;
import com.example.backend.inventory.serial.exception.SerialConflictException;
import com.example.backend.inventory.serial.repository.*;
import com.example.backend.product.entity.*;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.supplier.entity.Supplier;
import com.example.backend.supplier.exception.SupplierNotFoundException;
import com.example.backend.supplier.repository.SupplierRepository;
import com.example.backend.warehouse.entity.Warehouse;
import com.example.backend.warehouse.exception.WarehouseNotFoundException;
import com.example.backend.warehouse.repository.WarehouseRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class GoodsReceiptServiceImpl implements GoodsReceiptService {
    private static final BigDecimal MAX_TOTAL_AMOUNT = new BigDecimal("9999999999999.99");
    private static final int MAX_PAGE_SIZE = 100;
    private final GoodsReceiptRepository receipts;
    private final SupplierRepository suppliers;
    private final WarehouseRepository warehouses;
    private final EmployeeRepository employees;
    private final ProductVariantRepository variants;
    private final InventoryRepository inventory;
    private final SerialNumberRepository serials;
    private final ImeiRepository imeis;
    private final GoodsReceiptItemDeviceRepository draftSerials;
    private final GoodsReceiptItemDeviceImeiRepository draftImeis;

    public GoodsReceiptServiceImpl(GoodsReceiptRepository receipts, SupplierRepository suppliers,
            WarehouseRepository warehouses, EmployeeRepository employees, ProductVariantRepository variants,
            InventoryRepository inventory, SerialNumberRepository serials, ImeiRepository imeis,
            GoodsReceiptItemDeviceRepository draftSerials, GoodsReceiptItemDeviceImeiRepository draftImeis) {
        this.receipts = receipts; this.suppliers = suppliers; this.warehouses = warehouses; this.employees = employees;
        this.variants = variants; this.inventory = inventory; this.serials = serials; this.imeis = imeis;
        this.draftSerials = draftSerials; this.draftImeis = draftImeis;
    }

    @Override
    public GoodsReceiptPageResponse getGoodsReceipts(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Phân trang không hợp lệ; size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        Page<GoodsReceipt> result = receipts.findAll(PageRequest.of(page, size));
        return new GoodsReceiptPageResponse(result.getContent().stream().map(this::toSummaryResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override public GoodsReceiptResponse getGoodsReceiptById(UUID receiptId) { return toResponse(find(receiptId)); }

    @Override
    @Transactional
    public GoodsReceiptResponse createGoodsReceipt(CreateGoodsReceiptRequest request) {
        if (receipts.existsByReceiptCode(request.getReceiptCode())) {
            throw new ReceiptCodeAlreadyExistsException("Mã phiếu nhập đã tồn tại: " + request.getReceiptCode());
        }
        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setReceiptDate(OffsetDateTime.now());
        receipt.setStatus(GoodsReceiptStatus.DRAFT);
        apply(receipt, request, null);
        return toResponse(receipts.save(receipt));
    }

    @Override
    @Transactional
    public GoodsReceiptResponse updateGoodsReceipt(UUID receiptId, CreateGoodsReceiptRequest request) {
        GoodsReceipt receipt = locked(receiptId);
        if (receipt.getStatus() != GoodsReceiptStatus.DRAFT) {
            throw new InvalidGoodsReceiptStatusTransitionException("Chỉ được sửa phiếu nhập DRAFT");
        }
        if (receipts.existsByReceiptCodeAndReceiptIdNot(request.getReceiptCode(), receiptId)) {
            throw new ReceiptCodeAlreadyExistsException("Mã phiếu nhập đã tồn tại: " + request.getReceiptCode());
        }
        receipt.clearItems();
        receipts.flush();
        apply(receipt, request, receiptId);
        return toResponse(receipts.save(receipt));
    }

    @Override
    @Transactional
    public GoodsReceiptResponse updateGoodsReceiptStatus(UUID receiptId, GoodsReceiptStatus targetStatus) {
        GoodsReceipt receipt = locked(receiptId);
        if (receipt.getStatus() != GoodsReceiptStatus.DRAFT ||
                (targetStatus != GoodsReceiptStatus.CONFIRMED && targetStatus != GoodsReceiptStatus.CANCELLED)) {
            throw new InvalidGoodsReceiptStatusTransitionException(
                    "Không thể chuyển trạng thái phiếu nhập từ " + receipt.getStatus() + " sang " + targetStatus);
        }
        if (targetStatus == GoodsReceiptStatus.CONFIRMED) {
            validateAggregate(receipt);
            persistDevices(receipt);
            incrementInventory(receipt);
        }
        receipt.setStatus(targetStatus);
        return toResponse(receipts.save(receipt));
    }

    private void apply(GoodsReceipt receipt, CreateGoodsReceiptRequest request, UUID excludedReceiptId) {
        Supplier supplier = suppliers.findById(request.getSupplierId()).orElseThrow(() ->
                new SupplierNotFoundException("Không tìm thấy nhà cung cấp với ID: " + request.getSupplierId()));
        Warehouse warehouse = warehouses.findById(request.getWarehouseId()).orElseThrow(() ->
                new WarehouseNotFoundException("Không tìm thấy kho với ID: " + request.getWarehouseId()));
        Employee employee = resolveEmployee(request.getEmployeeId());
        receipt.setReceiptCode(request.getReceiptCode()); receipt.setSupplier(supplier);
        receipt.setWarehouse(warehouse); receipt.setEmployee(employee);

        List<CreateGoodsReceiptItemRequest> requested = request.getItems() == null ? List.of() : request.getItems();
        Set<String> requestSerials = new HashSet<>(); Set<String> requestImeis = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CreateGoodsReceiptItemRequest line : requested) {
            ProductVariant variant = variants.findById(line.getVariantId()).orElseThrow(() ->
                    new GoodsReceiptReferenceNotFoundException("Không tìm thấy biến thể với ID: " + line.getVariantId()));
            GoodsReceiptItem item = new GoodsReceiptItem(); item.setVariant(variant);
            item.setQuantity(line.getQuantity()); item.setUnitCost(line.getUnitCost());
            addDevices(item, line.getDevices(), requestSerials, requestImeis, excludedReceiptId);
            validateItem(item);
            receipt.addItem(item);
            total = total.add(line.getUnitCost().multiply(BigDecimal.valueOf(line.getQuantity())));
        }
        if (total.compareTo(MAX_TOTAL_AMOUNT) > 0) {
            throw new GoodsReceiptTotalAmountExceededException("Tổng tiền phiếu nhập vượt quá NUMERIC(15,2)");
        }
        receipt.setTotalAmount(total);
    }

    private void addDevices(GoodsReceiptItem item, List<ReceiptDeviceRequest> requests, Set<String> seenSerials,
                            Set<String> seenImeis, UUID excludedReceiptId) {
        for (ReceiptDeviceRequest request : requests == null ? List.<ReceiptDeviceRequest>of() : requests) {
            String serialValue = request.getSerialNumber().trim();
            if (!seenSerials.add(serialValue)) throw new SerialConflictException("Serial bị trùng trong phiếu nhập: " + serialValue);
            boolean stagedSerialExists = excludedReceiptId == null
                    ? draftSerials.existsInActiveReceipt(serialValue, GoodsReceiptStatus.CANCELLED)
                    : draftSerials.existsInOtherActiveReceipt(serialValue, GoodsReceiptStatus.CANCELLED, excludedReceiptId);
            if (serials.existsBySerialNumber(serialValue) || stagedSerialExists) {
                throw new SerialConflictException("Serial đã tồn tại: " + serialValue);
            }
            GoodsReceiptItemDevice device = new GoodsReceiptItemDevice(); device.setSerialNumber(serialValue);
            for (String rawImei : request.getImeiNumbers() == null ? List.<String>of() : request.getImeiNumbers()) {
                String imeiValue = rawImei.trim();
                if (!seenImeis.add(imeiValue)) throw new SerialConflictException("IMEI bị trùng trong phiếu nhập: " + imeiValue);
                boolean stagedImeiExists = excludedReceiptId == null
                        ? draftImeis.existsInActiveReceipt(imeiValue, GoodsReceiptStatus.CANCELLED)
                        : draftImeis.existsInOtherActiveReceipt(imeiValue, GoodsReceiptStatus.CANCELLED, excludedReceiptId);
                if (imeis.existsByImeiNumber(imeiValue) || stagedImeiExists) {
                    throw new SerialConflictException("IMEI đã tồn tại: " + imeiValue);
                }
                GoodsReceiptItemDeviceImei imei = new GoodsReceiptItemDeviceImei(); imei.setImeiNumber(imeiValue);
                device.addImei(imei);
            }
            item.addDevice(device);
        }
    }

    private void validateAggregate(GoodsReceipt receipt) { receipt.getItems().forEach(this::validateItem); }

    private void validateItem(GoodsReceiptItem item) {
        ProductTrackingType type = item.getVariant().getTrackingType();
        int deviceCount = item.getDevices().size();
        if (type == ProductTrackingType.NONE && deviceCount != 0) {
            throw new InvalidReceiptDeviceException("SKU " + item.getVariant().getSku() + " không quản lý serial/IMEI");
        }
        if (type != ProductTrackingType.NONE && deviceCount != item.getQuantity()) {
            throw new InvalidReceiptDeviceException("SKU " + item.getVariant().getSku() + " cần đúng "
                    + item.getQuantity() + " serial, hiện có " + deviceCount);
        }
        for (GoodsReceiptItemDevice device : item.getDevices()) {
            if (type == ProductTrackingType.SERIAL && !device.getImeis().isEmpty()) {
                throw new InvalidReceiptDeviceException("SKU " + item.getVariant().getSku() + " chỉ quản lý SERIAL");
            }
            if (type == ProductTrackingType.IMEI && device.getImeis().isEmpty()) {
                throw new InvalidReceiptDeviceException("Mỗi serial của SKU " + item.getVariant().getSku() + " phải có ít nhất một IMEI");
            }
        }
    }

    private void persistDevices(GoodsReceipt receipt) {
        List<SerialNumber> created = new ArrayList<>();
        for (GoodsReceiptItem item : receipt.getItems()) for (GoodsReceiptItemDevice draft : item.getDevices()) {
            if (serials.existsBySerialNumber(draft.getSerialNumber())) {
                throw new SerialConflictException("Serial đã tồn tại: " + draft.getSerialNumber());
            }
            SerialNumber serial = new SerialNumber(); serial.setSerialNumber(draft.getSerialNumber());
            serial.setVariant(item.getVariant()); serial.setWarehouse(receipt.getWarehouse()); serial.setStatus(SerialStatus.AVAILABLE);
            for (GoodsReceiptItemDeviceImei draftImei : draft.getImeis()) {
                if (imeis.existsByImeiNumber(draftImei.getImeiNumber())) {
                    throw new SerialConflictException("IMEI đã tồn tại: " + draftImei.getImeiNumber());
                }
                Imei imei = new Imei(); imei.setImeiNumber(draftImei.getImeiNumber()); serial.addImei(imei);
            }
            created.add(serial);
        }
        if (created.isEmpty()) return;
        try { serials.saveAllAndFlush(created); }
        catch (DataIntegrityViolationException exception) {
            throw new SerialConflictException("Serial/IMEI bị trùng khi xác nhận phiếu nhập", exception);
        }
    }

    private void incrementInventory(GoodsReceipt receipt) {
        Map<UUID, Integer> totals = new LinkedHashMap<>();
        receipt.getItems().forEach(item -> totals.merge(item.getVariant().getVariantId(), item.getQuantity(), Math::addExact));
        totals.forEach((variantId, quantity) -> inventory.incrementQuantity(receipt.getWarehouse().getWarehouseId(), variantId, quantity));
    }

    private Employee resolveEmployee(UUID requestedEmployeeId) {
        if (requestedEmployeeId != null) {
            return employees.findById(requestedEmployeeId).orElseThrow(() ->
                    new GoodsReceiptReferenceNotFoundException("Không tìm thấy nhân viên với ID: " + requestedEmployeeId));
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedUserPrincipal authenticated) {
            return employees.findByUser_UserId(authenticated.getUserId()).orElseThrow(() ->
                    new GoodsReceiptReferenceNotFoundException("Tài khoản hiện tại chưa được liên kết với nhân viên"));
        }
        throw new GoodsReceiptReferenceNotFoundException("Không xác định được nhân viên lập phiếu");
    }

    private GoodsReceipt find(UUID id) { return receipts.findById(id).orElseThrow(() ->
            new GoodsReceiptNotFoundException("Không tìm thấy phiếu nhập với ID: " + id)); }
    private GoodsReceipt locked(UUID id) { return receipts.findByReceiptIdForUpdate(id).orElseThrow(() ->
            new GoodsReceiptNotFoundException("Không tìm thấy phiếu nhập với ID: " + id)); }

    private GoodsReceiptSummaryResponse toSummaryResponse(GoodsReceipt r) {
        return new GoodsReceiptSummaryResponse(r.getReceiptId(), r.getReceiptCode(), r.getSupplier().getSupplierId(),
                r.getWarehouse().getWarehouseId(), r.getEmployee().getEmployeeId(), r.getReceiptDate(), r.getTotalAmount(), r.getStatus());
    }
    private GoodsReceiptResponse toResponse(GoodsReceipt r) {
        List<GoodsReceiptItemResponse> lines = r.getItems().stream().map(item -> new GoodsReceiptItemResponse(
                item.getReceiptItemId(), item.getVariant().getVariantId(), item.getQuantity(), item.getUnitCost(),
                item.getVariant().getSku(), item.getVariant().getProduct() == null ? null : item.getVariant().getProduct().getProductName(),
                item.getVariant().getTrackingType(),
                item.getDevices().stream().map(d -> new ReceiptDeviceResponse(d.getReceiptDeviceId(), d.getSerialNumber(),
                        d.getImeis().stream().map(GoodsReceiptItemDeviceImei::getImeiNumber).toList())).toList())).toList();
        return new GoodsReceiptResponse(r.getReceiptId(), r.getReceiptCode(), r.getSupplier().getSupplierId(),
                r.getWarehouse().getWarehouseId(), r.getEmployee().getEmployeeId(), r.getReceiptDate(), r.getTotalAmount(), r.getStatus(), lines);
    }
}
