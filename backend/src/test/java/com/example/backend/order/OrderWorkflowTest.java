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
    @Test void stateMachineAllowsOnlyAdjacentStatesAndEarlyCancellation() {
        assertThat(OrderTransitions.allowed(OrderStatus.PENDING, false, false)).containsExactly(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
        assertThat(OrderTransitions.allowed(OrderStatus.CONFIRMED, false, false)).containsExactly(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
        assertThat(OrderTransitions.allowed(OrderStatus.PROCESSING, false, false)).containsExactly(OrderStatus.SHIPPED);
        assertThat(OrderTransitions.allowed(OrderStatus.SHIPPED, false, false)).containsExactly(OrderStatus.DELIVERED);
        for (var current : OrderStatus.values()) for (var next : OrderStatus.values()) {
            if (!OrderTransitions.allowed(current, false, false).contains(next))
                assertThatThrownBy(() -> OrderTransitions.require(current, next, false, false)).isInstanceOf(CommerceException.class);
        }
        assertThat(OrderTransitions.allowed(OrderStatus.DELIVERED, false, false)).isEmpty();
        assertThat(OrderTransitions.allowed(OrderStatus.CANCELLED, false, false)).isEmpty();
    }
    @Test void customerCanOnlyCancelPendingUnpaidOrder() {
        assertThat(OrderTransitions.allowed(OrderStatus.PENDING, true, false)).containsExactly(OrderStatus.CANCELLED);
        assertThat(OrderTransitions.allowed(OrderStatus.CONFIRMED, true, false)).isEmpty();
        assertThatThrownBy(() -> OrderTransitions.require(OrderStatus.PENDING, OrderStatus.CONFIRMED, true, false)).isInstanceOf(CommerceException.class);
        assertThatThrownBy(() -> OrderTransitions.require(OrderStatus.PENDING, OrderStatus.CANCELLED, true, true)).hasMessageContaining("PAID");
    }
    @Test void stockReservesInSelectedWarehouseReleasesAndDeductsOnDelivery() {
        UUID id = UUID.randomUUID(); UUID warehouseId = UUID.randomUUID();
        var repository = mock(InventoryRepository.class); var service = new OrderStockServiceImpl(repository);
        var row = stock(9, 2);
        when(repository.lockByWarehouseAndVariant(warehouseId, id)).thenReturn(Optional.of(row));
        service.apply(warehouseId, Map.of(id, 6), OrderStockService.Action.RESERVE);
        assertThat(row.getReservedQuantity()).isEqualTo(8);
        assertThat(row.getQuantity()).isEqualTo(9);
        service.apply(warehouseId, Map.of(id, 6), OrderStockService.Action.RELEASE);
        assertThat(row.getReservedQuantity()).isEqualTo(2);
        service.apply(warehouseId, Map.of(id, 6), OrderStockService.Action.RESERVE);
        service.apply(warehouseId, Map.of(id, 6), OrderStockService.Action.DELIVER);
        assertThat(row.getQuantity()).isEqualTo(3);
        assertThat(row.getReservedQuantity()).isEqualTo(2);
        verify(repository, times(4)).lockByWarehouseAndVariant(warehouseId, id);
    }
    @Test void shortageFailsBeforeMutatingInventoryForThatVariant() {
        UUID id = UUID.randomUUID(); UUID warehouseId = UUID.randomUUID(); var repository = mock(InventoryRepository.class); var row = stock(5, 4);
        when(repository.lockByWarehouseAndVariant(warehouseId, id)).thenReturn(Optional.of(row));
        assertThatThrownBy(() -> new OrderStockServiceImpl(repository).apply(warehouseId, Map.of(id, 2), OrderStockService.Action.RESERVE)).hasMessageContaining("Không đủ stock");
        assertThat(row.getReservedQuantity()).isEqualTo(4); verify(repository, never()).flush();
    }
    @Test void shippingFieldsAreRequiredAndTrimmed() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new CheckoutRequest(); request.setPaymentMethod(PaymentMethod.COD);
            assertThat(factory.getValidator().validate(request)).hasSize(3);
            request.setRecipientName("  Khách hàng thử  "); request.setRecipientPhone("  0901234567  "); request.setShippingAddress("  TP. Hồ Chí Minh  ");
            assertThat(factory.getValidator().validate(request)).isEmpty(); assertThat(request.getRecipientName()).isEqualTo("Khách hàng thử");
            request.setRecipientPhone("1".repeat(31)); assertThat(factory.getValidator().validate(request)).hasSize(1);
        }
    }
    private Inventory stock(int quantity, int reserved) { var row = new Inventory(); row.setQuantity(quantity); row.setReservedQuantity(reserved); return row; }
}
