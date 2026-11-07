package com.example.backend.branch;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P3.3 / V12: branch employees only see and handle their own branch. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "app.seed.auth.enabled=false", "app.payment.vnpay.enabled=false", "app.order.vnpay-expiry.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class BranchScopeIntegrationTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("branch_test").withUsername("branch_test").withPassword("branch_test");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-branch-jwt-secret-value-long-enough");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID q1, td, orderQ1, orderTd, salesQ1, managerQ1, storeManager, deliveryTd, deliveryRole, salesRole;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE return_items, return_requests, invoices, shipments, order_status_history, order_items, payments, orders, employees, customers, user_roles, users, warehouses CASCADE");
        q1 = jdbc.queryForObject("INSERT INTO warehouses(warehouse_name) VALUES ('Chi nhánh Quận 1') RETURNING warehouse_id", UUID.class);
        td = jdbc.queryForObject("INSERT INTO warehouses(warehouse_name) VALUES ('Chi nhánh Thủ Đức') RETURNING warehouse_id", UUID.class);
        UUID customer = jdbc.queryForObject("INSERT INTO customers(user_id,full_name) VALUES (?, 'Nguyễn Văn An') RETURNING customer_id", UUID.class, account("khach"));
        orderQ1 = order(customer, q1, "ORD-Q1");
        orderTd = order(customer, td, "ORD-TD");
        jdbc.update("INSERT INTO invoices(order_id,invoice_number,total_amount,status) VALUES (?,'HD-Q1-0001',1000,'ISSUED'),(?,'HD-TD-0001',1000,'ISSUED')", orderQ1, orderTd);
        salesQ1 = staff("banhang.q1", q1, "SALES_STAFF");
        managerQ1 = staff("quanly.q1", q1, "MANAGER");
        storeManager = staff("quanly.tong", null, "MANAGER");
        deliveryTd = staff("giaohang.td", td, "DELIVERY_STAFF");
        deliveryRole = jdbc.queryForObject("SELECT role_id FROM roles WHERE role_name='DELIVERY_STAFF'", UUID.class);
        salesRole = jdbc.queryForObject("SELECT role_id FROM roles WHERE role_name='SALES_STAFF'", UUID.class);
    }

    UUID account(String username) {
        return jdbc.queryForObject("INSERT INTO users(username,password,email,status) VALUES (?,'test-only-hash',?,'ACTIVE') RETURNING user_id", UUID.class, username, username + "@example.test");
    }
    UUID staff(String username, UUID warehouse, String role) {
        UUID userId = account(username);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?, role_id FROM roles WHERE role_name=?", userId, role);
        jdbc.update("INSERT INTO employees(user_id,employee_code,full_name,warehouse_id) VALUES (?,?,?,?)", userId, username.toUpperCase(), username, warehouse);
        return userId;
    }
    UUID order(UUID customer, UUID warehouse, String code) {
        UUID id = jdbc.queryForObject("INSERT INTO orders(customer_id,order_code,recipient_name,recipient_phone,shipping_address,status,warehouse_id,subtotal,total_amount) "
                + "VALUES (?,?,'An','0901234567','TP.HCM','PROCESSING',?,1000,1000) RETURNING order_id", UUID.class, customer, code, warehouse);
        jdbc.update("INSERT INTO payments(order_id,payment_method,amount,status) VALUES (?,'COD',1000,'PENDING')", id);
        return id;
    }
    static AuthenticatedUserPrincipal as(UUID userId, String role) {
        var u = new User(); u.setUserId(userId); u.setUsername("actor"); u.setStatus("ACTIVE");
        return new AuthenticatedUserPrincipal(u, role, java.util.List.of("EMPLOYEE_CREATE", "EMPLOYEE_VIEW"));
    }

    @Test void branchStaffOnlySeeOrdersAndInvoicesOfTheirBranch() throws Exception {
        mvc.perform(get("/api/orders").with(user(as(salesQ1, "SALES_STAFF"))))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].orderCode").value("ORD-Q1"));
        mvc.perform(get("/api/orders/{id}", orderTd).with(user(as(salesQ1, "SALES_STAFF")))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/orders/{id}/status", orderTd).with(user(as(managerQ1, "MANAGER"))).with(csrf()).contentType("application/json")
                .content("{\"status\":\"SHIPPED\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/invoices").with(user(as(salesQ1, "SALES_STAFF"))))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].invoiceNumber").value("HD-Q1-0001"));
        UUID customer = jdbc.queryForObject("SELECT customer_id FROM orders WHERE order_id=?", UUID.class, orderQ1);
        jdbc.update("INSERT INTO return_requests(return_code,order_id,customer_id,reason,status) VALUES ('TH-Q1',?,?,'Lỗi','REQUESTED'),('TH-TD',?,?,'Lỗi','REQUESTED')", orderQ1, customer, orderTd, customer);
        mvc.perform(get("/api/returns").with(user(as(salesQ1, "SALES_STAFF")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].returnCode").value("TH-Q1"));
        mvc.perform(get("/api/returns").param("status", "REQUESTED").with(user(as(salesQ1, "SALES_STAFF")))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/returns").param("status", "REFUNDED").with(user(as(salesQ1, "SALES_STAFF")))).andExpect(jsonPath("$.totalElements").value(0));
        UUID otherReturn = jdbc.queryForObject("SELECT return_id FROM return_requests WHERE return_code='TH-TD'", UUID.class);
        mvc.perform(get("/api/returns/{id}", otherReturn).with(user(as(salesQ1, "SALES_STAFF")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders").with(user(as(storeManager, "MANAGER")))).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/orders").with(user(as(UUID.randomUUID(), "ADMIN")))).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test void branchManagerReportsAreLimitedToTheirBranch() throws Exception {
        jdbc.update("UPDATE orders SET status='DELIVERED'");
        mvc.perform(get("/api/reports/warehouses").param("warehouseId", td.toString()).with(user(as(managerQ1, "MANAGER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].warehouseName").value("Chi nhánh Quận 1"));
        mvc.perform(get("/api/reports/warehouses").with(user(as(storeManager, "MANAGER")))).andExpect(jsonPath("$.length()").value(2));
    }

    @Test void deliveryStaffOfAnotherBranchCannotTakeTheOrder() throws Exception {
        UUID carrier = jdbc.queryForObject("SELECT employee_id FROM employees WHERE user_id=?", UUID.class, deliveryTd);
        mvc.perform(get("/api/orders/delivery-staff").with(user(as(managerQ1, "MANAGER")))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(patch("/api/orders/{id}/status", orderQ1).with(user(as(storeManager, "MANAGER"))).with(csrf()).contentType("application/json")
                .content("{\"status\":\"SHIPPED\",\"deliveryEmployeeId\":\"" + carrier + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Nhân viên giao hàng thuộc chi nhánh khác"));
    }

    @Test void branchManagerHiresOnlyIntoTheirOwnBranch() throws Exception {
        String body = "{\"username\":\"%s\",\"password\":\"MatKhau123\",\"fullName\":\"Nhân viên mới\",\"email\":\"%s@dienmay.test\",\"roleId\":\"%s\"%s}";
        mvc.perform(post("/api/employees").with(user(as(managerQ1, "MANAGER"))).with(csrf()).contentType("application/json")
                        .content(body.formatted("moi.q1", "moi.q1", salesRole, "")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.warehouseId").value(q1.toString()))
                .andExpect(jsonPath("$.warehouseName").value("Chi nhánh Quận 1"));
        mvc.perform(post("/api/employees").with(user(as(managerQ1, "MANAGER"))).with(csrf()).contentType("application/json")
                .content(body.formatted("moi.td", "moi.td", deliveryRole, ",\"warehouseId\":\"" + td + "\""))).andExpect(status().isForbidden());
        mvc.perform(post("/api/employees").with(user(as(UUID.randomUUID(), "ADMIN"))).with(csrf()).contentType("application/json")
                        .content(body.formatted("moi.td2", "moi.td2", deliveryRole, ",\"warehouseId\":\"" + td + "\"")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.warehouseName").value("Chi nhánh Thủ Đức"));
    }
}
