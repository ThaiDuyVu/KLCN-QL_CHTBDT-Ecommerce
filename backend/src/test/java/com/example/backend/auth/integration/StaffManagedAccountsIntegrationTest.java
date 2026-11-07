package com.example.backend.auth.integration;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P0.4 staff accounts and P0.5 counter-registered customers. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "app.seed.auth.enabled=false", "app.payment.vnpay.enabled=false", "app.order.vnpay-expiry.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class StaffManagedAccountsIntegrationTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("accounts_test").withUsername("accounts_test").withPassword("accounts_test");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-accounts-jwt-secret-value-long-enough");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE TABLE refresh_tokens, employees, customer_addresses, carts, customers, user_roles, users CASCADE");
    }

    UUID role(String name) { return jdbc.queryForObject("SELECT role_id FROM roles WHERE role_name=?", UUID.class, name); }

    static AuthenticatedUserPrincipal principal(String role, String... permissions) {
        var u = new User(); u.setUserId(UUID.randomUUID()); u.setUsername("actor"); u.setStatus("ACTIVE");
        return new AuthenticatedUserPrincipal(u, role, List.of(permissions));
    }

    ResultActions createEmployee(AuthenticatedUserPrincipal actor, String body) throws Exception {
        return mvc.perform(post("/api/employees").with(user(actor)).with(csrf()).contentType("application/json").content(body));
    }

    String employeeBody(String username, UUID roleId, String extra) {
        return "{\"username\":\"" + username + "\",\"password\":\"MatKhau123\",\"fullName\":\"Trần Thị Bình\","
                + "\"email\":\"" + username + "@dienmay.test\",\"phone\":\"0912345678\",\"roleId\":\"" + roleId + "\"" + extra + "}";
    }

    @Test void adminCreatesAWarehouseKeeperWhoGetsAnEmployeeProfileRoleAndUsablePassword() throws Exception {
        createEmployee(principal("ADMIN"), employeeBody("thukho.q1", role("WAREHOUSE_KEEPER"), ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeCode").value("NV0001"))
                .andExpect(jsonPath("$.roleName").value("WAREHOUSE_KEEPER"))
                .andExpect(jsonPath("$.position").value("Thủ kho"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        String hash = jdbc.queryForObject("SELECT password FROM users WHERE username='thukho.q1'", String.class);
        assertThat(passwordEncoder.matches("MatKhau123", hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employees e JOIN users u ON u.user_id=e.user_id WHERE u.username='thukho.q1'", Integer.class)).isEqualTo(1);

        createEmployee(principal("ADMIN"), employeeBody("banhang.q1", role("SALES_STAFF"), ",\"position\":\"Tư vấn laptop\""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.employeeCode").value("NV0002"))
                .andExpect(jsonPath("$.position").value("Tư vấn laptop"));
    }

    @Test void managerCreatesJobTitleStaffButNotManagementOrCustomers() throws Exception {
        var manager = principal("MANAGER", "EMPLOYEE_CREATE");
        createEmployee(manager, employeeBody("giaohang.q1", role("DELIVERY_STAFF"), "")).andExpect(status().isCreated());
        createEmployee(manager, employeeBody("quanly.moi", role("MANAGER"), "")).andExpect(status().isForbidden());
        createEmployee(manager, employeeBody("admin.moi", role("ADMIN"), "")).andExpect(status().isForbidden());
        createEmployee(manager, employeeBody("khach.moi", role("CUSTOMER"), "")).andExpect(status().isBadRequest());
        createEmployee(principal("SALES_STAFF", "EMPLOYEE_VIEW"), employeeBody("khac.nv", role("SALES_STAFF"), "")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).as("nothing half-created").isEqualTo(1);
    }

    @Test void duplicateUsernameOrEmployeeCodeIsRejectedWithoutPartialRows() throws Exception {
        createEmployee(principal("ADMIN"), employeeBody("kythuat.q1", role("WARRANTY_TECHNICIAN"), ",\"employeeCode\":\"kt-01\""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.employeeCode").value("KT-01"));
        createEmployee(principal("ADMIN"), employeeBody("KyThuat.Q1", role("WARRANTY_TECHNICIAN"), "")).andExpect(status().isConflict());
        createEmployee(principal("ADMIN"), employeeBody("kythuat.q2", role("WARRANTY_TECHNICIAN"), ",\"employeeCode\":\"KT-01\""))
                .andExpect(status().isBadRequest());
        createEmployee(principal("ADMIN"), "{\"username\":\"ab\",\"password\":\"123\",\"fullName\":\"\",\"email\":\"x\",\"roleId\":null}")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isEqualTo(1);
    }

    @Test void employeeProfileIsReadAndUpdatedByUserAccount() throws Exception {
        createEmployee(principal("ADMIN"), employeeBody("banhang.q3", role("SALES_STAFF"), "")).andExpect(status().isCreated());
        UUID userId = jdbc.queryForObject("SELECT user_id FROM users WHERE username='banhang.q3'", UUID.class);
        UUID employeeId = jdbc.queryForObject("SELECT employee_id FROM employees WHERE user_id=?", UUID.class, userId);
        mvc.perform(get("/api/employees/by-user/{id}", userId).with(user(principal("MANAGER", "EMPLOYEE_VIEW"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.employeeId").value(employeeId.toString()));
        mvc.perform(put("/api/employees/{id}", employeeId).with(user(principal("MANAGER", "EMPLOYEE_UPDATE"))).with(csrf())
                        .contentType("application/json").content("{\"fullName\":\"Trần Thị Bình An\",\"position\":\"Trưởng ca\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Trần Thị Bình An"))
                .andExpect(jsonPath("$.position").value("Trưởng ca"));
        assertThat(jdbc.queryForObject("SELECT display_name FROM users WHERE user_id=?", String.class, userId)).isEqualTo("Trần Thị Bình An");
    }

    ResultActions createCustomer(AuthenticatedUserPrincipal actor, String body) throws Exception {
        return mvc.perform(post("/api/customers").with(user(actor)).with(csrf()).contentType("application/json").content(body));
    }

    @Test void salesStaffRegistersAWalkInCustomerWhoCanSignInWithThePhoneNumber() throws Exception {
        createCustomer(principal("SALES_STAFF", "CUSTOMER_VIEW"),
                "{\"fullName\":\"Lê Văn Cường\",\"phone\":\"+84987654321\",\"address\":\"12 Lê Lợi, Quận 1\",\"password\":\"KhachHang1\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("0987654321"))
                .andExpect(jsonPath("$.fullName").value("Lê Văn Cường"))
                .andExpect(jsonPath("$.address").value("12 Lê Lợi, Quận 1"))
                .andExpect(jsonPath("$.email").value(startsWith("noemail-")));
        assertThat(jdbc.queryForObject("SELECT r.role_name FROM user_roles ur JOIN roles r ON r.role_id=ur.role_id JOIN users u ON u.user_id=ur.user_id WHERE u.username='0987654321'", String.class))
                .isEqualTo("CUSTOMER");
        createCustomer(principal("SALES_STAFF"), "{\"fullName\":\"Người khác\",\"phone\":\"+84987654321\",\"username\":\"nguoikhac\",\"password\":\"KhachHang1\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Số điện thoại đã thuộc về một khách hàng khác"));
        createCustomer(principal("DELIVERY_STAFF"), "{\"fullName\":\"X\",\"phone\":\"0900000001\",\"password\":\"KhachHang1\"}")
                .andExpect(status().isForbidden());
        createCustomer(principal("CUSTOMER"), "{\"fullName\":\"X\",\"phone\":\"0900000002\",\"password\":\"KhachHang1\"}")
                .andExpect(status().isForbidden());
    }

    @Test void staffUpdatesCustomerContactDetails() throws Exception {
        createCustomer(principal("ADMIN"), "{\"fullName\":\"Phạm Thị Dung\",\"phone\":\"0901112223\",\"password\":\"KhachHang1\"}")
                .andExpect(status().isCreated());
        createCustomer(principal("ADMIN"), "{\"fullName\":\"Võ Minh Em\",\"phone\":\"0904445556\",\"email\":\"em@dienmay.test\",\"password\":\"KhachHang1\"}")
                .andExpect(status().isCreated());
        UUID dung = jdbc.queryForObject("SELECT customer_id FROM customers WHERE full_name='Phạm Thị Dung'", UUID.class);
        String update = "{\"fullName\":\"Phạm Thị Dung\",\"phone\":\"0901112299\",\"email\":\"dung@dienmay.test\",\"address\":\"5 Nguyễn Huệ\"}";
        mvc.perform(put("/api/customers/{id}", dung).with(user(principal("SALES_STAFF"))).with(csrf()).contentType("application/json").content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("0901112299"))
                .andExpect(jsonPath("$.email").value("dung@dienmay.test")).andExpect(jsonPath("$.address").value("5 Nguyễn Huệ"));
        mvc.perform(put("/api/customers/{id}", dung).with(user(principal("SALES_STAFF"))).with(csrf()).contentType("application/json")
                        .content("{\"fullName\":\"Phạm Thị Dung\",\"phone\":\"0904445556\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/customers/{id}", dung).with(user(principal("SALES_STAFF"))).with(csrf()).contentType("application/json")
                        .content("{\"fullName\":\"Phạm Thị Dung\",\"phone\":\"0901112299\",\"email\":\"em@dienmay.test\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/customers/{id}", UUID.randomUUID()).with(user(principal("ADMIN"))).with(csrf()).contentType("application/json").content(update))
                .andExpect(status().isNotFound());
    }
}
