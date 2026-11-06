package com.example.backend.review;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P1.4: customers review what they received; staff reply and moderate. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "app.seed.auth.enabled=false", "app.payment.vnpay.enabled=false", "app.order.vnpay-expiry.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class ReviewIntegrationTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("reviews_test").withUsername("reviews_test").withPassword("reviews_test");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-reviews-jwt-secret-value-long-enough");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID buyer, other, sales, product, deliveredLine, pendingLine, deliveredOrder;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE reviews, order_items, payments, orders, product_variants, products, brands, categories, employees, customers, users CASCADE");
        buyer = account("khach.an"); other = account("khach.binh"); sales = account("banhang");
        jdbc.update("INSERT INTO employees(user_id,employee_code,full_name) VALUES (?,'NV0001','Lê Bán Hàng')", sales);
        UUID buyerCustomer = jdbc.queryForObject("INSERT INTO customers(user_id,full_name) VALUES (?, 'Nguyễn Văn An') RETURNING customer_id", UUID.class, buyer);
        jdbc.update("INSERT INTO customers(user_id,full_name) VALUES (?, 'Trần Thị Bình')", other);
        UUID category = jdbc.queryForObject("INSERT INTO categories(category_name) VALUES ('Tai nghe') RETURNING category_id", UUID.class);
        UUID brand = jdbc.queryForObject("INSERT INTO brands(brand_name) VALUES ('Hãng thử') RETURNING brand_id", UUID.class);
        product = jdbc.queryForObject("INSERT INTO products(category_id,brand_id,product_name,status) VALUES (?,?,'Tai nghe Bluetooth','ACTIVE') RETURNING product_id", UUID.class, category, brand);
        UUID variant = jdbc.queryForObject("INSERT INTO product_variants(product_id,sku,price,cost_price) VALUES (?,'TN-1',500000,300000) RETURNING variant_id", UUID.class, product);
        deliveredOrder = order(buyerCustomer, "DELIVERED");
        deliveredLine = line(deliveredOrder, variant);
        pendingLine = line(order(buyerCustomer, "PENDING"), variant);
    }

    UUID account(String username) {
        return jdbc.queryForObject("INSERT INTO users(username,password,email,status) VALUES (?,'test-only-hash',?,'ACTIVE') RETURNING user_id", UUID.class, username, username + "@example.test");
    }
    UUID order(UUID customer, String status) {
        return jdbc.queryForObject("INSERT INTO orders(customer_id,order_code,recipient_name,recipient_phone,shipping_address,status) VALUES (?,?,'An','0901234567','TP.HCM',?) RETURNING order_id",
                UUID.class, customer, "ORD-" + UUID.randomUUID(), status);
    }
    UUID line(UUID order, UUID variant) {
        return jdbc.queryForObject("INSERT INTO order_items(order_id,variant_id,quantity,cost_price,unit_price,final_unit_price) VALUES (?,?,1,300000,500000,500000) RETURNING order_item_id", UUID.class, order, variant);
    }
    static AuthenticatedUserPrincipal as(UUID userId, String role) {
        var u = new User(); u.setUserId(userId); u.setUsername("actor"); u.setStatus("ACTIVE");
        return new AuthenticatedUserPrincipal(u, role);
    }
    ResultActions review(UUID userId, UUID line, int rating) throws Exception {
        return mvc.perform(post("/api/reviews").with(user(as(userId, "CUSTOMER"))).with(csrf()).contentType("application/json")
                .content("{\"orderItemId\":\"" + line + "\",\"rating\":" + rating + ",\"reviewText\":\"  Âm thanh tốt, pin lâu  \"}"));
    }

    @Test void buyerReviewsADeliveredItemOnceAndItShowsOnTheProduct() throws Exception {
        review(buyer, deliveredLine, 4).andExpect(status().isCreated()).andExpect(jsonPath("$.reviewText").value("Âm thanh tốt, pin lâu"))
                .andExpect(jsonPath("$.productName").value("Tai nghe Bluetooth")).andExpect(jsonPath("$.customerName").value("Nguyễn Văn An"));
        review(buyer, deliveredLine, 5).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/products/{id}/reviews", product).with(user(as(other, "CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(4.0)).andExpect(jsonPath("$.totalReviews").value(1))
                .andExpect(jsonPath("$.content[0].rating").value(4));
        mvc.perform(get("/api/reviews/mine/by-order/{id}", deliveredOrder).with(user(as(buyer, "CUSTOMER"))))
                .andExpect(jsonPath("$[0].orderItemId").value(deliveredLine.toString()));
    }

    @Test void onlyTheBuyerOfADeliveredItemMayReview() throws Exception {
        review(other, deliveredLine, 5).andExpect(status().isNotFound());
        review(buyer, pendingLine, 5).andExpect(status().isConflict());
        review(buyer, deliveredLine, 6).andExpect(status().isBadRequest());
        mvc.perform(post("/api/reviews").with(user(as(sales, "SALES_STAFF"))).with(csrf()).contentType("application/json")
                .content("{\"orderItemId\":\"" + deliveredLine + "\",\"rating\":5}")).andExpect(status().isForbidden());
    }

    @Test void staffReplyAndHiddenReviewsLeaveTheProductPage() throws Exception {
        review(buyer, deliveredLine, 2).andExpect(status().isCreated());
        UUID id = jdbc.queryForObject("SELECT review_id FROM reviews", UUID.class);
        mvc.perform(patch("/api/reviews/{id}", id).with(user(as(sales, "SALES_STAFF"))).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/reviews/{id}", id).with(user(as(sales, "SALES_STAFF"))).with(csrf()).contentType("application/json")
                        .content("{\"replyText\":\"Cửa hàng xin lỗi, mời anh mang máy đến kiểm tra\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.repliedBy").value("Lê Bán Hàng")).andExpect(jsonPath("$.repliedAt").isNotEmpty());
        mvc.perform(patch("/api/reviews/{id}", id).with(user(as(sales, "SALES_STAFF"))).with(csrf()).contentType("application/json")
                .content("{\"status\":\"HIDDEN\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HIDDEN"));
        mvc.perform(get("/api/v1/products/{id}/reviews", product).with(user(as(other, "CUSTOMER"))))
                .andExpect(jsonPath("$.totalReviews").value(0)).andExpect(jsonPath("$.averageRating").doesNotExist());
        mvc.perform(get("/api/reviews").param("status", "HIDDEN").with(user(as(sales, "SALES_STAFF")))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/reviews").with(user(as(buyer, "CUSTOMER")))).andExpect(status().isForbidden());
    }
}
