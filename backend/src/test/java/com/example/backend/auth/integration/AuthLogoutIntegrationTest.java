package com.example.backend.auth.integration;

import com.jayway.jsonpath.JsonPath;
import com.example.backend.common.security.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthLogoutIntegrationTest {

    private static final UUID ACTIVE_USER_ID = UUID.fromString(
            "65ca4a05-c1d4-4d95-bf23-9f9172d612ea"
    );
    private static final UUID ROLE_ID = UUID.fromString(
            "1eead89b-4097-4ef8-ac37-82a98979f1e8"
    );

    private static final String USERNAME = "phamthuyduong";
    private static final String PASSWORD = "Password123!";
    private static final String DISPLAY_NAME = "Phạm Thùy Dương";
    private static final String ROLE_NAME = "ROLE_TEST";

    @Container
    private static final PostgreSQLContainer<?> postgresql =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("auth_logout_test")
                    .withUsername("auth_logout_test")
                    .withPassword("auth_logout_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthCookieProperties authCookieProperties;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
        registry.add(
                "app.jwt.secret",
                () -> "test-jwt-secret-for-auth-logout-integration-tests"
        );
    }

    @BeforeEach
    void setUpTestData() {
        clearTestData();

        jdbcTemplate.update(
                """
                        INSERT INTO roles (role_id, role_name, description)
                        VALUES (?, ?, ?)
                        """,
                ROLE_ID,
                ROLE_NAME,
                "Vai trò chỉ dùng cho kiểm thử đăng xuất"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            user_id,
                            username,
                            password,
                            email,
                            display_name,
                            phone,
                            status
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                ACTIVE_USER_ID,
                USERNAME,
                passwordEncoder.encode(PASSWORD),
                "phamthuyduong@example.test",
                DISPLAY_NAME,
                "0904567890",
                "ACTIVE"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO user_roles (user_id, role_id)
                        VALUES (?, ?)
                        """,
                ACTIVE_USER_ID,
                ROLE_ID
        );
    }

    @AfterEach
    void cleanUpTestData() {
        clearTestData();
    }

    @Test
    void logoutWithoutCsrf_shouldReturnForbidden() throws Exception {
        LoginSession loginSession = login();

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(
                                loginSession.accessTokenCookie(),
                                loginSession.refreshTokenCookie()
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutAfterLogin_shouldReturnNoContentAndClearCookies() throws Exception {
        LoginSession loginSession = login();

        MvcResult result = performLogout(
                loginSession.csrfCookie(),
                loginSession.accessTokenCookie(),
                loginSession.refreshTokenCookie()
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertCookiesAreCleared(result);
    }

    @Test
    void logoutAfterLogin_shouldRevokePersistedRefreshToken() throws Exception {
        LoginSession loginSession = login();

        MvcResult result = performLogout(
                loginSession.csrfCookie(),
                loginSession.accessTokenCookie(),
                loginSession.refreshTokenCookie()
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(countRevokedRefreshTokens(loginSession.userId()))
                .isEqualTo(1L);
    }

    @Test
    void logoutWithoutAuthenticationCookies_shouldRemainIdempotent()
            throws Exception {
        Cookie csrfCookie = obtainCsrfCookie();

        MvcResult result = performLogout(csrfCookie);

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertCookiesAreCleared(result);
    }

    @Test
    void logoutWithOnlyRefreshTokenCookie_shouldRevokeToken() throws Exception {
        LoginSession loginSession = login();

        MvcResult result = performLogout(
                loginSession.csrfCookie(),
                loginSession.refreshTokenCookie()
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(countRevokedRefreshTokens(loginSession.userId()))
                .isEqualTo(1L);
    }

    private LoginSession login() throws Exception {
        Cookie csrfCookie = obtainCsrfCookie();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest())
                        .cookie(csrfCookie)
                        .header(
                                authCookieProperties.getCsrfHeaderName(),
                                csrfCookie.getValue()
                        ))
                .andExpect(status().isOk())
                .andReturn();

        String userIdValue = JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.userId"
        );
        UUID userId = UUID.fromString(userIdValue);
        Cookie accessTokenCookie = loginResult.getResponse().getCookie(
                authCookieProperties.getAccessTokenCookieName()
        );
        Cookie refreshTokenCookie = loginResult.getResponse().getCookie(
                authCookieProperties.getRefreshTokenCookieName()
        );

        assertThat(userId).isEqualTo(ACTIVE_USER_ID);
        assertThat(accessTokenCookie).isNotNull();
        assertThat(refreshTokenCookie).isNotNull();

        return new LoginSession(
                userId,
                csrfCookie,
                accessTokenCookie,
                refreshTokenCookie
        );
    }

    private Cookie obtainCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie(
                authCookieProperties.getCsrfCookieName()
        );

        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private MvcResult performLogout(
            Cookie csrfCookie,
            Cookie... authenticationCookies
    ) throws Exception {
        var request = post("/api/auth/logout")
                .cookie(csrfCookie)
                .header(
                        authCookieProperties.getCsrfHeaderName(),
                        csrfCookie.getValue()
                );

        if (authenticationCookies.length > 0) {
            request.cookie(authenticationCookies);
        }

        return mockMvc.perform(request).andReturn();
    }

    private String loginRequest() {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(USERNAME, PASSWORD);
    }

    private long countRevokedRefreshTokens(UUID userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM refresh_tokens
                        WHERE user_id = ?
                          AND revoked_at IS NOT NULL
                        """,
                Long.class,
                userId
        );

        return count == null ? 0L : count;
    }

    private void assertCookiesAreCleared(MvcResult result) {
        assertThat(hasClearingCookie(
                result,
                authCookieProperties.getAccessTokenCookieName()
        )).isTrue();
        assertThat(hasClearingCookie(
                result,
                authCookieProperties.getRefreshTokenCookieName()
        )).isTrue();
        assertThat(hasClearingCookie(
                result,
                authCookieProperties.getCsrfCookieName()
        )).isTrue();
    }

    private boolean hasClearingCookie(MvcResult result, String cookieName) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)
                .stream()
                .filter(header -> header.startsWith(cookieName + "="))
                .anyMatch(header -> header.contains("Max-Age=0"));
    }

    private void clearTestData() {
        jdbcTemplate.update(
                "DELETE FROM refresh_tokens WHERE user_id = ?",
                ACTIVE_USER_ID
        );
        jdbcTemplate.update(
                "DELETE FROM user_roles WHERE user_id = ?",
                ACTIVE_USER_ID
        );
        jdbcTemplate.update(
                "DELETE FROM users WHERE user_id = ?",
                ACTIVE_USER_ID
        );
        jdbcTemplate.update(
                "DELETE FROM roles WHERE role_id = ?",
                ROLE_ID
        );
    }

    private static class LoginSession {

        private final UUID userId;
        private final Cookie csrfCookie;
        private final Cookie accessTokenCookie;
        private final Cookie refreshTokenCookie;

        private LoginSession(
                UUID userId,
                Cookie csrfCookie,
                Cookie accessTokenCookie,
                Cookie refreshTokenCookie
        ) {
            this.userId = userId;
            this.csrfCookie = csrfCookie;
            this.accessTokenCookie = accessTokenCookie;
            this.refreshTokenCookie = refreshTokenCookie;
        }

        private UUID userId() {
            return userId;
        }

        private Cookie csrfCookie() {
            return csrfCookie;
        }

        private Cookie accessTokenCookie() {
            return accessTokenCookie;
        }

        private Cookie refreshTokenCookie() {
            return refreshTokenCookie;
        }
    }
}
