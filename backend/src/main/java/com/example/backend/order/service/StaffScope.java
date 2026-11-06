package com.example.backend.order.service;

import com.example.backend.order.entity.OrderStatus;
import java.util.function.Predicate;

/**
 * What the caller may do with an order.
 * <ul>
 *   <li>{@code targets}: statuses the caller's job title may set (see {@link OrderStaffPolicy}).</li>
 *   <li>{@code canRefund}: may cancel a PAID order (payment becomes REFUNDED).</li>
 *   <li>{@code deliveryOnly}: a delivery employee without wider rights; acts only on shipments assigned to them,
 *       and "cancel" for them means a failed delivery (SHIPPED → CANCELLED).</li>
 *   <li>{@code mayFailDelivery}: may record a failed delivery at all.</li>
 * </ul>
 */
public record StaffScope(Predicate<OrderStatus> targets, boolean canRefund, boolean deliveryOnly, boolean mayFailDelivery) {
    /** Customers and internal callers: the transition table alone decides. */
    public static final StaffScope ANY = new StaffScope(status -> true, false, false, true);

    public boolean permits(OrderStatus from, OrderStatus to) {
        if (!targets.test(to)) return false;
        if (to == OrderStatus.CANCELLED && from == OrderStatus.SHIPPED) return mayFailDelivery;
        return !(deliveryOnly && to == OrderStatus.CANCELLED);
    }
}
