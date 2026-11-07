package com.example.backend.warranty;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P0.6: a technician opens a warranty ticket for a device the customer brings to the counter. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "app.seed.auth.enabled=false", "app.payment.vnpay.enabled=false", "app.order.vnpay-expiry.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class WarrantyCounterTicketIntegrationTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("warranty_test").withUsername("warranty_test").withPassword("warranty_test");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-warranty-jwt-secret-value-long-enough");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID technicianUser, technicianEmployee, warranty, serial;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE warranty_tickets, warranties, order_items, payments, orders, serial_numbers, inventory, "
                + "product_variants, products, brands, categories, warehouses, employees, customers, users CASCADE");
        technicianUser = account("kythuat.q1");
        technicianEmployee = jdbc.queryForObject("INSERT INTO employees(user_id,employee_code,full_name,position) "
                + "VALUES (?,'NV0001','Đỗ Văn Kỹ','Nhân viên kỹ thuật') RETURNING employee_id", UUID.class, technicianUser);
        UUID customer = jdbc.queryForObject("INSERT INTO customers(user_id,full_name) VALUES (?, 'Nguyễn Thị Hoa') RETURNING customer_id",
                UUID.class, account("khach.hoa"));
        UUID category = jdbc.queryForObject("INSERT INTO categories(category_name) VALUES ('Điện thoại') RETURNING category_id", UUID.class);
        UUID brand = jdbc.queryForObject("INSERT INTO brands(brand_name) VALUES ('Hãng thử') RETURNING brand_id", UUID.class);
        UUID product = jdbc.queryForObject("INSERT INTO products(category_id,brand_id,product_name,status) VALUES (?,?,'Điện thoại thử','ACTIVE') RETURNING product_id",
                UUID.class, category, brand);
        UUID variant = jdbc.queryForObject("INSERT INTO product_variants(product_id,sku,price,cost_price,tracking_type,warranty_months) "
                + "VALUES (?,'DT-128',10000000,8000000,'SERIAL',12) RETURNING variant_id", UUID.class, product);
        UUID warehouse = jdbc.queryForObject("INSERT INTO warehouses(warehouse_name) VALUES ('Chi nhánh Quận 1') RETURNING warehouse_id", UUID.class);
        serial = jdbc.queryForObject("INSERT INTO serial_numbers(variant_id,warehouse_id,serial_number,status) VALUES (?,?,'SN-0001','SOLD') RETURNING serial_id",
                UUID.class, variant, warehouse);
        UUID order = jdbc.queryForObject("INSERT INTO orders(customer_id,order_code,recipient_name,recipient_phone,shipping_address,status,warehouse_id) "
                + "VALUES (?,'ORD-1','Nguyễn Thị Hoa','0901234567','TP. Hồ Chí Minh','DELIVERED',?) RETURNING order_id", UUID.class, customer, warehouse);
        UUID item = jdbc.queryForObject("INSERT INTO order_items(order_id,variant_id,serial_id,quantity,cost_price,unit_price,final_unit_price) "
                + "VALUES (?,?,?,1,8000000,10000000,10000000) RETURNING order_item_id", UUID.class, order, variant, serial);
        warranty = jdbc.queryForObject("INSERT INTO warranties(serial_id,order_item_id,customer_id,start_date,end_date,status) "
                + "VALUES (?,?,?,CURRENT_DATE - 30,CURRENT_DATE + 335,'ACTIVE') RETURNING warranty_id", UUID.class, serial, item, customer);
    }

    UUID account(String username) {
        return jdbc.queryForObject("INSERT INTO users(username,password,email,status) VALUES (?,'test-only-hash',?,'ACTIVE') RETURNING user_id",
                UUID.class, username, username + "@example.test");
    }

    static AuthenticatedUserPrincipal principal(UUID userId, String role) {
        var u = new User(); u.setUserId(userId); u.setUsername("actor"); u.setStatus("ACTIVE");
        return new AuthenticatedUserPrincipal(u, role);
    }

    ResultActions open(UUID warrantyId, AuthenticatedUserPrincipal actor, String description) throws Exception {
        return mvc.perform(post("/api/warranties/{id}/tickets", warrantyId).with(user(actor)).with(csrf())
                .contentType("application/json").content("{\"issueDescription\":\"" + description + "\"}"));
    }

    @Test void technicianOpensATicketAssignedToThemselves() throws Exception {
        open(warranty, principal(technicianUser, "WARRANTY_TECHNICIAN"), "  Máy không lên nguồn  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.employeeId").value(technicianEmployee.toString()))
                .andExpect(jsonPath("$.employeeName").value("Đỗ Văn Kỹ"))
                .andExpect(jsonPath("$.serialNumber").value("SN-0001"))
                .andExpect(jsonPath("$.issueDescription").value("Máy không lên nguồn"))
                .andExpect(jsonPath("$.allowedStatuses[0]").value("IN_PROGRESS"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM warranty_tickets WHERE warranty_id=? AND employee_id=?",
                Integer.class, warranty, technicianEmployee)).isEqualTo(1);
    }

    @Test void expiredWarrantyOrReturnedDeviceIsRefused() throws Exception {
        jdbc.update("UPDATE warranties SET end_date = CURRENT_DATE - 1 WHERE warranty_id=?", warranty);
        open(warranty, principal(technicianUser, "WARRANTY_TECHNICIAN"), "Màn hình sọc").andExpect(status().isConflict());
        jdbc.update("UPDATE warranties SET end_date = CURRENT_DATE + 30 WHERE warranty_id=?", warranty);
        jdbc.update("UPDATE serial_numbers SET status='RETURNED' WHERE serial_id=?", serial);
        open(warranty, principal(technicianUser, "WARRANTY_TECHNICIAN"), "Màn hình sọc").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM warranty_tickets", Integer.class)).isZero();
    }

    @Test void onlyWarrantyStaffWithAnEmployeeProfileMayOpenTickets() throws Exception {
        open(warranty, principal(account("quanly.khong.hoso"), "MANAGER"), "Lỗi loa").andExpect(status().isForbidden());
        open(warranty, principal(technicianUser, "SALES_STAFF"), "Lỗi loa").andExpect(status().isForbidden());
        open(warranty, principal(technicianUser, "CUSTOMER"), "Lỗi loa").andExpect(status().isForbidden());
        open(warranty, principal(technicianUser, "WARRANTY_TECHNICIAN"), " ").andExpect(status().isBadRequest());
        open(UUID.randomUUID(), principal(technicianUser, "WARRANTY_TECHNICIAN"), "Lỗi loa").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM warranty_tickets", Integer.class)).isZero();
    }
}
