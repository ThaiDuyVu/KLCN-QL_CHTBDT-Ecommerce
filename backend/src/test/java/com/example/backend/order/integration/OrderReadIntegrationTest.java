package com.example.backend.order.integration;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate", "app.seed.auth.enabled=false", "app.payment.vnpay.enabled=false"})
@Testcontainers(disabledWithoutDocker=true)
@AutoConfigureMockMvc
class OrderReadIntegrationTest {
    private static final String INTEGRITY_MESSAGE = "Đơn không có đúng một payment; cần rà dữ liệu trước khi xử lý";
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("order_read_test").withUsername("order_read_test").withPassword("order_read_test");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", database::getJdbcUrl);
        r.add("spring.datasource.username", database::getUsername);
        r.add("spring.datasource.password", database::getPassword);
        r.add("app.jwt.secret", () -> "test-only-order-read-secret-value-long-enough");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    UUID ownerUser, otherUser, owner, warehouse;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE orders, customers, warehouses CASCADE");
        ownerUser = UUID.randomUUID(); otherUser = UUID.randomUUID(); owner = UUID.randomUUID(); warehouse = UUID.randomUUID();
        customer(ownerUser, owner); customer(otherUser, UUID.randomUUID());
        jdbc.update("INSERT INTO warehouses(warehouse_id,warehouse_name) VALUES (?,?)", warehouse, "Kho kiểm thử");
    }
    void customer(UUID user, UUID customer) {
        jdbc.update("INSERT INTO users(user_id,username,password,email,status) VALUES (?,?,?,?,'ACTIVE')", user, "test-"+user, "isolated-test-hash", user+"@example.test");
        jdbc.update("INSERT INTO customers(customer_id,user_id,full_name) VALUES (?,?,?)", customer, user, "Khách kiểm thử");
    }
    UUID order(UUID wh, int paymentCount) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO orders(order_id,customer_id,warehouse_id,order_code,recipient_name,recipient_phone,shipping_address,subtotal,total_amount,status) VALUES (?,?,?,?,?,?,?,100,100,'PENDING')",
                id, owner, wh, "ORD-READ-"+id, "Khách kiểm thử", "0000000000", "Địa chỉ kiểm thử");
        for (int i=0; i<paymentCount; i++) jdbc.update("INSERT INTO payments(order_id,payment_method,amount,status) VALUES (?,'COD',100,'PENDING')", id);
        return id;
    }
    AuthenticatedUserPrincipal principal(UUID id, String role) {
        var user = new User(); user.setUserId(id); user.setUsername("test"); user.setStatus("ACTIVE");
        return new AuthenticatedUserPrincipal(user, role);
    }
    @Test void nullWarehouseOwnerListAndDetail() throws Exception {
        UUID id = order(null, 1);
        mvc.perform(get("/api/orders/mine").with(user(principal(ownerUser,"CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].warehouseId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].warehouseName").value(nullValue()));
        mvc.perform(get("/api/orders/mine/{id}",id).with(user(principal(ownerUser,"CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(id.toString()))
                .andExpect(jsonPath("$.warehouseId").value(nullValue())).andExpect(jsonPath("$.warehouseName").value(nullValue()))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.totalAmount").value(100))
                .andExpect(jsonPath("$.payment.status").value("PENDING"));
    }
    @Test void nonNullAndMixedWarehouseListPreserveRealNames() throws Exception {
        UUID legacy = order(null,1), normal = order(warehouse,1);
        mvc.perform(get("/api/orders/mine").with(user(principal(ownerUser,"CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content",hasSize(2)))
                .andExpect(jsonPath("$.content[*].warehouseName",hasItems(nullValue(),equalTo("Kho kiểm thử"))));
        mvc.perform(get("/api/orders/mine/{id}",normal).with(user(principal(ownerUser,"CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.warehouseId").value(warehouse.toString()))
                .andExpect(jsonPath("$.warehouseName").value("Kho kiểm thử"));
        assertThat(jdbc.queryForObject("SELECT warehouse_id FROM orders WHERE order_id=?",UUID.class,legacy)).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"STAFF","MANAGER","ADMIN"})
    void staffReadPathsKeepNullWarehouse(String role) throws Exception {
        UUID id = order(null,1);
        mvc.perform(get("/api/orders").with(user(principal(otherUser,role))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].warehouseName").value(nullValue()));
        mvc.perform(get("/api/orders/{id}",id).with(user(principal(otherUser,role))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.warehouseId").value(nullValue()));
    }
    @ParameterizedTest @ValueSource(ints={0,2})
    void missingOrMultiplePaymentsRemainSpecific409(int count) throws Exception {
        UUID id = order(warehouse,count);
        mvc.perform(get("/api/orders/mine/{id}",id).with(user(principal(ownerUser,"CUSTOMER"))))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("{\"message\":\""+INTEGRITY_MESSAGE+"\"}"));
        mvc.perform(get("/api/orders/{id}",id).with(user(principal(otherUser,"STAFF"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(INTEGRITY_MESSAGE));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id=?",Integer.class,id)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,id)).isEqualTo("PENDING");
    }
    @Test void foreignAndUnknownOrderAre404BeforePaymentDiagnostics() throws Exception {
        UUID id = order(null,0);
        mvc.perform(get("/api/orders/mine/{id}",id).with(user(principal(otherUser,"CUSTOMER"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Không tìm thấy đơn hàng"));
        mvc.perform(get("/api/orders/mine/{id}",UUID.randomUUID()).with(user(principal(ownerUser,"CUSTOMER"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/{id}",id).with(user(principal(ownerUser,"CUSTOMER"))))
                .andExpect(status().isForbidden());
    }
    @Test void vnpayReadAndConfirmationIntegrityRemainUnchanged() throws Exception {
        UUID id = order(warehouse,1);
        jdbc.update("UPDATE payments SET payment_method='VNPAY' WHERE order_id=?",id);
        mvc.perform(get("/api/orders/mine/{id}",id).with(user(principal(ownerUser,"CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.paymentMethod").value("VNPAY"))
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andExpect(jsonPath("$.allowedStatuses",not(hasItem("CONFIRMED"))));
        mvc.perform(patch("/api/orders/{id}/status",id).with(user(principal(otherUser,"STAFF"))).with(csrf())
                .contentType("application/json").content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Payment VNPAY chưa PAID; không thể xác nhận đơn hàng"));
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?",String.class,id)).isEqualTo("PENDING");
    }
    @Test void unrelatedInstallmentConflictIsNotPaymentCardinalityError() throws Exception {
        UUID id = order(warehouse,1);
        jdbc.update("UPDATE payments SET payment_method='INSTALLMENT' WHERE order_id=?",id);
        mvc.perform(get("/api/orders/mine/{id}",id).with(user(principal(ownerUser,"CUSTOMER"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Payment INSTALLMENT thiếu hồ sơ trả góp"));
    }
}
