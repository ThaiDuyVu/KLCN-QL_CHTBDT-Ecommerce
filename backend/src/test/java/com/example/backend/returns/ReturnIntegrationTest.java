package com.example.backend.returns;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P1.2: customer return request → sales approval → warehouse receipt → refund. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "app.seed.auth.enabled=false", "app.payment.vnpay.enabled=false", "app.order.vnpay-expiry.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class ReturnIntegrationTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("returns_test").withUsername("returns_test").withPassword("returns_test");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-returns-jwt-secret-value-long-enough");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReportService reports;
    UUID customerUser, otherUser, salesUser, keeperUser, managerUser;
    UUID warehouse, phoneVariant, caseVariant, headsetVariant, order, phoneLine, caseLine, serial;
    @Autowired com.example.backend.order.service.OrderService orderService;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE order_status_history, return_exchange_items, invoices, return_items, return_requests, shipments, warranties, order_items, payments, orders, serial_numbers, "
                + "inventory, product_variants, products, brands, categories, warehouses, employees, customers, users CASCADE");
        customerUser = account("khach.an");
        otherUser = account("khach.binh");
        UUID customer = jdbc.queryForObject("INSERT INTO customers(user_id,full_name) VALUES (?, 'Nguyễn Văn An') RETURNING customer_id", UUID.class, customerUser);
        jdbc.update("INSERT INTO customers(user_id,full_name) VALUES (?, 'Trần Thị Bình')", otherUser);
        salesUser = staff("banhang", "Lê Bán Hàng");
        keeperUser = staff("thukho", "Phạm Thủ Kho");
        managerUser = staff("quanly", "Võ Quản Lý");
        UUID category = jdbc.queryForObject("INSERT INTO categories(category_name) VALUES ('Điện thoại') RETURNING category_id", UUID.class);
        UUID brand = jdbc.queryForObject("INSERT INTO brands(brand_name) VALUES ('Hãng thử') RETURNING brand_id", UUID.class);
        UUID product = jdbc.queryForObject("INSERT INTO products(category_id,brand_id,product_name,status) VALUES (?,?,'Điện thoại X','ACTIVE') RETURNING product_id", UUID.class, category, brand);
        phoneVariant = jdbc.queryForObject("INSERT INTO product_variants(product_id,sku,price,cost_price,tracking_type,warranty_months) VALUES (?,'DT-X',10000000,8000000,'SERIAL',12) RETURNING variant_id", UUID.class, product);
        caseVariant = jdbc.queryForObject("INSERT INTO product_variants(product_id,sku,price,cost_price) VALUES (?,'OP-X',200000,100000) RETURNING variant_id", UUID.class, product);
        headsetVariant = jdbc.queryForObject("INSERT INTO product_variants(product_id,sku,price,cost_price) VALUES (?,'TN-Y',1000000,600000) RETURNING variant_id", UUID.class, product);
        warehouse = jdbc.queryForObject("INSERT INTO warehouses(warehouse_name) VALUES ('Chi nhánh Quận 1') RETURNING warehouse_id", UUID.class);
        jdbc.update("INSERT INTO inventory(warehouse_id,variant_id,quantity,reserved_quantity) VALUES (?,?,5,0)", warehouse, headsetVariant);
        jdbc.update("INSERT INTO inventory(warehouse_id,variant_id,quantity,reserved_quantity) VALUES (?,?,4,0),(?,?,10,0)", warehouse, phoneVariant, warehouse, caseVariant);
        serial = jdbc.queryForObject("INSERT INTO serial_numbers(variant_id,warehouse_id,serial_number,status) VALUES (?,?,'SN-X1','SOLD') RETURNING serial_id", UUID.class, phoneVariant, warehouse);
        order = jdbc.queryForObject("INSERT INTO orders(customer_id,order_code,recipient_name,recipient_phone,shipping_address,status,warehouse_id,subtotal,total_amount) "
                + "VALUES (?,'ORD-RET','Nguyễn Văn An','0901234567','TP. Hồ Chí Minh','DELIVERED',?,10400000,10400000) RETURNING order_id", UUID.class, customer, warehouse);
        phoneLine = jdbc.queryForObject("INSERT INTO order_items(order_id,variant_id,serial_id,quantity,cost_price,unit_price,final_unit_price) VALUES (?,?,?,1,8000000,10000000,10000000) RETURNING order_item_id", UUID.class, order, phoneVariant, serial);
        caseLine = jdbc.queryForObject("INSERT INTO order_items(order_id,variant_id,quantity,cost_price,unit_price,final_unit_price) VALUES (?,?,2,100000,200000,200000) RETURNING order_item_id", UUID.class, order, caseVariant);
        jdbc.update("INSERT INTO payments(order_id,payment_method,amount,status) VALUES (?,'COD',10400000,'PAID')", order);
        UUID carrier = jdbc.queryForObject("SELECT employee_id FROM employees LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO shipments(order_id,employee_id,tracking_number,shipping_provider,shipped_date,delivered_date,status) "
                + "VALUES (?,?,'GH-RET','Giao nội bộ',now() - interval '3 days',now() - interval '2 days','DELIVERED')", order, carrier);
        jdbc.update("INSERT INTO invoices(order_id,invoice_number,total_amount,status) VALUES (?,'HD-TEST-0001',10400000,'ISSUED')", order);
        jdbc.update("INSERT INTO warranties(serial_id,order_item_id,customer_id,start_date,end_date,status) VALUES (?,?,?,CURRENT_DATE - 2,CURRENT_DATE + 363,'ACTIVE')", serial, phoneLine, customer);
    }

    UUID account(String username) {
        return jdbc.queryForObject("INSERT INTO users(username,password,email,status) VALUES (?,'test-only-hash',?,'ACTIVE') RETURNING user_id", UUID.class, username, username + "@example.test");
    }
    UUID staff(String username, String name) {
        UUID userId = account(username);
        jdbc.update("INSERT INTO employees(user_id,employee_code,full_name) VALUES (?,?,?)", userId, username.toUpperCase(), name);
        return userId;
    }
    static AuthenticatedUserPrincipal as(UUID userId, String role) {
        var u = new User(); u.setUserId(userId); u.setUsername("actor"); u.setStatus("ACTIVE");
        return new AuthenticatedUserPrincipal(u, role);
    }
    ResultActions request(UUID userId, String body) throws Exception {
        return mvc.perform(post("/api/returns/mine").with(user(as(userId, "CUSTOMER"))).with(csrf()).contentType("application/json").content(body));
    }
    String body(UUID line, int quantity) {
        return "{\"orderId\":\"" + order + "\",\"reason\":\"Máy bị lỗi màn hình\",\"items\":[{\"orderItemId\":\"" + line + "\",\"quantity\":" + quantity + "}]}";
    }
    ResultActions move(UUID returnId, UUID userId, String role, String body) throws Exception {
        return mvc.perform(patch("/api/returns/{id}/status", returnId).with(user(as(userId, role))).with(csrf()).contentType("application/json").content(body));
    }
    UUID returnId() { return jdbc.queryForObject("SELECT return_id FROM return_requests ORDER BY request_date DESC LIMIT 1", UUID.class); }
    UUID returnItemId(UUID returnId) { return jdbc.queryForObject("SELECT return_item_id FROM return_items WHERE return_id=?", UUID.class, returnId); }
    int stock(UUID variant) { return jdbc.queryForObject("SELECT quantity FROM inventory WHERE variant_id=?", Integer.class, variant); }
    ReportFilter lastMonth() { var f = new ReportFilter(); f.setFrom(LocalDate.now().minusDays(29)); f.setTo(LocalDate.now()); return f; }

    @Test void partialReturnIsApprovedReceivedRestockedAndRefundedAndReportsAreNet() throws Exception {
        request(customerUser, body(phoneLine, 1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED")).andExpect(jsonPath("$.refundAmount").value(10000000))
                .andExpect(jsonPath("$.returnCode").value(org.hamcrest.Matchers.startsWith("TH-")))
                .andExpect(jsonPath("$.items[0].serialNumber").value("SN-X1"));
        UUID id = returnId();
        mvc.perform(get("/api/returns/{id}", id).with(user(as(salesUser, "SALES_STAFF"))))
                .andExpect(jsonPath("$.allowedStatuses").value(org.hamcrest.Matchers.contains("APPROVED", "REJECTED")));
        move(id, keeperUser, "WAREHOUSE_KEEPER", "{\"status\":\"APPROVED\"}").andExpect(status().isForbidden());
        move(id, salesUser, "SALES_STAFF", "{\"status\":\"APPROVED\"}").andExpect(status().isOk()).andExpect(jsonPath("$.employeeName").value("Lê Bán Hàng"));
        move(id, keeperUser, "WAREHOUSE_KEEPER", "{\"status\":\"RECEIVED\"}").andExpect(status().isBadRequest());
        move(id, keeperUser, "WAREHOUSE_KEEPER", "{\"status\":\"RECEIVED\",\"conditions\":[{\"returnItemId\":\"" + returnItemId(id) + "\",\"condition\":\"RESTOCK\"}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].receivedCondition").value("RESTOCK"));
        assertThat(jdbc.queryForObject("SELECT status FROM serial_numbers WHERE serial_id=?", String.class, serial)).isEqualTo("AVAILABLE");
        assertThat(stock(phoneVariant)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT status FROM warranties WHERE serial_id=?", String.class, serial)).isEqualTo("VOIDED");
        move(id, salesUser, "SALES_STAFF", "{\"status\":\"REFUNDED\"}").andExpect(status().isForbidden());
        move(id, managerUser, "MANAGER", "{\"status\":\"REFUNDED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED")).andExpect(jsonPath("$.processedAt").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?", String.class, order)).as("partial return").isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT status FROM invoices WHERE order_id=?", String.class, order)).isEqualTo("ISSUED");
        var summary = reports.summary(lastMonth());
        assertThat(summary.getRevenue()).isEqualByComparingTo("400000");
        var categories = reports.categories(lastMonth());
        assertThat(categories.get(0).getQuantity()).as("only the two cases are still sold").isEqualTo(2);
        assertThat(categories.get(0).getRevenue()).isEqualByComparingTo("400000");
    }

    @Test void returningEveryUnitRefundsThePaymentAndDefectiveGoodsDoNotGoBackToSale() throws Exception {
        request(customerUser, "{\"orderId\":\"" + order + "\",\"reason\":\"Đổi ý\",\"items\":[{\"orderItemId\":\"" + phoneLine + "\",\"quantity\":1},"
                + "{\"orderItemId\":\"" + caseLine + "\",\"quantity\":2,\"reason\":\"Không vừa\"}]}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundAmount").value(10400000));
        UUID id = returnId();
        move(id, salesUser, "SALES_STAFF", "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
        var lines = jdbc.queryForList("SELECT return_item_id, order_item_id FROM return_items WHERE return_id=?", id);
        String conditions = lines.stream().map(row -> "{\"returnItemId\":\"" + row.get("return_item_id") + "\",\"condition\":\""
                + (row.get("order_item_id").equals(phoneLine) ? "DEFECTIVE" : "RESTOCK") + "\"}").reduce((a, b) -> a + "," + b).orElseThrow();
        move(id, keeperUser, "WAREHOUSE_KEEPER", "{\"status\":\"RECEIVED\",\"conditions\":[" + conditions + "]}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM serial_numbers WHERE serial_id=?", String.class, serial)).isEqualTo("DEFECTIVE");
        assertThat(stock(phoneVariant)).as("defective phone not back on sale").isEqualTo(4);
        assertThat(stock(caseVariant)).isEqualTo(12);
        move(id, managerUser, "MANAGER", "{\"status\":\"REFUNDED\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?", String.class, order)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT status FROM invoices WHERE order_id=?", String.class, order)).isEqualTo("CANCELLED");
        assertThat(reports.summary(lastMonth()).getRevenue()).isEqualByComparingTo("0");
    }

    @Test void requestsOutsideThePolicyAreRefused() throws Exception {
        request(otherUser, body(phoneLine, 1)).andExpect(status().isNotFound());
        request(customerUser, body(caseLine, 3)).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Chỉ còn 2 sản phẩm có thể trả"));
        request(customerUser, body(caseLine, 1)).andExpect(status().isCreated());
        request(customerUser, body(caseLine, 2)).andExpect(status().isConflict());
        request(customerUser, body(caseLine, 1)).andExpect(status().isCreated());
        request(customerUser, body(caseLine, 1)).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Sản phẩm đã có yêu cầu đổi trả"));
        jdbc.update("UPDATE shipments SET shipped_date = now() - interval '9 days', delivered_date = now() - interval '8 days' WHERE order_id=?", order);
        request(customerUser, body(phoneLine, 1)).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("quá 7 ngày")));
        jdbc.update("UPDATE shipments SET delivered_date = now() WHERE order_id=?", order);
        jdbc.update("UPDATE orders SET status='SHIPPED' WHERE order_id=?", order);
        request(customerUser, body(phoneLine, 1)).andExpect(status().isConflict());
        request(customerUser, "{\"orderId\":\"" + order + "\",\"reason\":\" \",\"items\":[]}").andExpect(status().isBadRequest());
    }

    @Test void rejectionNeedsAReasonAndFreesTheItemsForANewRequest() throws Exception {
        request(customerUser, body(phoneLine, 1)).andExpect(status().isCreated());
        UUID id = returnId();
        move(id, salesUser, "SALES_STAFF", "{\"status\":\"REJECTED\"}").andExpect(status().isBadRequest());
        move(id, salesUser, "SALES_STAFF", "{\"status\":\"REJECTED\",\"note\":\"Máy có dấu hiệu rơi vỡ\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectionReason").value("Máy có dấu hiệu rơi vỡ")).andExpect(jsonPath("$.reason").value("Máy bị lỗi màn hình"));
        move(id, managerUser, "MANAGER", "{\"status\":\"APPROVED\"}").andExpect(status().isConflict());
        mvc.perform(get("/api/returns/mine").with(user(as(customerUser, "CUSTOMER")))).andExpect(jsonPath("$.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.content[0].allowedStatuses").isEmpty());
        mvc.perform(get("/api/returns/mine/{id}", id).with(user(as(otherUser, "CUSTOMER")))).andExpect(status().isNotFound());
        request(customerUser, body(phoneLine, 1)).andExpect(status().isCreated());
        mvc.perform(get("/api/returns").param("status", "REQUESTED").with(user(as(keeperUser, "WAREHOUSE_KEEPER"))))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].allowedStatuses").isEmpty());
        mvc.perform(get("/api/returns").with(user(as(customerUser, "CUSTOMER")))).andExpect(status().isForbidden());
    }

    // ---- V14 exchange ----
    String exchangeBody(UUID line, int quantity, UUID wanted, int wantedQuantity) {
        return "{\"orderId\":\"" + order + "\",\"reason\":\"Muốn đổi mẫu khác\",\"type\":\"EXCHANGE\",\"items\":[{\"orderItemId\":\"" + line
                + "\",\"quantity\":" + quantity + "}],\"exchangeItems\":[{\"variantId\":\"" + wanted + "\",\"quantity\":" + wantedQuantity + "}]}";
    }
    UUID approveAndReceive(String condition) throws Exception {
        UUID id = returnId();
        move(id, salesUser, "SALES_STAFF", "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
        move(id, keeperUser, "WAREHOUSE_KEEPER", "{\"status\":\"RECEIVED\",\"conditions\":[{\"returnItemId\":\"" + returnItemId(id) + "\",\"condition\":\"" + condition + "\"}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowedStatuses").isEmpty());
        return id;
    }

    @Test void customerExchangesTwoCasesForAHeadsetAndPaysTheDifferenceOnDelivery() throws Exception {
        request(customerUser, exchangeBody(caseLine, 2, headsetVariant, 1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestType").value("EXCHANGE")).andExpect(jsonPath("$.refundAmount").value(400000))
                .andExpect(jsonPath("$.exchangeItems[0].sku").value("TN-Y")).andExpect(jsonPath("$.exchangeItems[0].currentPrice").value(1000000));
        UUID id = approveAndReceive("RESTOCK");
        mvc.perform(get("/api/returns/{id}", id).with(user(as(salesUser, "SALES_STAFF"))))
                .andExpect(jsonPath("$.allowedStatuses").value(org.hamcrest.Matchers.contains("EXCHANGED")));
        move(id, managerUser, "MANAGER", "{\"status\":\"REFUNDED\"}").andExpect(status().isConflict());
        move(id, salesUser, "SALES_STAFF", "{\"status\":\"EXCHANGED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXCHANGED")).andExpect(jsonPath("$.exchangeDifference").value(600000))
                .andExpect(jsonPath("$.exchangeOrderCode").value(org.hamcrest.Matchers.startsWith("EX-")));
        request(customerUser, body(caseLine, 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Sản phẩm đã có yêu cầu đổi trả"));
        request(customerUser, exchangeBody(caseLine, 1, headsetVariant, 1)).andExpect(status().isConflict());
        UUID replacement = jdbc.queryForObject("SELECT exchange_order_id FROM return_requests WHERE return_id=?", UUID.class, id);
        var placed = orderService.detail(customerUser, true, replacement);
        assertThat(placed.getStatus().name()).isEqualTo("CONFIRMED");
        assertThat(placed.getSalesChannel().name()).isEqualTo("ONLINE");
        assertThat(placed.getTotalAmount()).isEqualByComparingTo("1000000");
        assertThat(placed.getPayment().getAmount()).as("only the difference is collected").isEqualByComparingTo("600000");
        assertThat(placed.getPayment().getStatus().name()).isEqualTo("PENDING");
        assertThat(placed.getExchangeCredit()).isEqualByComparingTo("400000");
        assertThat(placed.getExchangeFromReturnCode()).startsWith("TH-");
        assertThat(stock(caseVariant)).as("returned cases back on sale").isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM inventory WHERE variant_id=?", Integer.class, headsetVariant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?", String.class, order)).as("original payment untouched").isEqualTo("PAID");
        assertThat(reports.summary(lastMonth()).getRevenue()).as("original net of the returned cases").isEqualByComparingTo("10000000");

        for (var next : java.util.List.of(com.example.backend.order.entity.OrderStatus.PROCESSING, com.example.backend.order.entity.OrderStatus.SHIPPED,
                com.example.backend.order.entity.OrderStatus.DELIVERED)) orderService.status(managerUser, false, replacement, next);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?", String.class, replacement)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT total_amount FROM invoices WHERE order_id=?", java.math.BigDecimal.class, replacement)).isEqualByComparingTo("1000000");
        assertThat(reports.summary(lastMonth()).getRevenue()).as("10.4M paid + 0.6M difference").isEqualByComparingTo("11000000");
        assertThat(stock(headsetVariant)).isEqualTo(4);
    }

    @Test void exchangeForSomethingCheaperRefundsTheDifferenceAndCounterOrdersCompleteAtOnce() throws Exception {
        jdbc.update("UPDATE orders SET sales_channel='IN_STORE' WHERE order_id=?", order);
        request(customerUser, exchangeBody(phoneLine, 1, headsetVariant, 2)).andExpect(status().isCreated());
        UUID id = approveAndReceive("DEFECTIVE");
        move(id, salesUser, "SALES_STAFF", "{\"status\":\"EXCHANGED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeDifference").value(-8000000));
        UUID replacement = jdbc.queryForObject("SELECT exchange_order_id FROM return_requests WHERE return_id=?", UUID.class, id);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_id=?", String.class, replacement)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT payment_method || ':' || status || ':' || amount FROM payments WHERE order_id=?", String.class, replacement))
                .isEqualTo("CASH:PAID:0.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id=?", Integer.class, replacement)).isEqualTo(1);
        assertThat(stock(headsetVariant)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM warranties WHERE serial_id=?", String.class, serial)).isEqualTo("VOIDED");
        assertThat(reports.summary(lastMonth()).getRevenue()).as("0.4M cases + 2M headsets").isEqualByComparingTo("2400000");
    }

    @Test void exchangeRequestsAreValidated() throws Exception {
        request(customerUser, "{\"orderId\":\"" + order + "\",\"reason\":\"Đổi\",\"type\":\"EXCHANGE\",\"items\":[{\"orderItemId\":\"" + caseLine + "\",\"quantity\":1}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Vui lòng chọn sản phẩm muốn đổi sang"));
        request(customerUser, "{\"orderId\":\"" + order + "\",\"reason\":\"Trả\",\"items\":[{\"orderItemId\":\"" + caseLine + "\",\"quantity\":1}],"
                + "\"exchangeItems\":[{\"variantId\":\"" + headsetVariant + "\",\"quantity\":1}]}").andExpect(status().isBadRequest());
        request(customerUser, exchangeBody(caseLine, 1, headsetVariant, 6)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không đủ hàng")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM return_requests", Integer.class)).isZero();
    }
}
