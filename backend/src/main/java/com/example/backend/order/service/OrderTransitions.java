package com.example.backend.order.service;
import com.example.backend.order.entity.*;
import com.example.backend.order.exception.CommerceException;
import java.util.List;
public final class OrderTransitions {
    private OrderTransitions() {}
    public static List<OrderStatus> allowed(OrderStatus current, boolean customer, boolean paid) {
        if (customer) return current == OrderStatus.PENDING && !paid ? List.of(OrderStatus.CANCELLED) : List.of();
        return switch(current) {
            case PENDING -> paid ? List.of(OrderStatus.CONFIRMED) : List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
            case CONFIRMED -> paid ? List.of(OrderStatus.PROCESSING) : List.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
            case PROCESSING -> List.of(OrderStatus.SHIPPED);
            case SHIPPED -> List.of(OrderStatus.DELIVERED);
            default -> List.of();
        };
    }
    public static void require(OrderStatus current, OrderStatus next, boolean customer, boolean paid) {
        if (next == OrderStatus.CANCELLED && paid) throw new CommerceException(409, "Payment đã PAID; cần refund flow");
        if (!allowed(current, customer, paid).contains(next)) throw new CommerceException(409, "Không thể chuyển " + current + " sang " + next);
    }
}
