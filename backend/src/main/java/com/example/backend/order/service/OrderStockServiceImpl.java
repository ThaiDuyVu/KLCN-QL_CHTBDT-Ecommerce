package com.example.backend.order.service;
import com.example.backend.inventory.repository.InventoryRepository;
import com.example.backend.order.exception.CommerceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
@Service
public class OrderStockServiceImpl implements OrderStockService {
    private final InventoryRepository inventory;
    public OrderStockServiceImpl(InventoryRepository inventory) { this.inventory = inventory; }
    // All callers hold an order/customer lock and use the same global variant order.
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(UUID warehouseId, Map<UUID, Integer> quantities, Action action) {
        if (warehouseId == null) throw new CommerceException(409, "Đơn hàng chưa có chi nhánh fulfillment");
        for (var id : quantities.keySet().stream().sorted().toList()) {
            var row = inventory.lockByWarehouseAndVariant(warehouseId, id)
                    .orElseThrow(() -> new CommerceException(409,
                            "Không có tồn kho cho variant " + id + " tại chi nhánh của đơn hàng"));
            long available = action == Action.RESERVE
                    ? (long) row.getQuantity() - row.getReservedQuantity()
                    : row.getReservedQuantity();
            int remaining = quantities.get(id);
            if (available < remaining) throw new CommerceException(409, action == Action.RESERVE ? "Không đủ stock cho variant " + id : "Reserved stock không đủ cho variant " + id);
            row.setReservedQuantity(row.getReservedQuantity() + (action == Action.RESERVE ? remaining : -remaining));
            if (action == Action.DELIVER) row.setQuantity(row.getQuantity() - remaining);
            row.setUpdatedAt(OffsetDateTime.now());
        }
        inventory.flush();
    }
}
