package com.example.backend.backup;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import org.junit.jupiter.api.BeforeAll;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P2.2: real pg_dump/pg_restore round trip, run inside the PostgreSQL 16 container
 * (the recommended app.backup.command-prefix = "docker exec -i -e PGPASSWORD &lt;container&gt;").
 */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "app.seed.auth.enabled=false", "app.payment.vnpay.enabled=false", "app.order.vnpay-expiry.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class BackupIntegrationTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("backup_test").withUsername("backup_test").withPassword("backup_test");
    static Path dir;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        dir = Files.createTempDirectory("backup-test");
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-backup-jwt-secret-value-long-enough");
        registry.add("app.backup.dir", () -> dir.toString());
        registry.add("app.backup.command-prefix", () -> "docker exec -i -e PGPASSWORD " + database.getContainerId());
        registry.add("app.backup.host", () -> "localhost");
        registry.add("app.backup.port", () -> "5432");
    }

    @BeforeAll static void dockerCli() throws Exception {
        boolean available;
        try {
            Process p = new ProcessBuilder("docker", "version").redirectErrorStream(true).start();
            available = p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            available = false;
        }
        assumeTrue(available, "docker CLI not available");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    static AuthenticatedUserPrincipal as(String role) {
        var u = new User(); u.setUserId(UUID.randomUUID()); u.setUsername("actor"); u.setStatus("ACTIVE");
        return new AuthenticatedUserPrincipal(u, role);
    }

    @Test void adminBacksUpAndRestoresTheWholeDatabase() throws Exception {
        jdbc.update("INSERT INTO brands(brand_name) VALUES ('Hãng trước sao lưu')");
        String body = mvc.perform(post("/api/admin/backups").with(user(as("ADMIN"))).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.automatic").value(false))
                .andReturn().getResponse().getContentAsString();
        String name = body.replaceAll(".*\"name\":\"([^\"]+)\".*", "$1");
        assertThat(name).matches("backup-\\d{8}-\\d{6}\\.dump");

        byte[] file = mvc.perform(get("/api/admin/backups/{n}/download", name).with(user(as("ADMIN"))))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(name)))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(file, 0, 5)).isEqualTo("PGDMP");

        jdbc.update("DELETE FROM brands WHERE brand_name='Hãng trước sao lưu'");
        jdbc.update("INSERT INTO brands(brand_name) VALUES ('Hãng sau sao lưu')");

        mvc.perform(post("/api/admin/backups/{n}/restore", name).with(user(as("ADMIN"))).with(csrf()).contentType("application/json")
                .content("{\"confirmName\":\"sai-ten.dump\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/backups/{n}/restore", name).with(user(as("ADMIN"))).with(csrf()).contentType("application/json")
                        .content("{\"confirmName\":\"" + name + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.restored").value(name))
                .andExpect(jsonPath("$.safetyBackup.automatic").value(true));

        assertThat(jdbc.queryForList("SELECT brand_name FROM brands", String.class))
                .contains("Hãng trước sao lưu").doesNotContain("Hãng sau sao lưu");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isGreaterThan(5);
        mvc.perform(get("/api/admin/backups").with(user(as("ADMIN"))))
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[*].automatic").value(org.hamcrest.Matchers.hasItem(true)));
    }

    @Test void onlyAdminsAndOnlyGeneratedNames() throws Exception {
        mvc.perform(get("/api/admin/backups").with(user(as("MANAGER")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/backups").with(user(as("MANAGER"))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/backups/{n}/download", "..%2F..%2Fetc%2Fpasswd").with(user(as("ADMIN")))).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/admin/backups/{n}/download", "backup-20990101-000000.dump").with(user(as("ADMIN")))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/backups/{n}", "evil.sh").with(user(as("ADMIN"))).with(csrf())).andExpect(status().isBadRequest());
    }
}
