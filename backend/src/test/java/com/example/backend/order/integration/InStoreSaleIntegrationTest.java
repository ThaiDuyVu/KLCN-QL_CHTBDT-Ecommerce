package com.example.backend.order.integration;

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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P3.4 / V13: walk-in customers and cash sales at the counter. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "app.seed.auth.enabled=false", "app.payment.vnpay.enabled=false", "app.order.vnpay-expiry.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class InStoreSaleIntegrationTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("pos_test").withUsername("pos_test").withPassword("pos_test");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-pos-jwt-secret-value-long-enough");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID q1, td, phone, caseVariant, salesQ1;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE invoices, warranties, order_status_history, order_items, payments, orders, serial_numbers, inventory, "
                + "product_variants, products, brands, categories, warehouses, employees, customers, user_roles, users CASCADE");
        q1 = jdbc.queryForObject("INSERT INTO warehouses(warehouse_name) VALUES ('Chi nhánh Quận 1') RETURNING warehouse_id", UUID.class);
        td = jdbc.queryForObject("INSERT INTO warehouses(warehouse_name) VALUES ('Chi nhánh Thủ Đức') RETURNING warehouse_id", UUID.class);
        UUID category = jdbc.queryForObject("INSERT INTO categories(category_name) VALUES ('Điện thoại') RETURNING category_id", UUID.class);
        UUID brand = jdbc.queryForObject("INSERT INTO brands(brand_name) VALUES ('Hãng thử') RETURNING brand_id", UUID.class);
        UUID product = jdbc.queryForObject("INSERT INTO products(category_id,brand_id,product_name,status) VALUES (?,?,'Điện thoại X','ACTIVE') RETURNING product_id", UUID.class, category, brand);
        phone = jdbc.queryForObject("INSERT INTO product_variants(product_id,sku,price,cost_price,tracking_type,warranty_months) VALUES (?,'DT-X',10000000,8000000,'SERIAL',12) RETURNING variant_id", UUID.class, product);
        caseVariant = jdbc.queryForObject("INSERT INTO product_variants(product_id,sku,price,cost_price) VALUES (?,'OP-X',200000,100000) RETURNING variant_id", UUID.class, product);
        jdbc.update("INSERT INTO inventory(warehouse_id,variant_id,quantity,reserved_quantity) VALUES (?,?,3,0),(?,?,10,0),(?,?,1,0)", q1, phone, q1, caseVariant, td, phone);
        for (String serial : new String[]{"SN-1", "SN-2", "SN-3"})
            jdbc.update("INSERT INTO serial_numbers(variant_id,warehouse_id,serial_number,status) VALUES (?,?,?,'AVAILABLE')", phone, q1, serial);
        jdbc.update("INSERT INTO serial_numbers(variant_id,warehouse_id,serial_number,status) VALUES (?,?,'SN-TD','AVAILABLE')", phone, td);
        salesQ1 = jdbc.queryForObject("INSERT INTO users(username,password,email,status) VALUES ('banhang','x','bh@example.test','ACTIVE') RETURNING user_id", UUID.class);
        jdbc.update("INSERT INTO employees(user_id,employee_code,full_name,warehouse_id) VALUES (?,'NV0001','Lê Bán Hàng',?)", salesQ1, q1);
    }

    static AuthenticatedUserPrincipal as(UUID userId, String role) {
        var u = new User(); u.setUserId(userId); u.setUsername("actor"); u.setStatus("ACTIVE");
        // Same permissions as the seeded SALES_STAFF role (V9).
        return new AuthenticatedUserPrincipal(u, role, java.util.List.of("CUSTOMER_VIEW", "EMPLOYEE_VIEW"));
    }
    UUID walkIn() throws Exception {
        mvc.perform(post("/api/customers").with(user(as(salesQ1, "SALES_STAFF"))).with(csrf()).contentType("application/json")
                        .content("{\"fullName\":\"Khách lẻ Hoàng\",\"phone\":\"0912000111\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.phone").value("0912000111")).andExpect(jsonPath("$.accountStatus").doesNotExist());
        return jdbc.queryForObject("SELECT customer_id FROM customers WHERE phone='0912000111'", UUID.class);
    }
    ResultActions sell(UUID customer, String items) throws Exception {
        return mvc.perform(post("/api/orders/in-store").with(user(as(salesQ1, "SALES_STAFF"))).with(csrf()).contentType("application/json")
                .content("{\"customerId\":\"" + customer + "\",\"items\":[" + items + "]}"));
    }
    int stock(UUID warehouse, UUID variant) {
        return jdbc.queryForObject("SELECT quantity FROM inventory WHERE warehouse_id=? AND variant_id=?", Integer.class, warehouse, variant);
    }

    @Test void walkInCustomerPaysCashAndLeavesWithScannedDeviceInvoiceAndWarranty() throws Exception {
        UUID customer = walkIn();
        sell(customer, "{\"variantId\":\"" + phone + "\",\"quantity\":1,\"serialNumbers\":[\"SN-2\"]},{\"variantId\":\"" + caseVariant + "\",\"quantity\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DELIVERED")).andExpect(jsonPath("$.salesChannel").value("IN_STORE"))
                .andExpect(jsonPath("$.orderCode").value(org.hamcrest.Matchers.startsWith("POS-")))
                .andExpect(jsonPath("$.payment.paymentMethod").value("CASH")).andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.totalAmount").value(10400000))
                .andExpect(jsonPath("$.invoice.invoiceNumber").value(org.hamcrest.Matchers.startsWith("HD-")))
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.statusHistory[0].actorName").value("Lê Bán Hàng"))
                .andExpect(jsonPath("$.shippingAddress").value("Nhận tại cửa hàng: Chi nhánh Quận 1"))
                .andExpect(jsonPath("$.recipientPhone").value("0912000111"));
        assertThat(stock(q1, phone)).isEqualTo(2);
        assertThat(stock(q1, caseVariant)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT status FROM serial_numbers WHERE serial_number='SN-2'", String.class)).isEqualTo("SOLD");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM warranties w JOIN serial_numbers s ON s.serial_id=w.serial_id WHERE s.serial_number='SN-2'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM inventory WHERE warehouse_id=? AND variant_id=?", Integer.class, q1, phone)).isZero();
        mvc.perform(get("/api/customers").param("keyword", "0912000111").with(user(as(salesQ1, "SALES_STAFF"))))
                .andExpect(jsonPath("$.content[0].fullName").value("Khách lẻ Hoàng"))
                .andExpect(jsonPath("$.content[0].orderSummary.totalOrders").value(1));
    }

    @Test void deviceNotAvailableAtThisBranchIsRefusedWithoutTouchingStock() throws Exception {
        UUID customer = walkIn();
        sell(customer, "{\"variantId\":\"" + phone + "\",\"quantity\":1,\"serialNumbers\":[\"SN-TD\"]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SN-TD")));
        sell(customer, "{\"variantId\":\"" + phone + "\",\"quantity\":2,\"serialNumbers\":[\"SN-1\"]}").andExpect(status().isBadRequest());
        sell(customer, "{\"variantId\":\"" + caseVariant + "\",\"quantity\":11}").andExpect(status().isConflict());
        assertThat(stock(q1, phone)).isEqualTo(3);
        assertThat(stock(q1, caseVariant)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM serial_numbers WHERE status <> 'AVAILABLE'", Integer.class)).isZero();
    }

    @Test void branchStaffSellOnlyFromTheirBranchAndCustomersCannotUseTheCounter() throws Exception {
        UUID customer = walkIn();
        mvc.perform(post("/api/orders/in-store").with(user(as(salesQ1, "SALES_STAFF"))).with(csrf()).contentType("application/json")
                        .content("{\"customerId\":\"" + customer + "\",\"warehouseId\":\"" + td + "\",\"items\":[{\"variantId\":\"" + phone + "\",\"quantity\":1}]}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/in-store").with(user(as(UUID.randomUUID(), "CUSTOMER"))).with(csrf()).contentType("application/json")
                .content("{\"customerId\":\"" + customer + "\",\"items\":[{\"variantId\":\"" + phone + "\",\"quantity\":1}]}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/customers").with(user(as(salesQ1, "SALES_STAFF"))).with(csrf()).contentType("application/json")
                .content("{\"fullName\":\"Trùng số\",\"phone\":\"0912000111\"}")).andExpect(status().isConflict());
    }
}
