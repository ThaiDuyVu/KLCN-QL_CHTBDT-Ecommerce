package com.example.backend.product.integration;

import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.entity.Brand;
import com.example.backend.product.entity.BrandStatus;
import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.ProductImage;
import com.example.backend.product.entity.ProductStatus;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.entity.ProductVariantStatus;
import com.example.backend.product.entity.Specification;
import com.example.backend.product.exception.ProductInUseException;
import com.example.backend.product.exception.ProductReferenceNotFoundException;
import com.example.backend.product.repository.BrandRepository;
import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.service.ProductService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "app.seed.auth.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class ProductPersistenceIntegrationTest {

    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID BRAND_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Container
    private static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("product_persistence_test")
            .withUsername("product_test")
            .withPassword("product_test");

    @Autowired private ProductService service;
    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private Flyway flyway;
    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
        registry.add("app.jwt.secret", () -> "test-jwt-secret-for-product-persistence-tests");
    }

    @BeforeEach
    void setUp() {
        // Only the dedicated ephemeral Testcontainer database is cleared.
        jdbc.execute("TRUNCATE TABLE product_images, specifications, product_variants, products, brands, categories CASCADE");
        jdbc.update("INSERT INTO categories (category_id, category_name) VALUES (?, ?)", CATEGORY_ID, "Thiết bị kiểm thử");
        jdbc.update("INSERT INTO brands (brand_id, brand_name) VALUES (?, ?)", BRAND_ID, "Hãng kiểm thử");
    }

    @Test
    void migrationsAndProductCreation_matchLockedSchema() {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().applied()).hasSize(2);

        var response = service.createProduct(request(null));
        Product persisted = productRepository.findById(response.productId()).orElseThrow();

        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isEqualTo(persisted.getCreatedAt());
        assertThat(service.getProductById(response.productId()).categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(service.getProducts(0, 20).getContent()).extracting(item -> item.productId()).contains(response.productId());
        assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void productEndpoints_requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void pagination_loadsOnlyRequestedProductsAndUsesStableTieBreaker() {
        List<UUID> ids = insertProducts(5);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var first = service.getProducts(0, 2);

        assertThat(statistics.getEntityStatistics(Product.class.getName()).getLoadCount()).isEqualTo(2);
        assertThat(first.getContent()).extracting(item -> item.productId()).containsExactly(ids.get(4), ids.get(3));
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(service.getProducts(1, 2).getContent()).extracting(item -> item.productId())
                .containsExactly(ids.get(2), ids.get(1));
        assertThat(service.getProducts(2, 2).getContent()).extracting(item -> item.productId()).containsExactly(ids.get(0));
        var beyondLast = service.getProducts(3, 2);
        assertThat(beyondLast.getContent()).isEmpty();
        assertThat(beyondLast.getTotalElements()).isEqualTo(5);
        assertThat(beyondLast.getPage()).isEqualTo(3);
    }

    @Test
    void pagination_ordersNewerProductsBeforeUuidTieBreaker() {
        List<UUID> ids = insertProducts(2);
        jdbc.update("UPDATE products SET created_at = ? WHERE product_id = ?",
                OffsetDateTime.parse("2025-01-01T00:00:00Z"), ids.getFirst());

        assertThat(service.getProducts(0, 2).getContent()).extracting(item -> item.productId())
                .containsExactly(ids.getFirst(), ids.getLast());
    }

    @Test
    @WithMockUser
    void getWithoutParameters_returnsAtMostTwentyProductsAndMetadata() throws Exception {
        insertProducts(25);

        mockMvc.perform(get("/api/v1/products").with(user("product-tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "0,2147483647", "2147483647,100"})
    @WithMockUser
    void getProducts_rejectsUnsafeParametersWithActualService(int page, int size) throws Exception {
        mockMvc.perform(get("/api/v1/products").with(user("product-tester"))
                        .param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagination_handlesEmptyCatalog() {
        var response = service.getProducts(0, 20);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
    }

    @Test
    @WithMockUser
    void productWrites_requireCsrfWithActualSecurityFilterChain() throws Exception {
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_changesUpdatedAtButNotCreatedAtAndKeepsOmittedStatus() {
        UUID id = service.createProduct(request("INACTIVE")).productId();
        OffsetDateTime original = OffsetDateTime.parse("2000-01-01T00:00:00Z");
        jdbc.update("UPDATE products SET created_at = ?, updated_at = ? WHERE product_id = ?", original, original, id);

        service.updateProduct(id, new ProductRequest("Thiết bị cập nhật", null, CATEGORY_ID, BRAND_ID, null));

        Product persisted = productRepository.findById(id).orElseThrow();
        assertThat(persisted.getCreatedAt()).isEqualTo(original);
        assertThat(persisted.getUpdatedAt()).isAfter(original);
        assertThat(persisted.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(persisted.getProductName()).isEqualTo("Thiết bị cập nhật");
    }

    @Test
    void failedUpdate_rollsBackAndKeepsOriginalProduct() {
        UUID id = service.createProduct(request(null)).productId();

        assertThatThrownBy(() -> service.updateProduct(id,
                new ProductRequest("Không được lưu", null, CATEGORY_ID, UUID.randomUUID(), null)))
                .isInstanceOf(ProductReferenceNotFoundException.class);

        assertThat(service.getProductById(id).productName()).isEqualTo("Thiết bị mẫu");
    }

    @Test
    void brandAndVariant_statusCanBePersistedAndRead() {
        Brand brand = brandRepository.findById(BRAND_ID).orElseThrow();
        assertThat(brand.getStatus()).isEqualTo(BrandStatus.ACTIVE);
        brand.setStatus(BrandStatus.INACTIVE);
        brandRepository.saveAndFlush(brand);
        assertThat(brandRepository.findById(BRAND_ID).orElseThrow().getStatus()).isEqualTo(BrandStatus.INACTIVE);

        UUID productId = service.createProduct(request(null)).productId();
        UUID variantId = transactionTemplate.execute(transaction -> {
            ProductVariant variant = variant(entityManager.getReference(Product.class, productId), "SKU-STATUS");
            assertThat(variant.getStatus()).isEqualTo(ProductVariantStatus.ACTIVE);
            variant.setStatus(ProductVariantStatus.INACTIVE);
            entityManager.persist(variant);
            entityManager.flush();
            return variant.getVariantId();
        });

        transactionTemplate.executeWithoutResult(transaction -> {
            assertThat(entityManager.find(ProductVariant.class, variantId).getStatus()).isEqualTo(ProductVariantStatus.INACTIVE);
        });
    }

    @Test
    void imagesAndSpecifications_matchSchemaAndPrimaryImageUniqueness() {
        UUID productId = service.createProduct(request(null)).productId();
        UUID imageId = transactionTemplate.execute(transaction -> {
            Product product = entityManager.getReference(Product.class, productId);
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageUrl("https://example.test/den-led.png");
            image.setPrimary(true);
            entityManager.persist(image);
            Specification specification = new Specification();
            specification.setProduct(product);
            specification.setSpecKey("Công suất");
            specification.setSpecValue("12W");
            entityManager.persist(specification);
            entityManager.flush();
            return image.getImageId();
        });

        transactionTemplate.executeWithoutResult(transaction -> {
            assertThat(entityManager.find(ProductImage.class, imageId).getPrimary()).isTrue();
        });
        assertThat(jdbc.queryForObject("SELECT spec_value FROM specifications WHERE product_id = ?", String.class, productId))
                .isEqualTo("12W");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO product_images (product_id, image_url, is_primary) VALUES (?, ?, TRUE)",
                productId, "https://example.test/duplicate.png"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"product_variants", "product_images", "specifications"})
    void delete_rejectsReferencedProductWithoutCascading(String table) {
        UUID id = service.createProduct(request(null)).productId();
        switch (table) {
            case "product_variants" -> jdbc.update(
                    "INSERT INTO product_variants (product_id, sku, price, cost_price) VALUES (?, ?, 100000, 80000)", id, "SKU-DELETE");
            case "product_images" -> jdbc.update(
                    "INSERT INTO product_images (product_id, image_url) VALUES (?, ?)", id, "https://example.test/image.png");
            case "specifications" -> jdbc.update(
                    "INSERT INTO specifications (product_id, spec_key, spec_value) VALUES (?, ?, ?)", id, "Điện áp", "220V");
            default -> throw new IllegalArgumentException(table);
        }

        assertThatThrownBy(() -> service.deleteProduct(id)).isInstanceOf(ProductInUseException.class);
        assertThat(productRepository.existsById(id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE product_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void delete_removesUnreferencedProduct() {
        UUID id = service.createProduct(request(null)).productId();

        service.deleteProduct(id);

        assertThat(productRepository.existsById(id)).isFalse();
    }

    @Test
    void variants_enforceSkuAndNonNegativePriceConstraints() {
        UUID id = service.createProduct(request(null)).productId();
        jdbc.update("INSERT INTO product_variants (product_id, sku, price, cost_price) VALUES (?, ?, 100000, 80000)", id, "SKU-UNIQUE");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO product_variants (product_id, sku, price, cost_price) VALUES (?, ?, 100000, 80000)", id, "SKU-UNIQUE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO product_variants (product_id, sku, price, cost_price) VALUES (?, ?, -1, 80000)", id, "SKU-NEGATIVE-PRICE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO product_variants (product_id, sku, price, cost_price) VALUES (?, ?, 100000, -1)", id, "SKU-NEGATIVE-COST"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ProductRequest request(String status) {
        return new ProductRequest("Thiết bị mẫu", "Mô tả kiểm thử", CATEGORY_ID, BRAND_ID, ProductStatus.fromValue(status));
    }

    private List<UUID> insertProducts(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(index -> {
            UUID id = new UUID(0L, index);
            OffsetDateTime createdAt = OffsetDateTime.parse("2024-01-01T00:00:00Z");
            jdbc.update("""
                    INSERT INTO products (product_id, category_id, brand_id, product_name, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                    """, id, CATEGORY_ID, BRAND_ID, "Thiết bị mẫu " + index, createdAt, createdAt);
            return id;
        }).toList();
    }

    private ProductVariant variant(Product product, String sku) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setPrice(new BigDecimal("100000.00"));
        variant.setCostPrice(new BigDecimal("80000.00"));
        return variant;
    }
}
