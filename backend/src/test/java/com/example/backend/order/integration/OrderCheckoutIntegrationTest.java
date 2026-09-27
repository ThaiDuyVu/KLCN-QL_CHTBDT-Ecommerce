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

@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false", "app.seed.auth.enabled=false"})
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
    UUID firstUser, secondUser, variant;
    @BeforeEach void setup() {
        // Dedicated ephemeral container only: never touches application data.
        jdbc.execute("TRUNCATE TABLE orders, carts, customers, inventory, products, brands, categories, warehouses CASCADE");
        firstUser = customer(); secondUser = customer(); variant = UUID.randomUUID();
        UUID category = UUID.randomUUID(), product = UUID.randomUUID(), warehouse = UUID.randomUUID(), brand = UUID.randomUUID();
        jdbc.update("INSERT INTO categories(category_id,category_name) VALUES (?,?)",category,"Danh mục thử");
        jdbc.update("INSERT INTO brands(brand_id,brand_name,status) VALUES (?, ?, 'ACTIVE')",brand,"Hãng thử");
        jdbc.update("INSERT INTO products(product_id,category_id,brand_id,product_name,status) VALUES (?,?,?,?,'ACTIVE')",product,category,brand,"Thiết bị thử");
        jdbc.update("INSERT INTO product_variants(variant_id,product_id,sku,price,cost_price,status) VALUES (?,?,?,2000000,1500000,'ACTIVE')",variant,product,"SKU-"+variant);
        jdbc.update("INSERT INTO warehouses(warehouse_id,warehouse_name) VALUES (?,?)",warehouse,"Kho thử");
        jdbc.update("INSERT INTO inventory(warehouse_id,variant_id,quantity,reserved_quantity) VALUES (?,?,5,0)",warehouse,variant);
    }
    UUID customer() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(user_id,username,password,email,status) VALUES (?,?,?,?,'ACTIVE')",userId,"test-"+userId,"test-only-hash",userId+"@example.test");
        jdbc.update("INSERT INTO customers(user_id,full_name) VALUES (?,?)",userId,"Khách hàng thử"); return userId;
    }
    void add(UUID userId,int quantity) { var request = new CartItemRequest(); request.setVariantId(variant); request.setQuantity(quantity); cart.add(userId,request); }
    CheckoutRequest request() {
        var r=new CheckoutRequest(); r.setRecipientName("Khách hàng thử");r.setRecipientPhone("0901234567");r.setShippingAddress("TP. Hồ Chí Minh");r.setPaymentMethod(PaymentMethod.COD);return r;
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
            var attempts=List.of(firstUser,secondUser).stream().map(id -> executor.submit(() -> {start.await();try {orders.checkout(id,request());return true;}catch(com.example.backend.order.exception.CommerceException e){return false;}})).toList();
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
        add(firstUser,2);var r=new CartItemRequest();r.setVariantId(last);r.setQuantity(1);cart.add(firstUser,r);
        assertThatThrownBy(() -> orders.checkout(firstUser,request())).hasMessageContaining("Không đủ stock");
        assertThat(stock("reserved_quantity")).isZero();assertThat(cart.get(firstUser).getItems()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)).isZero();
    }
    @Test void staffCancellationAfterConfirmationReleasesReservations() {
        add(firstUser,2);var order=orders.checkout(firstUser,request());orders.status(firstUser,false,order.getOrderId(),OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> orders.status(firstUser,true,order.getOrderId(),OrderStatus.CANCELLED)).hasMessageContaining("Không thể");
        orders.status(firstUser,false,order.getOrderId(),OrderStatus.CANCELLED);assertThat(stock("reserved_quantity")).isZero();assertThat(stock("quantity")).isEqualTo(5);
    }
    AuthenticatedUserPrincipal principal(String role) {var u=new User();u.setUserId(firstUser);u.setUsername("test");u.setStatus("ACTIVE");return new AuthenticatedUserPrincipal(u,role);}
    @Test void httpCustomerCannotAdministrateAndServerIgnoresClientPrices() throws Exception {
        add(firstUser,1);
        mvc.perform(post("/api/orders/checkout").with(user(principal("CUSTOMER"))).with(csrf()).contentType("application/json")
                .content("{\"recipientName\":\"Khách thử\",\"recipientPhone\":\"0901234567\",\"shippingAddress\":\"TP. HCM\",\"paymentMethod\":\"COD\",\"totalAmount\":1,\"unitPrice\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalAmount").value(2000000));
        UUID id=jdbc.queryForObject("SELECT order_id FROM orders",UUID.class);
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("CUSTOMER"))).with(csrf()).contentType("application/json").content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders").with(user(principal("CUSTOMER")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/cart").with(user(principal("STAFF")))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal("STAFF"))).with(csrf()).contentType("application/json").content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
    }
    @Test void missingAddressAndUnsupportedPaymentRejectCheckout() throws Exception {
        add(firstUser,1);
        mvc.perform(post("/api/orders/checkout").with(user(principal("CUSTOMER"))).with(csrf()).contentType("application/json")
                .content("{\"recipientName\":\"Khách thử\",\"recipientPhone\":\"0901234567\",\"paymentMethod\":\"COD\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.message").value("Địa chỉ giao hàng là bắt buộc"));
        jdbc.update("UPDATE inventory SET quantity=0 WHERE variant_id=?", variant);
        mvc.perform(post("/api/orders/checkout").with(user(principal("CUSTOMER"))).with(csrf()).contentType("application/json")
                .content("{\"recipientName\":\"Khách thử\",\"recipientPhone\":\"0901234567\",\"shippingAddress\":\"TP. HCM\",\"paymentMethod\":\"COD\"}"))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Không đủ stock")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        var r=request();r.setPaymentMethod(PaymentMethod.VNPAY);assertThatThrownBy(() -> orders.checkout(firstUser,r)).hasMessageContaining("chỉ hỗ trợ COD");assertThat(stock("reserved_quantity")).isZero();
    }
}
