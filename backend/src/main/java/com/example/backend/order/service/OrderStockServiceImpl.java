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
    public void apply(Map<UUID, Integer> quantities, Action action) {
        for (var id : quantities.keySet().stream().sorted().toList()) {
            var rows = inventory.lockByVariantId(id); long available = 0;
            for (var row : rows) available += action == Action.RESERVE ? (long) row.getQuantity() - row.getReservedQuantity() : row.getReservedQuantity();
            int remaining = quantities.get(id);
            if (available < remaining) throw new CommerceException(409, action == Action.RESERVE ? "Không đủ stock cho variant " + id : "Reserved stock không đủ cho variant " + id);
            for (var row : rows) {
                int capacity = action == Action.RESERVE ? row.getQuantity() - row.getReservedQuantity() : row.getReservedQuantity();
                int delta = Math.min(remaining, capacity);
                if (delta > 0) {
                    row.setReservedQuantity(row.getReservedQuantity() + (action == Action.RESERVE ? delta : -delta));
                    if (action == Action.DELIVER) row.setQuantity(row.getQuantity() - delta);
                    row.setUpdatedAt(OffsetDateTime.now()); remaining -= delta;
                }
                if (remaining == 0) break;
            }
        }
        inventory.flush();
    }
}
