package com.example.backend.auth.integration;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthRefreshIntegrationTest {

    private static final UUID ACTIVE_USER_ID = UUID.fromString(
            "38d8eb3b-bf83-4515-8a92-59791070b397"
    );
    private static final UUID ROLE_ID = UUID.fromString(
            "ca75edf4-25e2-4dc1-bd3d-3cfdc111bf43"
    );

    private static final String USERNAME = "lehoangnam";
    private static final String PASSWORD = "Password123!";
    private static final String DISPLAY_NAME = "Lê Hoàng Nam";
    private static final String ROLE_NAME = "ROLE_TEST";

    @Container
    private static final PostgreSQLContainer<?> postgresql =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("auth_refresh_test")
                    .withUsername("auth_refresh_test")
                    .withPassword("auth_refresh_test");

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
                () -> "test-jwt-secret-for-auth-refresh-integration-tests"
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
                "Vai trò chỉ dùng cho kiểm thử làm mới phiên"
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
                "lehoangnam@example.test",
                DISPLAY_NAME,
                "0903456789",
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
    void refreshWithoutCsrf_shouldReturnForbidden() throws Exception {
        LoginCookies loginCookies = login();

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(loginCookies.refreshTokenCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshWithoutRefreshCookie_shouldReturnUnauthorizedAndClearAuthenticationCookies()
            throws Exception {
        Cookie csrfCookie = obtainCsrfCookie();

        MvcResult result = performRefresh(csrfCookie);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertAuthenticationCookiesAreCleared(result);
    }

    @Test
    void successfulRefresh_shouldRotatePersistedRefreshToken() throws Exception {
        LoginCookies loginCookies = login();
        UUID originalRefreshTokenId = findOnlyRefreshTokenId();

        MvcResult result = performRefresh(
                loginCookies.csrfCookie(),
                loginCookies.accessTokenCookie(),
                loginCookies.refreshTokenCookie()
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertSafeSessionResponse(result);
        assertThat(hasSetCookie(
                result,
                authCookieProperties.getAccessTokenCookieName()
        )).isTrue();
        assertThat(hasSetCookie(
                result,
                authCookieProperties.getRefreshTokenCookieName()
        )).isTrue();
        assertThat(countRotatedTokenRows(originalRefreshTokenId)).isEqualTo(1L);
        assertThat(countSuccessorRecords(originalRefreshTokenId)).isEqualTo(1L);
        assertThat(countRefreshTokens()).isEqualTo(2L);
        assertThat(countRefreshTokensWithHash(
                loginCookies.refreshTokenCookie().getValue()
        )).isZero();
    }

    @Test
    void replayingRotatedRefreshToken_shouldReturnUnauthorizedAndRevokeActiveTokens()
            throws Exception {
        LoginCookies loginCookies = login();

        MvcResult successfulRefresh = performRefresh(
                loginCookies.csrfCookie(),
                loginCookies.accessTokenCookie(),
                loginCookies.refreshTokenCookie()
        );
        assertThat(successfulRefresh.getResponse().getStatus()).isEqualTo(200);

        MvcResult replayResult = performRefresh(
                loginCookies.csrfCookie(),
                loginCookies.refreshTokenCookie()
        );

        assertThat(replayResult.getResponse().getStatus()).isEqualTo(401);
        assertAuthenticationCookiesAreCleared(replayResult);
        assertThat(countActiveRefreshTokens()).isZero();
    }

    @Test
    void refreshAfterUserIsLocked_shouldReturnUnauthorizedWithoutCreatingSuccessor()
            throws Exception {
        LoginCookies loginCookies = login();

        jdbcTemplate.update(
                "UPDATE users SET status = ? WHERE user_id = ?",
                "LOCKED",
                ACTIVE_USER_ID
        );

        MvcResult result = performRefresh(
                loginCookies.csrfCookie(),
                loginCookies.refreshTokenCookie()
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertAuthenticationCookiesAreCleared(result);
        assertThat(countRefreshTokens()).isEqualTo(1L);
        assertThat(countActiveRefreshTokens()).isEqualTo(1L);
    }

    private LoginCookies login() throws Exception {
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

        Cookie accessTokenCookie = loginResult.getResponse().getCookie(
                authCookieProperties.getAccessTokenCookieName()
        );
        Cookie refreshTokenCookie = loginResult.getResponse().getCookie(
                authCookieProperties.getRefreshTokenCookieName()
        );

        assertThat(accessTokenCookie).isNotNull();
        assertThat(refreshTokenCookie).isNotNull();

        return new LoginCookies(
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

    private MvcResult performRefresh(
            Cookie csrfCookie,
            Cookie... authenticationCookies
    ) throws Exception {
        var request = post("/api/auth/refresh")
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

    private void assertSafeSessionResponse(MvcResult result) throws Exception {
        jsonPath("$.userId").value(ACTIVE_USER_ID.toString())
                .match(result);
        jsonPath("$.username").value(USERNAME).match(result);
        jsonPath("$.displayName").value(DISPLAY_NAME).match(result);
        jsonPath("$.roleName").value(ROLE_NAME).match(result);
        jsonPath("$.accessToken").doesNotExist().match(result);
        jsonPath("$.refreshToken").doesNotExist().match(result);
        jsonPath("$.token").doesNotExist().match(result);
        jsonPath("$.tokenType").doesNotExist().match(result);
    }

    private String loginRequest() {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(USERNAME, PASSWORD);
    }

    private UUID findOnlyRefreshTokenId() {
        return jdbcTemplate.queryForObject(
                "SELECT refresh_token_id FROM refresh_tokens WHERE user_id = ?",
                UUID.class,
                ACTIVE_USER_ID
        );
    }

    private long countRotatedTokenRows(UUID refreshTokenId) {
        return count(
                """
                        SELECT COUNT(*)
                        FROM refresh_tokens
                        WHERE refresh_token_id = ?
                          AND revoked_at IS NOT NULL
                          AND replaced_by_token_id IS NOT NULL
                        """,
                refreshTokenId
        );
    }

    private long countSuccessorRecords(UUID refreshTokenId) {
        return count(
                """
                        SELECT COUNT(*)
                        FROM refresh_tokens
                        WHERE refresh_token_id = (
                            SELECT replaced_by_token_id
                            FROM refresh_tokens
                            WHERE refresh_token_id = ?
                        )
                        """,
                refreshTokenId
        );
    }

    private long countRefreshTokensWithHash(String rawRefreshToken) {
        return count(
                """
                        SELECT COUNT(*)
                        FROM refresh_tokens
                        WHERE user_id = ?
                          AND token_hash = ?
                        """,
                ACTIVE_USER_ID,
                rawRefreshToken
        );
    }

    private long countRefreshTokens() {
        return count(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?",
                ACTIVE_USER_ID
        );
    }

    private long countActiveRefreshTokens() {
        return count(
                """
                        SELECT COUNT(*)
                        FROM refresh_tokens
                        WHERE user_id = ?
                          AND revoked_at IS NULL
                          AND expires_at > CURRENT_TIMESTAMP
                        """,
                ACTIVE_USER_ID
        );
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? 0L : count;
    }

    private void assertAuthenticationCookiesAreCleared(MvcResult result) {
        assertThat(hasClearingCookie(
                result,
                authCookieProperties.getAccessTokenCookieName()
        )).isTrue();
        assertThat(hasClearingCookie(
                result,
                authCookieProperties.getRefreshTokenCookieName()
        )).isTrue();
    }

    private boolean hasSetCookie(MvcResult result, String cookieName) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)
                .stream()
                .anyMatch(header -> header.startsWith(cookieName + "="));
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

    private static class LoginCookies {

        private final Cookie csrfCookie;
        private final Cookie accessTokenCookie;
        private final Cookie refreshTokenCookie;

        private LoginCookies(
                Cookie csrfCookie,
                Cookie accessTokenCookie,
                Cookie refreshTokenCookie
        ) {
            this.csrfCookie = csrfCookie;
            this.accessTokenCookie = accessTokenCookie;
            this.refreshTokenCookie = refreshTokenCookie;
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
