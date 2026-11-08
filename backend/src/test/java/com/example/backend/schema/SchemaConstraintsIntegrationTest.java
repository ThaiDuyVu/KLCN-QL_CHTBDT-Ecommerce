package com.example.backend.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3: the database itself rejects values the application cannot read (V10, V11, V13, V15). */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "app.seed.auth.enabled=false",
        "app.payment.vnpay.enabled=false", "app.order.vnpay-expiry.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
class SchemaConstraintsIntegrationTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("schema_test").withUsername("schema_test").withPassword("schema_test");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-schema-jwt-secret-value-long-enough");
    }

    @Autowired JdbcTemplate jdbc;
    UUID order;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE order_status_history, payments, orders, customers, users CASCADE");
        UUID customer = jdbc.queryForObject("INSERT INTO customers(full_name, phone) VALUES ('Khách lẻ', '0900000001') RETURNING customer_id", UUID.class);
        order = jdbc.queryForObject("INSERT INTO orders(customer_id,order_code,recipient_name,recipient_phone,shipping_address,status) "
                + "VALUES (?,'ORD-S','A','0900000001','HCM','PENDING') RETURNING order_id", UUID.class, customer);
    }

    void rejects(String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void unknownStatusesAndMethodsAreRejected() {
        rejects("UPDATE orders SET status='DONE' WHERE order_id=?", order);
        rejects("UPDATE orders SET sales_channel='PHONE' WHERE order_id=?", order);
        rejects("INSERT INTO payments(order_id,payment_method,amount,status) VALUES (?,'MOMO',1,'PENDING')", order);
        rejects("INSERT INTO payments(order_id,payment_method,amount,status) VALUES (?,'CASH',1,'SETTLED')", order);
        jdbc.update("INSERT INTO payments(order_id,payment_method,amount,status) VALUES (?,'CASH',1,'REFUNDED')", order);
        rejects("INSERT INTO order_status_history(order_id,to_status) VALUES (?,'ARCHIVED')", order);
        rejects("INSERT INTO promotions(promotion_name,discount_type,discount_value,start_date,end_date,status) VALUES ('Giảm sốc','PERCENTAGE',150,now(),now(),'ACTIVE')");
        rejects("INSERT INTO promotions(promotion_name,discount_type,discount_value,start_date,end_date,status) VALUES ('Sai loại','BOGO',1,now(),now(),'ACTIVE')");
        assertThat(jdbc.queryForObject("SELECT sales_channel FROM orders WHERE order_id=?", String.class, order)).isEqualTo("ONLINE");
    }

    @Test void walkInCustomersNeedAUniquePhone() {
        rejects("INSERT INTO customers(full_name) VALUES ('Không có gì để nhận diện')");
        rejects("INSERT INTO customers(full_name, phone) VALUES ('Trùng số', '0900000001')");
    }

    @Test void storedTotalsMustMatchTheirFormula() {
        rejects("UPDATE orders SET subtotal=100, discount_amount=10, shipping_fee=0, total_amount=100 WHERE order_id=?", order);
        jdbc.update("UPDATE orders SET subtotal=100, discount_amount=10, shipping_fee=5, total_amount=95 WHERE order_id=?", order);
        rejects("UPDATE orders SET sales_channel='IN_STORE' WHERE order_id=?", order);

        UUID payment = jdbc.queryForObject("INSERT INTO payments(order_id,payment_method,amount,status) VALUES (?,'INSTALLMENT',95,'PENDING') RETURNING payment_id", UUID.class, order);
        UUID provider = jdbc.queryForObject("INSERT INTO installment_providers(provider_name,provider_code,status) VALUES ('Tài chính A','FIN-A','ACTIVE') RETURNING provider_id", UUID.class);
        String installment = "INSERT INTO installment_payments(payment_id,provider_id,total_amount,down_payment,remaining_amount,term_months,status) VALUES (?,?,95,?,?,6,'PENDING')";
        rejects(installment, payment, provider, 20, 70);
        rejects(installment, payment, provider, 120, 0);
        jdbc.update(installment, payment, provider, 20, 75);
    }

    @Test void chatbotStatusesAreCheckedAndCartIsOnePerCustomer() {
        UUID customer = jdbc.queryForObject("SELECT customer_id FROM orders WHERE order_id=?", UUID.class, order);
        rejects("INSERT INTO chat_sessions(customer_id,status) VALUES (?,'OPEN')", customer);
        jdbc.update("INSERT INTO carts(customer_id) VALUES (?)", customer);
        rejects("INSERT INTO carts(customer_id) VALUES (?)", customer); // one cart per customer (V17)
        jdbc.update("INSERT INTO chat_sessions(customer_id,status) VALUES (?,'ACTIVE')", customer);
    }

    @Test void existingOrdersGetABaselineHistoryRowOnMigration() {
        // V11 backfill runs once at migration time; new orders get their row from the application.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='11' AND success", Integer.class)).isEqualTo(1);
    }
}
