package com.example.backend.order;

import com.example.backend.order.service.*;
import com.example.backend.order.entity.*;
import com.example.backend.order.exception.CommerceException;
import com.example.backend.inventory.entity.Inventory;
import com.example.backend.inventory.repository.InventoryRepository;
import com.example.backend.order.dto.CheckoutRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderWorkflowTest {

    @Test
    void stateMachineAllowsOnlyAdjacentStatesAndEarlyCancellation() {
        assertThat(OrderTransitions.allowed(OrderStatus.PENDING, false, false)).containsExactly(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
        assertThat(OrderTransitions.allowed(OrderStatus.CONFIRMED, false, false)).containsExactly(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
        assertThat(OrderTransitions.allowed(OrderStatus.PROCESSING, false, false)).containsExactly(OrderStatus.SHIPPED);
        assertThat(OrderTransitions.allowed(OrderStatus.SHIPPED, false, false)).containsExactly(OrderStatus.DELIVERED);

        for (var current : OrderStatus.values()) {
            for (var next : OrderStatus.values()) {
                if (!OrderTransitions.allowed(current, false, false).contains(next))
                    assertThatThrownBy(() -> OrderTransitions.require(current, next, false, false)).isInstanceOf(CommerceException.class);
            }
        }
        assertThat(OrderTransitions.allowed(OrderStatus.DELIVERED, false, false)).isEmpty();
        assertThat(OrderTransitions.allowed(OrderStatus.CANCELLED, false, false)).isEmpty();
    }

    @Test
    void customerCanOnlyCancelPendingUnpaidOrder() {
        assertThat(OrderTransitions.allowed(OrderStatus.PENDING, true, false)).containsExactly(OrderStatus.CANCELLED);
        assertThat(OrderTransitions.allowed(OrderStatus.CONFIRMED, true, false)).isEmpty();

        assertThatThrownBy(() -> OrderTransitions.require(OrderStatus.PENDING, OrderStatus.CONFIRMED, true, false)).isInstanceOf(CommerceException.class);
        assertThatThrownBy(() -> OrderTransitions.require(OrderStatus.PENDING, OrderStatus.CANCELLED, true, true)).hasMessageContaining("PAID");
    }

    @Test
    void stockReservesAcrossWarehousesReleasesAndDeductsOnDelivery() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        var repository = mock(InventoryRepository.class);
        var service = new OrderStockServiceImpl(repository);

        var first = stock(5, 2);
        // var second = stock(4, 0); // Logic kho đã đổi, thường 1 order chỉ trừ ở 1 warehouse

        // Mock hàm lockByWarehouseAndVariant
        when(repository.lockByWarehouseAndVariant(any(UUID.class), eq(id)))
                .thenReturn(Optional.of(first));

        service.apply(orderId, Map.of(id, 3), OrderStockService.Action.RESERVE);
        assertThat(first.getReservedQuantity()).isEqualTo(5); // 2 cũ + 3 mới
        assertThat(first.getQuantity()).isEqualTo(5);

        service.apply(orderId, Map.of(id, 3), OrderStockService.Action.RELEASE);
        assertThat(first.getReservedQuantity()).isEqualTo(2); // Trả lại 2

        service.apply(orderId, Map.of(id, 3), OrderStockService.Action.RESERVE);
        service.apply(orderId, Map.of(id, 3), OrderStockService.Action.DELIVER);
        
        assertThat(first.getQuantity()).isEqualTo(2); // 5 - 3
        assertThat(first.getReservedQuantity()).isEqualTo(2); // 5 - 3
    }

    @Test
    void shortageFailsBeforeMutatingInventoryForThatVariant() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        var repository = mock(InventoryRepository.class);
        var row = stock(5, 4);

        when(repository.lockByWarehouseAndVariant(any(UUID.class), eq(id)))
                .thenReturn(Optional.of(row));

        assertThatThrownBy(() -> new OrderStockServiceImpl(repository).apply(orderId, Map.of(id, 2), OrderStockService.Action.RESERVE))
                .hasMessageContaining("Không có tồn kho cho variant")
                .hasMessageContaining(id.toString());
        
        assertThat(row.getReservedQuantity()).isEqualTo(4);
        verify(repository, never()).flush();
    }

    private Inventory stock(int quantity, int reserved) {
        var row = new Inventory();
        row.setQuantity(quantity);
        row.setReservedQuantity(reserved);
        return row;
    }
}