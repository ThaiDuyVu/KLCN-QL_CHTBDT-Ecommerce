package com.example.backend.inventory.serial.service;

import com.example.backend.inventory.entity.Inventory;
import com.example.backend.inventory.exception.InventoryConflictException;
import com.example.backend.inventory.repository.InventoryRepository;
import com.example.backend.inventory.serial.entity.SerialNumber;
import com.example.backend.inventory.serial.entity.SerialStatus;
import com.example.backend.inventory.serial.exception.SerialConflictException;
import com.example.backend.inventory.serial.repository.SerialNumberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class SerialAllocationServiceImpl implements SerialAllocationService {
    private final SerialNumberRepository serials;
    private final InventoryRepository inventory;
    public SerialAllocationServiceImpl(SerialNumberRepository serials, InventoryRepository inventory) {
        this.serials = serials; this.inventory = inventory;
    }

    @Override
    @Transactional
    public List<UUID> reserveAvailable(UUID variantId, UUID warehouseId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Số lượng serial cần giữ phải lớn hơn 0");
        Inventory stock = inventory.lockByWarehouseAndVariant(warehouseId, variantId).orElseThrow(() ->
                new InventoryConflictException("Không có tồn kho cho biến thể tại kho đã chọn"));
        long alreadyReserved = serials.countByVariant_VariantIdAndWarehouse_WarehouseIdAndStatus(
                variantId, warehouseId, SerialStatus.RESERVED);
        if (alreadyReserved + quantity > stock.getReservedQuantity()) {
            throw new SerialConflictException("Số serial RESERVED sẽ vượt reservedQuantity của tồn kho");
        }
        List<SerialNumber> selected = serials.findAvailableForUpdate(variantId, warehouseId,
                SerialStatus.AVAILABLE, PageRequest.of(0, quantity));
        if (selected.size() != quantity) throw new SerialConflictException("Không đủ serial AVAILABLE để cấp phát");
        selected.forEach(serial -> serial.setStatus(SerialStatus.RESERVED));
        return serials.saveAll(selected).stream().map(SerialNumber::getSerialId).toList();
    }

    @Override
    @Transactional
    public void releaseReserved(List<UUID> serialIds) { transition(serialIds, SerialStatus.RESERVED, SerialStatus.AVAILABLE); }

    @Override
    @Transactional
    public void markSold(List<UUID> serialIds) { transition(serialIds, SerialStatus.RESERVED, SerialStatus.SOLD); }

    private void transition(List<UUID> ids, SerialStatus source, SerialStatus target) {
        if (ids == null || ids.isEmpty()) return;
        Set<UUID> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) throw new SerialConflictException("Danh sách serial chứa ID trùng");
        List<SerialNumber> rows = serials.findAllByIdForUpdate(unique);
        if (rows.size() != unique.size()) throw new SerialConflictException("Có serial không tồn tại");
        for (SerialNumber row : rows) {
            if (row.getStatus() != source) {
                throw new SerialConflictException("Serial " + row.getSerialNumber() + " không ở trạng thái " + source);
            }
            row.setStatus(target);
        }
        serials.saveAll(rows);
    }
}
