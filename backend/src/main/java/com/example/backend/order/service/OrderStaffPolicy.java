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
            // CANCELLED for delivery staff only means "delivery failed" (SHIPPED → CANCELLED), see StaffScope.
            "DELIVERY_STAFF", EnumSet.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED));
    public static final Predicate<OrderStatus> ANY = status -> true;
    private OrderStaffPolicy() {}
    /** Only store management may cancel an already PAID order, which turns its payment into REFUNDED. */
    public static boolean canRefund(Collection<String> authorities) {
        return authorities.stream().anyMatch(authority -> authority.equals("ADMIN") || authority.equals("MANAGER"));
    }
    public static StaffScope scope(Collection<String> authorities) {
        boolean unrestricted = authorities.stream().anyMatch(UNRESTRICTED::contains);
        boolean delivery = authorities.contains("DELIVERY_STAFF");
        return new StaffScope(permitted(authorities), canRefund(authorities), delivery && !unrestricted, delivery || unrestricted);
    }
    public static Predicate<OrderStatus> permitted(Collection<String> authorities) {
        if (authorities.stream().anyMatch(UNRESTRICTED::contains)) return ANY;
        Set<OrderStatus> targets = EnumSet.noneOf(OrderStatus.class);
        authorities.forEach(authority -> targets.addAll(BY_ROLE.getOrDefault(authority, Set.of())));
        return targets::contains;
    }
}
