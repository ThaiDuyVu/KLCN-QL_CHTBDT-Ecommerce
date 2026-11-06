package com.example.backend.order.service;
import com.example.backend.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStaffPolicyTest {
    private static List<OrderStatus> permitted(String... authorities) {
        var policy = OrderStaffPolicy.permitted(List.of(authorities));
        return Arrays.stream(OrderStatus.values()).filter(policy).toList();
    }
    @Test void generalRolesKeepEveryTransition() {
        assertThat(permitted("STAFF")).containsExactly(OrderStatus.values());
        assertThat(permitted("MANAGER", "CUSTOMER_VIEW")).containsExactly(OrderStatus.values());
    }
    @Test void jobTitlesAreLimitedToTheirSteps() {
        assertThat(permitted("SALES_STAFF", "CUSTOMER_VIEW")).containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.CANCELLED);
        assertThat(permitted("WAREHOUSE_KEEPER")).containsExactly(OrderStatus.PROCESSING);
        assertThat(permitted("DELIVERY_STAFF")).containsExactlyInAnyOrder(OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED);
    }
    @Test void deliveryStaffCancelOnlyMeansAFailedDeliveryAndSalesCannotFailDeliveries() {
        var delivery = OrderStaffPolicy.scope(java.util.List.of("DELIVERY_STAFF"));
        assertThat(delivery.deliveryOnly()).isTrue();
        assertThat(delivery.permits(OrderStatus.SHIPPED, OrderStatus.CANCELLED)).isTrue();
        assertThat(delivery.permits(OrderStatus.PENDING, OrderStatus.CANCELLED)).isFalse();
        var sales = OrderStaffPolicy.scope(java.util.List.of("SALES_STAFF"));
        assertThat(sales.permits(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)).isTrue();
        assertThat(sales.permits(OrderStatus.SHIPPED, OrderStatus.CANCELLED)).isFalse();
        var manager = OrderStaffPolicy.scope(java.util.List.of("MANAGER"));
        assertThat(manager.deliveryOnly()).isFalse();
        assertThat(manager.permits(OrderStatus.SHIPPED, OrderStatus.CANCELLED)).isTrue();
    }
    @Test void rolesWithoutOrderStepsGetNothing() {
        assertThat(permitted("WARRANTY_TECHNICIAN")).isEmpty();
        assertThat(permitted("CUSTOMER_VIEW")).isEmpty();
    }
}
