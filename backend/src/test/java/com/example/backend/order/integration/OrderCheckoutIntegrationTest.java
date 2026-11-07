package com.example.backend.order.integration;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.cart.dto.*;
import com.example.backend.cart.service.CartService;
import com.example.backend.order.dto.*;
import com.example.backend.order.entity.*;
import com.example.backend.order.service.OrderService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false", "app.seed.auth.enabled=false", "app.payment.vnpay.enabled=false"})
@Testcontainers(disabledWithoutDocker=true)
@AutoConfigureMockMvc
class OrderCheckoutIntegrationTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("checkout_test").withUsername("checkout_test").withPassword("checkout_test");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl); registry.add("spring.datasource.username", database::getUsername); registry.add("spring.datasource.password", database::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-checkout-jwt-secret-value-long-enough");
    }
    @Autowired CartService cart;
    @Autowired OrderService orders;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    UUID firstUser, secondUser, variant, warehouse;
    Map<UUID, UUID> addressIds;
    @BeforeEach void setup() {
        // Dedicated ephemeral container only: never touches application data.
        jdbc.execute("TRUNCATE TABLE invoices, shipments, orders, carts, customers, employees, inventory, products, brands, categories, warehouses CASCADE");
        addressIds = new HashMap<>();
        firstUser = customer(); secondUser = customer(); variant = UUID.randomUUID();
        warehouse = UUID.randomUUID();
        UUID category = UUID.randomUUID(), product = UUID.randomUUID(), brand = UUID.randomUUID();
        jdbc.update("INSERT INTO categories(category_id,category_name) VALUES (?,?)",category,"Danh mục thử");
        jdbc.update("INSERT INTO brands(brand_id,brand_name,status) VALUES (?, ?, 'ACTIVE')",brand,"Hãng thử");
        jdbc.update("INSERT INTO products(product_id,category_id,brand_id,product_name,status) VALUES (?,?,?,?,'ACTIVE')",product,category,brand,"Thiết bị thử");
        jdbc.update("INSERT INTO product_variants(variant_id,product_id,sku,price,cost_price,status) VALUES (?,?,?,2000000,1500000,'ACTIVE')",variant,product,"SKU-"+variant);
        jdbc.update("INSERT INTO warehouses(warehouse_id,warehouse_name) VALUES (?,?)",warehouse,"Kho thử");
        jdbc.update("INSERT INTO inventory(warehouse_id,variant_id,quantity,reserved_quantity) VALUES (?,?,5,0)",warehouse,variant);
        // firstUser also acts as staff in these tests; SHIPPED assigns the delivery to the caller's employee profile.
        jdbc.update("INSERT INTO employees(user_id,employee_code,full_name) VALUES (?,?, 'Nhân viên giao hàng thử')", firstUser, "T-" + firstUser);
        var selection = new SelectCartWarehouseRequest(); selection.setWarehouseId(warehouse);
        cart.selectWarehouse(firstUser, selection); cart.selectWarehouse(secondUser, selection);
    }
    UUID customer() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(user_id,username,password,email,status) VALUES (?,?,?,?,'ACTIVE')",userId,"test-"+userId,"test-only-hash",userId+"@example.test");
        jdbc.update("INSERT INTO customers(user_id,full_name) VALUES (?,?)",userId,"Khách hàng thử");
        UUID addressId = UUID.randomUUID();
        jdbc.update("INSERT INTO customer_addresses(address_id,customer_id,recipient_name,recipient_phone,address_line,is_default) "
                + "SELECT ?, customer_id, 'Khách hàng thử', '0901234567', 'TP. Hồ Chí Minh', true FROM customers WHERE user_id=?",
                addressId, userId);
        addressIds.put(userId, addressId);
        return userId;
    }
    void add(UUID userId,int quantity) { var request = new CartItemRequest(); request.setVariantId(variant); request.setQuantity(quantity); cart.add(userId,request); }
    CheckoutRequest request() { return request(firstUser); }
    CheckoutRequest request(UUID userId) {
        var r=new CheckoutRequest(); r.setAddressId(addressIds.get(userId));r.setPaymentMethod(PaymentMethod.COD);return r;
    }
    int stock(String column) { return jdbc.queryForObject("SELECT "+column+" FROM inventory WHERE variant_id=?",Integer.class,variant); }
    @Test void cartAddChangeDeleteAndEmptyCheckout() {
        add(firstUser,1);add(firstUser,1); var c=cart.get(firstUser); assertThat(c.getItems()).hasSize(1); assertThat(c.getItems().getFirst().getQuantity()).isEqualTo(2);
        var q=new UpdateCartQuantityRequest();q.setQuantity(3);cart.quantity(firstUser,c.getItems().getFirst().getCartItemId(),q);
        assertThat(cart.get(firstUser).getSubtotal()).isEqualByComparingTo("6000000");
        assertThatThrownBy(() -> cart.remove(secondUser,c.getItems().getFirst().getCartItemId())).hasMessageContaining("Không tìm thấy");
        cart.remove(firstUser,c.getItems().getFirst().getCartItemId()); assertThat(cart.get(firstUser).getItems()).isEmpty();
        assertThatThrownBy(() -> orders.checkout(firstUser,request())).hasMessageContaining("rỗng");
    }
    @Test void checkoutSnapshotsCurrentPricesOneCodPaymentAndClearsCartThenCancelReleasesStock() {
        add(firstUser,2);
        jdbc.update("UPDATE product_variants SET price=2100000,cost_price=1600000 WHERE variant_id=?",variant);
        var order=orders.checkout(firstUser,request()); assertThat(order.getSubtotal()).isEqualByComparingTo("4200000");
        assertThat(order.getTotalAmount()).isEqualByComparingTo(order.getSubtotal());assertThat(order.getDiscountAmount()).isZero();assertThat(order.getShippingFee()).isZero();
        assertThat(order.getPayment().getPaymentMethod()).isEqualTo(PaymentMethod.COD);assertThat(order.getPayment().getStatus()).isEqualTo(PaymentStatus.PENDING);assertThat(order.getPayment().getTransactionCode()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id=?",Integer.class,order.getOrderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cost_price FROM order_items WHERE order_id=?",BigDecimal.class,order.getOrderId())).isEqualByComparingTo("1600000");
        assertThat(stock("reserved_quantity")).isEqualTo(2);assertThat(stock("quantity")).isEqualTo(5);assertThat(cart.get(firstUser).getItems()).isEmpty();
        jdbc.update("UPDATE product_variants SET price=1 WHERE variant_id=?",variant);
        assertThat(orders.detail(firstUser,true,order.getOrderId()).getItems().getFirst().getFinalUnitPrice()).isEqualByComparingTo("2100000");
        assertThatThrownBy(() -> orders.detail(secondUser,true,order.getOrderId())).hasMessageContaining("Không tìm thấy");
        orders.status(firstUser,true,order.getOrderId(),OrderStatus.CANCELLED);assertThat(stock("reserved_quantity")).isZero();assertThat(stock("quantity")).isEqualTo(5);
        assertThatThrownBy(() -> orders.status(firstUser,true,order.getOrderId(),OrderStatus.CANCELLED)).hasMessageContaining("Không thể");
    }
    @Test void validStaffTransitionsDeductOnlyOnDeliveryAndPaidCancellationFails() {
        add(firstUser,2);var order=orders.checkout(firstUser,request());
        assertThatThrownBy(() -> orders.status(firstUser,false,order.getOrderId(),OrderStatus.DELIVERED)).hasMessageContaining("Không thể");
        assertThatThrownBy(() -> orders.status(firstUser,true,order.getOrderId(),OrderStatus.CONFIRMED)).hasMessageContaining("Không thể");
        jdbc.update("UPDATE payments SET status='PAID' WHERE order_id=?",order.getOrderId());
        assertThatThrownBy(() -> orders.status(firstUser,true,order.getOrderId(),OrderStatus.CANCELLED)).hasMessageContaining("PAID");assertThat(stock("reserved_quantity")).isEqualTo(2);
        assertThatThrownBy(() -> orders.status(firstUser,false,order.getOrderId(),OrderStatus.CANCELLED)).hasMessageContaining("PAID");
        orders.status(firstUser,false,order.getOrderId(),OrderStatus.CONFIRMED);orders.status(firstUser,false,order.getOrderId(),OrderStatus.PROCESSING);orders.status(firstUser,false,order.getOrderId(),OrderStatus.SHIPPED);
        assertThat(stock("quantity")).isEqualTo(5);assertThatThrownBy(() -> orders.status(firstUser,false,order.getOrderId(),OrderStatus.CANCELLED)).isInstanceOf(RuntimeException.class);
        orders.status(firstUser,false,order.getOrderId(),OrderStatus.DELIVERED);assertThat(stock("quantity")).isEqualTo(3);assertThat(stock("reserved_quantity")).isZero();
        assertThatThrownBy(() -> orders.status(firstUser,false,order.getOrderId(),OrderStatus.DELIVERED)).hasMessageContaining("Không thể");
    }
    @Test void concurrentCheckoutDoesNotOversellAndFailedCheckoutPreservesCart() throws Exception {
        add(firstUser,4);add(secondUser,4);var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var attempts=List.of(firstUser,secondUser).stream().map(id -> executor.submit(() -> {start.await();try {orders.checkout(id,request(id));return true;}catch(com.example.backend.order.exception.CommerceException e){return false;}})).toList();
            start.countDown();int success=0;for(var attempt:attempts)if(attempt.get(20,TimeUnit.SECONDS))success++;
            assertThat(success).isEqualTo(1);
        }
        assertThat(stock("quantity")).isEqualTo(5);assertThat(stock("reserved_quantity")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items",Integer.class)).isEqualTo(1);
    }
    @Test void sameCustomerConcurrentCheckoutCreatesOnlyOneOrder() throws Exception {
        add(firstUser,2);var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> attempts=new ArrayList<>();
            for(int i=0;i<2;i++) attempts.add(executor.submit(() -> {start.await();try {orders.checkout(firstUser,request());return true;}catch(com.example.backend.order.exception.CommerceException e){return false;}}));
            start.countDown();int success=0;for(var attempt:attempts)if(attempt.get(20,TimeUnit.SECONDS))success++;
            assertThat(success).isEqualTo(1);
        }
        assertThat(stock("reserved_quantity")).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)).isEqualTo(1);
    }
    @Test void shortageOnSecondVariantRollsBackEarlierReservationAndKeepsCart() {
        UUID second=UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        jdbc.update("INSERT INTO product_variants(variant_id,product_id,sku,price,cost_price,status) SELECT ?,product_id,?,100,50,'ACTIVE' FROM product_variants WHERE variant_id=?",second,"SECOND",variant);
        // Java UUID sorting is signed; MAX signed UUID ensures original variant is processed first.
        UUID last=UUID.fromString("7fffffff-ffff-ffff-7fff-ffffffffffff");
        jdbc.update("UPDATE product_variants SET variant_id=? WHERE variant_id=?",last,second);
        jdbc.update("INSERT INTO inventory(warehouse_id,variant_id,quantity,reserved_quantity) VALUES (?,?,1,0)",warehouse,last);
        add(firstUser,2);var r=new CartItemRequest();r.setVariantId(last);r.setQuantity(1);cart.add(firstUser,r);
        jdbc.update("UPDATE inventory SET quantity=0 WHERE variant_id=?",last);
        assertThatThrownBy(() -> orders.checkout(firstUser,request())).hasMessageContaining("Không đủ stock");
        assertThat(stock("reserved_quantity")).isZero();assertThat(cart.get(firstUser).getItems()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)).isZero();
    }
    @Test void staffCancellationAfterConfirmationReleasesReservations() {
        add(firstUser,2);var order=orders.checkout(firstUser,request());orders.status(firstUser,false,order.getOrderId(),OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> orders.status(firstUser,true,order.getOrderId(),OrderStatus.CANCELLED)).hasMessageContaining("Không thể");
        orders.status(firstUser,false,order.getOrderId(),OrderStatus.CANCELLED);assertThat(stock("reserved_quantity")).isZero();assertThat(stock("quantity")).isEqualTo(5);
    }
    @Test void migrationSeedsStaffJobRolesWithTheirPermissions() {
        assertThat(jdbc.queryForList("SELECT role_name FROM roles WHERE role_name IN ('SALES_STAFF','WAREHOUSE_KEEPER','WARRANTY_TECHNICIAN','DELIVERY_STAFF')", String.class))
                .containsExactlyInAnyOrder("SALES_STAFF","WAREHOUSE_KEEPER","WARRANTY_TECHNICIAN","DELIVERY_STAFF");
        assertThat(jdbc.queryForList("SELECT r.role_name FROM role_permissions rp JOIN roles r ON r.role_id=rp.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE p.permission_name='CUSTOMER_VIEW' AND r.role_name IN ('SALES_STAFF','WAREHOUSE_KEEPER','WARRANTY_TECHNICIAN','DELIVERY_STAFF')", String.class))
                .containsExactlyInAnyOrder("SALES_STAFF","WARRANTY_TECHNICIAN");
    }
    @Test void eachStaffJobTitleOnlyMovesTheOrderThroughItsOwnSteps() throws Exception {
        add(firstUser,1);UUID id=orders.checkout(firstUser,request()).getOrderId();
        mvc.perform(get("/api/orders/{id}",id).with(user(principal("DELIVERY_STAFF")))).andExpect(status().isOk()).andExpect(jsonPath("$.allowedStatuses").isEmpty());
        mvc.perform(get("/api/orders/{id}",id).with(user(principal("SALES_STAFF")))).andExpect(jsonPath("$.allowedStatuses[0]").value("CONFIRMED"));
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("DELIVERY_STAFF"))).with(csrf()).contentType("application/json").content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("SALES_STAFF"))).with(csrf()).contentType("application/json").content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("WAREHOUSE_KEEPER"))).with(csrf()).contentType("application/json").content("{\"status\":\"PROCESSING\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("SALES_STAFF"))).with(csrf()).contentType("application/json").content("{\"status\":\"SHIPPED\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("DELIVERY_STAFF"))).with(csrf()).contentType("application/json").content("{\"status\":\"SHIPPED\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("DELIVERY_STAFF"))).with(csrf()).contentType("application/json").content("{\"status\":\"DELIVERED\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/orders").with(user(principal("WARRANTY_TECHNICIAN")))).andExpect(status().isForbidden());
        assertThat(stock("quantity")).isEqualTo(4);assertThat(stock("reserved_quantity")).isZero();
    }
    AuthenticatedUserPrincipal principal(String role) {var u=new User();u.setUserId(firstUser);u.setUsername("test");u.setStatus("ACTIVE");return new AuthenticatedUserPrincipal(u,role);}
    @Test void httpCustomerCannotAdministrateAndServerIgnoresClientPrices() throws Exception {
        add(firstUser,1);
        mvc.perform(post("/api/orders/checkout").with(user(principal("CUSTOMER"))).with(csrf()).contentType("application/json")
                .content("{\"addressId\":\""+addressIds.get(firstUser)+"\",\"paymentMethod\":\"COD\",\"totalAmount\":1,\"unitPrice\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalAmount").value(2000000));
        UUID id=jdbc.queryForObject("SELECT order_id FROM orders",UUID.class);
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("CUSTOMER"))).with(csrf()).contentType("application/json").content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders").with(user(principal("CUSTOMER")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/cart").with(user(principal("STAFF")))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("STAFF"))).with(csrf()).contentType("application/json").content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
    }
    @Test void missingAddressAndDisabledVnpayRejectCheckout() throws Exception {
        add(firstUser,1);
        mvc.perform(post("/api/orders/checkout").with(user(principal("CUSTOMER"))).with(csrf()).contentType("application/json")
                .content("{\"recipientName\":\"Khách thử\",\"recipientPhone\":\"0901234567\",\"paymentMethod\":\"COD\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.message").value("Vui lòng chọn địa chỉ giao hàng"));
        jdbc.update("UPDATE inventory SET quantity=0 WHERE variant_id=?", variant);
        mvc.perform(post("/api/orders/checkout").with(user(principal("CUSTOMER"))).with(csrf()).contentType("application/json")
                .content("{\"addressId\":\""+addressIds.get(firstUser)+"\",\"paymentMethod\":\"COD\"}"))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Không đủ stock")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        var r=request();r.setPaymentMethod(PaymentMethod.VNPAY);assertThatThrownBy(() -> orders.checkout(firstUser,r)).hasMessageContaining("VNPAY Sandbox chưa được cấu hình hoặc đang tắt");assertThat(stock("reserved_quantity")).isZero();
    }
    @Test void checkoutStillRequiresSelectedWarehouseBeforeAnyFinancialMutation() {
        add(firstUser,1);
        jdbc.update("UPDATE carts SET warehouse_id=NULL WHERE customer_id=(SELECT customer_id FROM customers WHERE user_id=?)", firstUser);
        assertThatThrownBy(() -> orders.checkout(firstUser,request())).hasMessageContaining("chọn chi nhánh");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments",Integer.class)).isZero();
        assertThat(stock("reserved_quantity")).isZero();
        assertThat(cart.get(firstUser).getItems()).hasSize(1);
    }
    String paymentStatus(UUID orderId) { return jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,orderId); }
    void deliver(UUID orderId) {
        for (var next : List.of(OrderStatus.CONFIRMED,OrderStatus.PROCESSING,OrderStatus.SHIPPED)) {
            orders.status(firstUser,false,orderId,next);
            assertThat(paymentStatus(orderId)).as("payment before delivery, after "+next).isEqualTo("PENDING");
        }
        orders.status(firstUser,false,orderId,OrderStatus.DELIVERED);
    }
    @Test void codPaymentIsSettledOnlyWhenTheOrderIsDelivered() {
        add(firstUser,1);var order=orders.checkout(firstUser,request());
        var created=jdbc.queryForObject("SELECT payment_date FROM payments WHERE order_id=?",java.time.OffsetDateTime.class,order.getOrderId());
        deliver(order.getOrderId());
        assertThat(paymentStatus(order.getOrderId())).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT payment_date FROM payments WHERE order_id=?",java.time.OffsetDateTime.class,order.getOrderId())).isAfterOrEqualTo(created);
        assertThat(orders.detail(firstUser,true,order.getOrderId()).getPayment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id=?",Integer.class,order.getOrderId())).isEqualTo(1);
    }
    @Test void cancelledCodOrderKeepsItsPaymentPending() {
        add(firstUser,1);var order=orders.checkout(firstUser,request());
        orders.status(firstUser,false,order.getOrderId(),OrderStatus.CONFIRMED);orders.status(firstUser,false,order.getOrderId(),OrderStatus.CANCELLED);
        assertThat(paymentStatus(order.getOrderId())).isEqualTo("PENDING");
    }
    @Test void approvedInstallmentPaymentIsSettledOnDelivery() {
        add(firstUser,1);
        UUID provider=UUID.randomUUID();
        jdbc.update("INSERT INTO installment_providers(provider_id,provider_code,provider_name,status) VALUES (?,?,?,'ACTIVE')", provider,"TEST-"+provider,"Đơn vị trả góp kiểm thử");
        var r=request();r.setPaymentMethod(PaymentMethod.INSTALLMENT);r.setProviderId(provider);r.setTermMonths(6);r.setDownPayment(new BigDecimal("100000"));
        var order=orders.checkout(firstUser,r);
        jdbc.update("UPDATE installment_payments SET status='APPROVED' WHERE payment_id=?",order.getPayment().getPaymentId());
        deliver(order.getOrderId());
        assertThat(paymentStatus(order.getOrderId())).isEqualTo("PAID");
    }
    @Autowired com.example.backend.order.scheduling.VnpayExpiryJob expiryJob;
    /** VNPAY is disabled in tests, so a COD checkout is turned into a VNPAY payment started `minutesAgo`. */
    UUID vnpayOrderStarted(int minutesAgo) {
        add(firstUser,2);var order=orders.checkout(firstUser,request());
        jdbc.update("UPDATE payments SET payment_method='VNPAY', payment_date=now() - make_interval(mins => ?) WHERE order_id=?",minutesAgo,order.getOrderId());
        return order.getOrderId();
    }
    String orderStatus(UUID orderId) { return jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,orderId); }
    @Test void expiredUnpaidVnpayOrderIsCancelledAndReleasesStock() {
        UUID id=vnpayOrderStarted(30);
        assertThat(stock("reserved_quantity")).isEqualTo(2);
        assertThat(expiryJob.expireNow()).isEqualTo(1);
        assertThat(orderStatus(id)).isEqualTo("CANCELLED");
        assertThat(paymentStatus(id)).isEqualTo("FAILED");
        assertThat(stock("reserved_quantity")).isZero();assertThat(stock("quantity")).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM orders WHERE order_id=?",String.class,id)).contains("quá hạn thanh toán VNPAY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_history WHERE order_id=? AND to_status='CANCELLED' AND changed_by_user_id IS NULL",Integer.class,id)).isEqualTo(1);
        assertThat(expiryJob.expireNow()).as("second run is a no-op").isZero();
    }
    @Test void vnpayOrderInsideItsSessionOrUnderReviewIsNotExpired() {
        UUID fresh=vnpayOrderStarted(10);
        assertThat(expiryJob.expireNow()).isZero();
        assertThat(orderStatus(fresh)).isEqualTo("PENDING");assertThat(stock("reserved_quantity")).isEqualTo(2);
        jdbc.update("UPDATE payments SET payment_date=now() - interval '30 minutes', transaction_code='VNPAY-123' WHERE order_id=?",fresh);
        assertThat(expiryJob.expireNow()).as("VNPAY response 07 needs manual review").isZero();
        assertThat(orderStatus(fresh)).isEqualTo("PENDING");
    }
    @Test void oldCodOrderIsNeverExpired() {
        add(firstUser,1);var order=orders.checkout(firstUser,request());
        jdbc.update("UPDATE payments SET payment_date=now() - interval '2 days' WHERE order_id=?",order.getOrderId());
        assertThat(expiryJob.expireNow()).isZero();
        assertThat(orderStatus(order.getOrderId())).isEqualTo("PENDING");
    }
    @Test void onlyManagementCancelsAPaidOrderWithAReasonAndThePaymentBecomesRefunded() throws Exception {
        add(firstUser,2);UUID id=orders.checkout(firstUser,request()).getOrderId();
        jdbc.update("UPDATE payments SET status='PAID' WHERE order_id=?",id);
        mvc.perform(get("/api/orders/{id}",id).with(user(principal("SALES_STAFF")))).andExpect(jsonPath("$.allowedStatuses").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CANCELLED"))));
        mvc.perform(get("/api/orders/{id}",id).with(user(principal("MANAGER")))).andExpect(jsonPath("$.allowedStatuses").value(org.hamcrest.Matchers.hasItem("CANCELLED")));
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("SALES_STAFF"))).with(csrf()).contentType("application/json")
                .content("{\"status\":\"CANCELLED\",\"reason\":\"Khách đổi ý\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("MANAGER"))).with(csrf()).contentType("application/json")
                .content("{\"status\":\"CANCELLED\",\"reason\":\"  \"}")).andExpect(status().isBadRequest());
        assertThat(orderStatus(id)).isEqualTo("PENDING");assertThat(stock("reserved_quantity")).isEqualTo(2);
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("MANAGER"))).with(csrf()).contentType("application/json")
                .content("{\"status\":\"CANCELLED\",\"reason\":\"Hết hàng tại chi nhánh\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.status").value("REFUNDED"))
                .andExpect(jsonPath("$.cancelReason").value("Hủy và hoàn tiền: Hết hàng tại chi nhánh")).andExpect(jsonPath("$.note").doesNotExist());
        assertThat(paymentStatus(id)).isEqualTo("REFUNDED");
        assertThat(stock("reserved_quantity")).isZero();assertThat(stock("quantity")).isEqualTo(5);
    }
    @Test void paidOrderCannotBeCancelledOnceItLeftTheWarehouse() throws Exception {
        add(firstUser,1);UUID id=orders.checkout(firstUser,request()).getOrderId();
        jdbc.update("UPDATE payments SET status='PAID' WHERE order_id=?",id);
        orders.status(firstUser,false,id,OrderStatus.CONFIRMED);orders.status(firstUser,false,id,OrderStatus.PROCESSING);
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("ADMIN"))).with(csrf()).contentType("application/json")
                .content("{\"status\":\"CANCELLED\",\"reason\":\"Thử\"}")).andExpect(status().isConflict());
        assertThat(paymentStatus(id)).isEqualTo("PAID");
    }
    // ---- P1.1 shipments ----
    record Staff(UUID userId, UUID employeeId) {}
    Staff deliveryStaff(String name, String status) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(user_id,username,password,email,status) VALUES (?,?,?,?,?)", userId, "gh-" + userId, "test-only-hash", userId + "@example.test", status);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?, role_id FROM roles WHERE role_name='DELIVERY_STAFF'", userId);
        UUID employeeId = jdbc.queryForObject("INSERT INTO employees(user_id,employee_code,full_name) VALUES (?,?,?) RETURNING employee_id",
                UUID.class, userId, "GH-" + userId, name);
        return new Staff(userId, employeeId);
    }
    AuthenticatedUserPrincipal principalFor(UUID userId, String role) {var u=new User();u.setUserId(userId);u.setUsername("test");u.setStatus("ACTIVE");return new AuthenticatedUserPrincipal(u,role);}
    org.springframework.test.web.servlet.ResultActions move(UUID id, AuthenticatedUserPrincipal actor, String body) throws Exception {
        return mvc.perform(patch("/api/orders/{id}/status",id).with(user(actor)).with(csrf()).contentType("application/json").content(body));
    }
    UUID processingOrder(int quantity) {
        add(firstUser,quantity);UUID id=orders.checkout(firstUser,request()).getOrderId();
        orders.status(firstUser,false,id,OrderStatus.CONFIRMED);orders.status(firstUser,false,id,OrderStatus.PROCESSING);
        return id;
    }
    @Test void managerHandsTheOrderToADeliveryEmployeeWhoClosesIt() throws Exception {
        Staff an = deliveryStaff("Phan Văn An", "ACTIVE"), binh = deliveryStaff("Lý Văn Bình", "ACTIVE");
        deliveryStaff("Nghỉ việc", "INACTIVE");
        mvc.perform(get("/api/orders/delivery-staff").with(user(principal("WAREHOUSE_KEEPER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].fullName").value("Lý Văn Bình"));
        UUID id = processingOrder(2);
        move(id, principal("MANAGER"), "{\"status\":\"SHIPPED\",\"deliveryEmployeeId\":\""+an.employeeId()+"\",\"shippingProvider\":\"Giao Hàng Nhanh\",\"trackingNumber\":\"ghn-123\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.status").value("SHIPPING"))
                .andExpect(jsonPath("$.shipment.employeeName").value("Phan Văn An"))
                .andExpect(jsonPath("$.shipment.shippingProvider").value("Giao Hàng Nhanh"))
                .andExpect(jsonPath("$.shipment.trackingNumber").value("GHN-123"))
                .andExpect(jsonPath("$.shipment.shippedDate").isNotEmpty());
        mvc.perform(get("/api/orders/deliveries").with(user(principalFor(an.userId(),"DELIVERY_STAFF"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].orderId").value(id.toString()))
                .andExpect(jsonPath("$.content[0].shipment.trackingNumber").value("GHN-123"));
        mvc.perform(get("/api/orders/deliveries").with(user(principalFor(binh.userId(),"DELIVERY_STAFF")))).andExpect(jsonPath("$.totalElements").value(0));
        move(id, principalFor(binh.userId(),"DELIVERY_STAFF"), "{\"status\":\"DELIVERED\"}").andExpect(status().isForbidden());
        move(id, principalFor(an.userId(),"DELIVERY_STAFF"), "{\"status\":\"DELIVERED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.shipment.status").value("DELIVERED"))
                .andExpect(jsonPath("$.shipment.deliveredDate").isNotEmpty()).andExpect(jsonPath("$.payment.status").value("PAID"));
        assertThat(stock("quantity")).isEqualTo(3);
    }
    @Test void shipmentNeedsAValidDeliveryEmployeeAndAUniqueTrackingNumber() throws Exception {
        Staff an = deliveryStaff("Phan Văn An", "ACTIVE");
        UUID first = processingOrder(1);
        UUID salesEmployee = jdbc.queryForObject("SELECT employee_id FROM employees WHERE user_id=?", UUID.class, firstUser);
        move(first, principal("MANAGER"), "{\"status\":\"SHIPPED\",\"deliveryEmployeeId\":\""+salesEmployee+"\"}").andExpect(status().isBadRequest());
        move(first, principal("MANAGER"), "{\"status\":\"SHIPPED\",\"deliveryEmployeeId\":\""+an.employeeId()+"\",\"trackingNumber\":\"VD-1\"}").andExpect(status().isOk());
        UUID second = processingOrder(1);
        move(second, principal("MANAGER"), "{\"status\":\"SHIPPED\",\"deliveryEmployeeId\":\""+an.employeeId()+"\",\"trackingNumber\":\"vd-1\"}").andExpect(status().isConflict());
        assertThat(orderStatus(second)).isEqualTo("PROCESSING");
        move(second, principalFor(an.userId(),"DELIVERY_STAFF"), "{\"status\":\"SHIPPED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.shipment.employeeName").value("Phan Văn An"))
                .andExpect(jsonPath("$.shipment.shippingProvider").value("Giao nội bộ"))
                .andExpect(jsonPath("$.shipment.trackingNumber").value(org.hamcrest.Matchers.startsWith("GH-")));
    }
    @Test void failedDeliveryCancelsTheOrderAndReturnsDevicesToSale() throws Exception {
        jdbc.update("UPDATE product_variants SET tracking_type='SERIAL' WHERE variant_id=?", variant);
        for (String serial : List.of("SN-A","SN-B","SN-C"))
            jdbc.update("INSERT INTO serial_numbers(variant_id,warehouse_id,serial_number,status) VALUES (?,?,?,'AVAILABLE')", variant, warehouse, serial);
        Staff an = deliveryStaff("Phan Văn An", "ACTIVE");
        UUID id = processingOrder(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM serial_numbers WHERE status='RESERVED'", Integer.class)).isEqualTo(2);
        move(id, principalFor(an.userId(),"DELIVERY_STAFF"), "{\"status\":\"SHIPPED\"}").andExpect(status().isOk());
        mvc.perform(get("/api/orders/{id}",id).with(user(principalFor(an.userId(),"DELIVERY_STAFF"))))
                .andExpect(jsonPath("$.allowedStatuses").value(org.hamcrest.Matchers.containsInAnyOrder("DELIVERED","CANCELLED")));
        move(id, principalFor(an.userId(),"DELIVERY_STAFF"), "{\"status\":\"CANCELLED\"}").andExpect(status().isBadRequest());
        move(id, principalFor(an.userId(),"DELIVERY_STAFF"), "{\"status\":\"CANCELLED\",\"reason\":\"Khách không nghe máy 3 lần\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.shipment.status").value("FAILED"))
                .andExpect(jsonPath("$.cancelReason").value("Giao không thành công: Khách không nghe máy 3 lần"));
        assertThat(stock("reserved_quantity")).isZero();assertThat(stock("quantity")).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM serial_numbers WHERE status='AVAILABLE'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id=? AND serial_id IS NOT NULL", Integer.class, id)).isZero();
        UUID next = processingOrder(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id=? AND serial_id IS NOT NULL", Integer.class, next))
                .as("returned devices can be sold again").isEqualTo(3);
    }
    @Test void deliveryStaffCannotCancelOrdersBeforeShippingNorFailAPaidDelivery() throws Exception {
        Staff an = deliveryStaff("Phan Văn An", "ACTIVE");
        add(firstUser,1);UUID pending=orders.checkout(firstUser,request()).getOrderId();
        move(pending, principalFor(an.userId(),"DELIVERY_STAFF"), "{\"status\":\"CANCELLED\",\"reason\":\"Thử\"}").andExpect(status().isForbidden());
        move(pending, principal("SALES_STAFF"), "{\"status\":\"CONFIRMED\"}").andExpect(status().isOk());
        orders.status(firstUser,false,pending,OrderStatus.PROCESSING);
        move(pending, principalFor(an.userId(),"DELIVERY_STAFF"), "{\"status\":\"SHIPPED\"}").andExpect(status().isOk());
        move(pending, principal("SALES_STAFF"), "{\"status\":\"CANCELLED\",\"reason\":\"Thử\"}").andExpect(status().isForbidden());
        jdbc.update("UPDATE payments SET status='PAID' WHERE order_id=?",pending);
        move(pending, principalFor(an.userId(),"DELIVERY_STAFF"), "{\"status\":\"CANCELLED\",\"reason\":\"Khách từ chối nhận\"}").andExpect(status().isConflict());
        move(pending, principal("MANAGER"), "{\"status\":\"CANCELLED\",\"reason\":\"Khách từ chối nhận\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.status").value("REFUNDED")).andExpect(jsonPath("$.shipment.status").value("FAILED"));
    }
    // ---- P1.3 invoices ----
    @Test void deliveryIssuesOneSequentialInvoicePerOrderVisibleToItsCustomerOnly() throws Exception {
        String day = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(java.time.LocalDate.now());
        add(firstUser,1);UUID first=orders.checkout(firstUser,request()).getOrderId();
        assertThat(orders.detail(firstUser,true,first).getInvoice()).as("no invoice before delivery").isNull();
        deliver(first);
        var invoice = orders.detail(firstUser,true,first).getInvoice();
        assertThat(invoice.invoiceNumber()).isEqualTo("HD-" + day + "-0001");
        assertThat(invoice.totalAmount()).isEqualByComparingTo("2000000");
        add(firstUser,2);UUID second=orders.checkout(firstUser,request()).getOrderId();deliver(second);
        assertThat(orders.detail(firstUser,true,second).getInvoice().invoiceNumber()).isEqualTo("HD-" + day + "-0002");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id=?", Integer.class, first)).isEqualTo(1);
        mvc.perform(get("/api/invoices/mine/by-order/{id}",first).with(user(principal("CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(2000000)).andExpect(jsonPath("$.warehouseName").value("Kho thử"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID")).andExpect(jsonPath("$.refundedAmount").value(0));
        mvc.perform(get("/api/invoices/mine/by-order/{id}",first).with(user(principalFor(secondUser,"CUSTOMER")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/invoices").with(user(principal("SALES_STAFF")))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].invoiceNumber").value("HD-" + day + "-0002"));
        mvc.perform(get("/api/invoices").param("keyword","0001").with(user(principal("MANAGER")))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/invoices").with(user(principal("DELIVERY_STAFF")))).andExpect(status().isForbidden());
        add(firstUser,1);UUID pending=orders.checkout(firstUser,request()).getOrderId();
        mvc.perform(get("/api/invoices/mine/by-order/{id}",pending).with(user(principal("CUSTOMER")))).andExpect(status().isNotFound());
    }
    // ---- P3.2 status history ----
    @Test void everyStatusChangeIsRecordedWithItsActorAndReason() throws Exception {
        Staff an = deliveryStaff("Phan Văn An", "ACTIVE");
        UUID id = processingOrder(1);
        move(id, principalFor(an.userId(),"DELIVERY_STAFF"), "{\"status\":\"SHIPPED\"}").andExpect(status().isOk());
        move(id, principalFor(an.userId(),"DELIVERY_STAFF"), "{\"status\":\"CANCELLED\",\"reason\":\"Sai địa chỉ\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory.length()").value(5))
                .andExpect(jsonPath("$.statusHistory[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("PENDING"))
                .andExpect(jsonPath("$.statusHistory[0].actorType").value("STAFF"))
                .andExpect(jsonPath("$.statusHistory[3].toStatus").value("SHIPPED"))
                .andExpect(jsonPath("$.statusHistory[3].actorName").value("Phan Văn An"))
                .andExpect(jsonPath("$.statusHistory[4].fromStatus").value("SHIPPED"))
                .andExpect(jsonPath("$.statusHistory[4].note").value("Giao không thành công: Sai địa chỉ"));
        add(firstUser,1);UUID own=orders.checkout(firstUser,request()).getOrderId();
        jdbc.update("DELETE FROM employees WHERE user_id=?", firstUser);
        orders.status(firstUser,true,own,OrderStatus.CANCELLED);
        var changes = orders.detail(firstUser,true,own).getStatusHistory();
        assertThat(changes).extracting(c -> c.actorType()).containsExactly("CUSTOMER","CUSTOMER");
    }
    @Test void installmentCheckoutPreservesExactlyOnePaymentAndApprovalGate() {
        add(firstUser,1);
        UUID provider=UUID.randomUUID();
        jdbc.update("INSERT INTO installment_providers(provider_id,provider_code,provider_name,status) VALUES (?,?,?,'ACTIVE')", provider,"TEST-"+provider,"Đơn vị trả góp kiểm thử");
        var r=request();r.setPaymentMethod(PaymentMethod.INSTALLMENT);r.setProviderId(provider);r.setTermMonths(6);r.setDownPayment(new BigDecimal("100000"));
        var order=orders.checkout(firstUser,r);
        assertThat(order.getWarehouseId()).isEqualTo(warehouse);
        assertThat(order.getPayment().getPaymentMethod()).isEqualTo(PaymentMethod.INSTALLMENT);
        assertThat(order.getPayment().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getInstallment().status()).isEqualTo(InstallmentStatus.PENDING);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id=?",Integer.class,order.getOrderId())).isEqualTo(1);
        assertThat(stock("reserved_quantity")).isEqualTo(1);
        assertThatThrownBy(() -> orders.status(firstUser,false,order.getOrderId(),OrderStatus.CONFIRMED)).hasMessageContaining("chưa được duyệt");
        jdbc.update("UPDATE installment_payments SET status='APPROVED' WHERE payment_id=?",order.getPayment().getPaymentId());
        assertThat(orders.status(firstUser,false,order.getOrderId(),OrderStatus.CONFIRMED).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(stock("reserved_quantity")).isEqualTo(1);
    }

}
