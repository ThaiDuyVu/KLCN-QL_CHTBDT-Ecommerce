package com.example.backend.goodsreceipt.integration;

import com.example.backend.goodsreceipt.entity.GoodsReceiptStatus;
import com.example.backend.goodsreceipt.exception.InvalidGoodsReceiptStatusTransitionException;
import com.example.backend.goodsreceipt.service.GoodsReceiptService;
import com.example.backend.inventory.entity.Inventory;
import com.example.backend.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class GoodsReceiptInventoryIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID CATEGORY_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID BRAND_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID PRODUCT_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID VARIANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000006");
    private static final UUID SUPPLIER_ID = UUID.fromString("10000000-0000-0000-0000-000000000007");
    private static final UUID WAREHOUSE_ID = UUID.fromString("10000000-0000-0000-0000-000000000008");

    @Container
    private static final PostgreSQLContainer<?> postgresql =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("goods_receipt_inventory_test")
                    .withUsername("goods_receipt_inventory_test")
                    .withPassword("goods_receipt_inventory_test");

    @Autowired
    private GoodsReceiptService goodsReceiptService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
        registry.add("app.jwt.secret", () -> "test-jwt-secret-for-goods-receipt-inventory-tests");
    }

    @BeforeEach
    void setUpTestData() {
        clearTestData();

        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            user_id, username, password, email, display_name, status
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                USER_ID, "inventorytester", "not-used", "inventory@example.test",
                "Nhân viên kiểm kho", "ACTIVE"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO employees (employee_id, user_id, employee_code, full_name)
                        VALUES (?, ?, ?, ?)
                        """,
                EMPLOYEE_ID, USER_ID, "NV-KHO-TEST", "Nhân viên kiểm kho"
        );
        jdbcTemplate.update(
                "INSERT INTO categories (category_id, category_name) VALUES (?, ?)",
                CATEGORY_ID, "Thiết bị kiểm thử"
        );
        jdbcTemplate.update(
                "INSERT INTO brands (brand_id, brand_name) VALUES (?, ?)",
                BRAND_ID, "Hãng kiểm thử"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO products (
                            product_id, category_id, brand_id, product_name, status
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                PRODUCT_ID, CATEGORY_ID, BRAND_ID, "Sản phẩm kiểm thử", "ACTIVE"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO product_variants (
                            variant_id, product_id, sku, price, cost_price
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                VARIANT_ID, PRODUCT_ID, "SKU-INVENTORY-TEST", 1000, 800
        );
        jdbcTemplate.update(
                """
                        INSERT INTO suppliers (
                            supplier_id, supplier_code, supplier_name
                        ) VALUES (?, ?, ?)
                        """,
                SUPPLIER_ID, "NCC-TEST", "Nhà cung cấp kiểm thử"
        );
        jdbcTemplate.update(
                "INSERT INTO warehouses (warehouse_id, warehouse_name) VALUES (?, ?)",
                WAREHOUSE_ID, "Kho kiểm thử"
        );
    }

    @AfterEach
    void cleanUpTestData() {
        clearTestData();
    }

    @Test
    void confirmation_shouldCreateInventoryAndApplyReceiptOnlyOnce() {
        UUID receiptId = UUID.randomUUID();
        insertReceipt(receiptId, "GR-INVENTORY-1", 5);

        goodsReceiptService.updateGoodsReceiptStatus(receiptId, GoodsReceiptStatus.CONFIRMED);

        assertThat(inventoryQuantity()).isEqualTo(5);
        assertThat(inventoryReservedQuantity()).isZero();
        assertThat(inventory().getUpdatedAt()).isNotNull();
        assertThat(receiptStatus(receiptId)).isEqualTo("CONFIRMED");

        assertThatThrownBy(() -> goodsReceiptService.updateGoodsReceiptStatus(
                receiptId, GoodsReceiptStatus.CONFIRMED
        )).isInstanceOf(InvalidGoodsReceiptStatusTransitionException.class);
        assertThat(inventoryQuantity()).isEqualTo(5);
    }

    @Test
    void confirmation_shouldPreserveReservedQuantityWhenIncrementingExistingInventory() {
        UUID receiptId = UUID.randomUUID();
        insertReceipt(receiptId, "GR-INVENTORY-2", 4);
        insertInventory(10, 3);

        goodsReceiptService.updateGoodsReceiptStatus(receiptId, GoodsReceiptStatus.CONFIRMED);

        assertThat(inventoryQuantity()).isEqualTo(14);
        assertThat(inventoryReservedQuantity()).isEqualTo(3);
    }

    @Test
    void concurrentConfirmationOfSameReceipt_shouldIncrementInventoryOnce() throws Exception {
        UUID receiptId = UUID.randomUUID();
        insertReceipt(receiptId, "GR-INVENTORY-3", 7);

        List<Throwable> failures = runConcurrently(
                () -> goodsReceiptService.updateGoodsReceiptStatus(
                        receiptId, GoodsReceiptStatus.CONFIRMED
                ),
                () -> goodsReceiptService.updateGoodsReceiptStatus(
                        receiptId, GoodsReceiptStatus.CONFIRMED
                )
        );

        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst())
                .isInstanceOf(InvalidGoodsReceiptStatusTransitionException.class);
        assertThat(inventoryQuantity()).isEqualTo(7);
        assertThat(receiptStatus(receiptId)).isEqualTo("CONFIRMED");
    }

    @Test
    void concurrentDistinctReceipts_shouldNotLoseInventoryIncrements() throws Exception {
        UUID firstReceiptId = UUID.randomUUID();
        UUID secondReceiptId = UUID.randomUUID();
        insertReceipt(firstReceiptId, "GR-INVENTORY-4", 2);
        insertReceipt(secondReceiptId, "GR-INVENTORY-5", 3);

        List<Throwable> failures = runConcurrently(
                () -> goodsReceiptService.updateGoodsReceiptStatus(
                        firstReceiptId, GoodsReceiptStatus.CONFIRMED
                ),
                () -> goodsReceiptService.updateGoodsReceiptStatus(
                        secondReceiptId, GoodsReceiptStatus.CONFIRMED
                )
        );

        assertThat(failures).isEmpty();
        assertThat(inventoryQuantity()).isEqualTo(5);
    }

    @Test
    void inventoryFailure_shouldRollBackReceiptConfirmation() {
        UUID receiptId = UUID.randomUUID();
        insertReceipt(receiptId, "GR-INVENTORY-6", 1);
        insertInventory(Integer.MAX_VALUE, 0);

        assertThatThrownBy(() -> goodsReceiptService.updateGoodsReceiptStatus(
                receiptId, GoodsReceiptStatus.CONFIRMED
        )).isInstanceOf(RuntimeException.class);

        assertThat(inventoryQuantity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(receiptStatus(receiptId)).isEqualTo("DRAFT");
    }

    private void insertReceipt(UUID receiptId, String receiptCode, int quantity) {
        jdbcTemplate.update(
                """
                        INSERT INTO goods_receipts (
                            receipt_id, receipt_code, supplier_id, warehouse_id,
                            employee_id, total_amount, status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                receiptId, receiptCode, SUPPLIER_ID, WAREHOUSE_ID,
                EMPLOYEE_ID, quantity * 800L, "DRAFT"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO goods_receipt_items (
                            receipt_id, variant_id, quantity, unit_cost
                        ) VALUES (?, ?, ?, ?)
                        """,
                receiptId, VARIANT_ID, quantity, 800
        );
    }

    private void insertInventory(int quantity, int reservedQuantity) {
        jdbcTemplate.update(
                """
                        INSERT INTO inventory (
                            warehouse_id, variant_id, quantity, reserved_quantity
                        ) VALUES (?, ?, ?, ?)
                        """,
                WAREHOUSE_ID, VARIANT_ID, quantity, reservedQuantity
        );
    }

    private int inventoryQuantity() {
        return inventory().getQuantity();
    }

    private int inventoryReservedQuantity() {
        return inventory().getReservedQuantity();
    }

    private Inventory inventory() {
        return inventoryRepository
                .findByWarehouse_WarehouseIdAndVariant_VariantId(WAREHOUSE_ID, VARIANT_ID)
                .orElseThrow();
    }

    private String receiptStatus(UUID receiptId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM goods_receipts WHERE receipt_id = ?",
                String.class,
                receiptId
        );
    }

    private List<Throwable> runConcurrently(
            Runnable firstAction,
            Runnable secondAction
    ) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Throwable> first = executor.submit(() -> runAfter(start, firstAction));
            Future<Throwable> second = executor.submit(() -> runAfter(start, secondAction));
            start.countDown();

            return Stream.of(
                            first.get(20, TimeUnit.SECONDS),
                            second.get(20, TimeUnit.SECONDS)
                    )
                    .filter(failure -> failure != null)
                    .toList();
        }
    }

    private Throwable runAfter(CountDownLatch start, Runnable action) {
        try {
            start.await(10, TimeUnit.SECONDS);
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private void clearTestData() {
        jdbcTemplate.update("DELETE FROM inventory WHERE warehouse_id = ?", WAREHOUSE_ID);
        jdbcTemplate.update(
                "DELETE FROM goods_receipt_items WHERE receipt_id IN (SELECT receipt_id FROM goods_receipts WHERE warehouse_id = ?)",
                WAREHOUSE_ID
        );
        jdbcTemplate.update("DELETE FROM goods_receipts WHERE warehouse_id = ?", WAREHOUSE_ID);
        jdbcTemplate.update("DELETE FROM suppliers WHERE supplier_id = ?", SUPPLIER_ID);
        jdbcTemplate.update("DELETE FROM warehouses WHERE warehouse_id = ?", WAREHOUSE_ID);
        jdbcTemplate.update("DELETE FROM product_variants WHERE variant_id = ?", VARIANT_ID);
        jdbcTemplate.update("DELETE FROM products WHERE product_id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM brands WHERE brand_id = ?", BRAND_ID);
        jdbcTemplate.update("DELETE FROM categories WHERE category_id = ?", CATEGORY_ID);
        jdbcTemplate.update("DELETE FROM employees WHERE employee_id = ?", EMPLOYEE_ID);
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", USER_ID);
    }
}
