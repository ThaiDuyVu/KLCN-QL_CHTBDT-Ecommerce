package com.example.backend.order.service;
import com.example.backend.order.entity.*;
import com.example.backend.order.exception.CommerceException;
import java.util.List;
public final class OrderTransitions {
    private OrderTransitions() {}
    public static List<OrderStatus> allowed(OrderStatus current, boolean customer, boolean paid) {
        return allowed(current, customer, paid, false);
    }
    /**
     * @param canRefund the caller may cancel an already PAID order (ADMIN/MANAGER); the payment then becomes REFUNDED.
     */
    public static List<OrderStatus> allowed(OrderStatus current, boolean customer, boolean paid, boolean canRefund) {
        if (customer) return current == OrderStatus.PENDING && !paid ? List.of(OrderStatus.CANCELLED) : List.of();
        boolean mayCancel = !paid || canRefund;
        return switch(current) {
            case PENDING -> mayCancel ? List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED) : List.of(OrderStatus.CONFIRMED);
            case CONFIRMED -> mayCancel ? List.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED) : List.of(OrderStatus.PROCESSING);
            case PROCESSING -> List.of(OrderStatus.SHIPPED);
            // Cancelling a shipped order records a failed delivery; the goods return to the branch.
            case SHIPPED -> mayCancel ? List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED) : List.of(OrderStatus.DELIVERED);
            default -> List.of();
        };
    }
    public static void require(OrderStatus current, OrderStatus next, boolean customer, boolean paid) {
        require(current, next, customer, paid, false);
    }
    public static void require(OrderStatus current, OrderStatus next, boolean customer, boolean paid, boolean canRefund) {
        if (next == OrderStatus.CANCELLED && paid && (customer || !canRefund)) {
            throw new CommerceException(409, "Payment đã PAID; chỉ Quản lý hoặc Admin được hủy đơn và hoàn tiền");
        }
        if (!allowed(current, customer, paid, canRefund).contains(next)) throw new CommerceException(409, "Không thể chuyển " + current + " sang " + next);
    }
}
