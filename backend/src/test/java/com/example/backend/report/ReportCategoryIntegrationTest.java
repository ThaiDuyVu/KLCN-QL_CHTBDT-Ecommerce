package com.example.backend.report;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.report.dto.CategoryReportRow;
import com.example.backend.report.dto.ReportFilter;
import com.example.backend.report.service.ReportService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "app.seed.auth.enabled=false", "app.payment.vnpay.enabled=false", "app.order.vnpay-expiry.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class ReportCategoryIntegrationTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("report_test").withUsername("report_test").withPassword("report_test");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-report-jwt-secret-value-long-enough");
    }

    @Autowired ReportService reports;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    UUID laptop, phone, customer, warehouse, otherWarehouse, gamingVariant, officeVariant, phoneVariant;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE order_items, payments, orders, carts, customers, inventory, product_variants, products, brands, categories, warehouses CASCADE");
        UUID brand = UUID.randomUUID();
        jdbc.update("INSERT INTO brands(brand_id,brand_name,status) VALUES (?,'Hãng thử','ACTIVE')", brand);
        laptop = category("Laptop", null);
        UUID gaming = category("Laptop gaming", laptop);
        UUID gamingHighEnd = category("Laptop gaming cao cấp", gaming);
        phone = category("Điện thoại", null);
        category("Tivi", null); // no sales: must not appear
        gamingVariant = variant(product(gamingHighEnd, brand), 30_000_000, 25_000_000);
        officeVariant = variant(product(laptop, brand), 15_000_000, 12_000_000);
        phoneVariant = variant(product(phone, brand), 10_000_000, 8_000_000);
        warehouse = warehouse("Chi nhánh Quận 1");
        otherWarehouse = warehouse("Chi nhánh Thủ Đức");
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(user_id,username,password,email,status) VALUES (?,?,?,?,'ACTIVE')",
                userId, "report-" + userId, "test-only-hash", userId + "@example.test");
        customer = jdbc.queryForObject("INSERT INTO customers(user_id,full_name) VALUES (?, 'Nguyễn Văn An') RETURNING customer_id", UUID.class, userId);
    }

    UUID category(String name, UUID parent) {
        return jdbc.queryForObject("INSERT INTO categories(category_name,parent_id) VALUES (?,?) RETURNING category_id", UUID.class, name, parent);
    }
    UUID product(UUID category, UUID brand) {
        return jdbc.queryForObject("INSERT INTO products(category_id,brand_id,product_name,status) VALUES (?,?,?,'ACTIVE') RETURNING product_id",
                UUID.class, category, brand, "Sản phẩm " + UUID.randomUUID());
    }
    UUID variant(UUID product, long price, long cost) {
        return jdbc.queryForObject("INSERT INTO product_variants(product_id,sku,price,cost_price) VALUES (?,?,?,?) RETURNING variant_id",
                UUID.class, product, "SKU-" + UUID.randomUUID(), price, cost);
    }
    UUID warehouse(String name) {
        return jdbc.queryForObject("INSERT INTO warehouses(warehouse_name) VALUES (?) RETURNING warehouse_id", UUID.class, name);
    }
    UUID order(String status, UUID warehouseId) {
        return jdbc.queryForObject("INSERT INTO orders(customer_id,order_code,recipient_name,recipient_phone,shipping_address,status,warehouse_id) "
                + "VALUES (?,?,'Nguyễn Văn An','0901234567','TP. Hồ Chí Minh',?,?) RETURNING order_id",
                UUID.class, customer, "ORD-" + UUID.randomUUID(), status, warehouseId);
    }
    void line(UUID order, UUID variant, int quantity, long finalPrice, long cost) {
        jdbc.update("INSERT INTO order_items(order_id,variant_id,quantity,cost_price,unit_price,final_unit_price) VALUES (?,?,?,?,?,?)",
                order, variant, quantity, cost, finalPrice, finalPrice);
    }
    ReportFilter today(UUID warehouseId) {
        var filter = new ReportFilter();
        filter.setFrom(LocalDate.now().minusDays(1)); filter.setTo(LocalDate.now()); filter.setWarehouseId(warehouseId);
        return filter;
    }

    @Test void deliveredSalesAreRolledUpToRootCategoriesWithProfit() {
        UUID first = order("DELIVERED", warehouse);
        line(first, gamingVariant, 1, 28_000_000, 25_000_000); // promotion price, grandchild of Laptop
        line(first, officeVariant, 2, 15_000_000, 12_000_000);
        UUID second = order("DELIVERED", otherWarehouse);
        line(second, phoneVariant, 1, 10_000_000, 8_000_000);
        line(order("CANCELLED", warehouse), phoneVariant, 5, 10_000_000, 8_000_000);
        line(order("SHIPPED", warehouse), officeVariant, 3, 15_000_000, 12_000_000);

        List<CategoryReportRow> rows = reports.categories(today(null));

        assertThat(rows).extracting(CategoryReportRow::getCategoryName).containsExactly("Laptop", "Điện thoại");
        CategoryReportRow laptopRow = rows.get(0);
        assertThat(laptopRow.getCategoryId()).isEqualTo(laptop);
        assertThat(laptopRow.getQuantity()).isEqualTo(3);
        assertThat(laptopRow.getOrderCount()).isEqualTo(1);
        assertThat(laptopRow.getRevenue()).isEqualByComparingTo("58000000");
        assertThat(laptopRow.getGrossProfit()).isEqualByComparingTo("9000000");
        assertThat(rows.get(1).getRevenue()).isEqualByComparingTo("10000000");
        assertThat(rows.get(1).getGrossProfit()).isEqualByComparingTo("2000000");

        assertThat(rows.stream().map(CategoryReportRow::getRevenue).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)).as("sum over categories equals delivered revenue").isEqualByComparingTo("68000000");
    }

    @Test void warehouseFilterLimitsTheBreakdown() {
        line(order("DELIVERED", warehouse), officeVariant, 1, 15_000_000, 12_000_000);
        line(order("DELIVERED", otherWarehouse), phoneVariant, 1, 10_000_000, 8_000_000);
        assertThat(reports.categories(today(otherWarehouse))).extracting(CategoryReportRow::getCategoryId).containsExactly(phone);
    }

    @Test void onlyManagementCanReadTheCategoryReport() throws Exception {
        line(order("DELIVERED", warehouse), phoneVariant, 2, 10_000_000, 8_000_000);
        mvc.perform(get("/api/reports/categories").with(user(principal("MANAGER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].categoryName").value("Điện thoại"))
                .andExpect(jsonPath("$[0].quantity").value(2));
        mvc.perform(get("/api/reports/categories").with(user(principal("SALES_STAFF")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/categories").with(user(principal("CUSTOMER")))).andExpect(status().isForbidden());
    }

    AuthenticatedUserPrincipal principal(String role) {
        var u = new User(); u.setUserId(UUID.randomUUID()); u.setUsername("test"); u.setStatus("ACTIVE");
        return new AuthenticatedUserPrincipal(u, role);
    }
}
