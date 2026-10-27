package com.example.backend.order.service;
import com.example.backend.order.entity.OrderStatus;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Target statuses each staff job title may set (report UC-06 bán hàng, UC-SYS-03 xuất kho,
 * UC-SYS-09 giao hàng). ADMIN, MANAGER and the general STAFF role keep every transition.
 */
public final class OrderStaffPolicy {
    private static final Set<String> UNRESTRICTED = Set.of("ADMIN", "MANAGER", "STAFF");
    private static final Map<String, Set<OrderStatus>> BY_ROLE = Map.of(
            "SALES_STAFF", EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            "WAREHOUSE_KEEPER", EnumSet.of(OrderStatus.PROCESSING),
            "DELIVERY_STAFF", EnumSet.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    public static final Predicate<OrderStatus> ANY = status -> true;
    private OrderStaffPolicy() {}
    public static Predicate<OrderStatus> permitted(Collection<String> authorities) {
        if (authorities.stream().anyMatch(UNRESTRICTED::contains)) return ANY;
        Set<OrderStatus> targets = EnumSet.noneOf(OrderStatus.class);
        authorities.forEach(authority -> targets.addAll(BY_ROLE.getOrDefault(authority, Set.of())));
        return targets::contains;
    }
}
