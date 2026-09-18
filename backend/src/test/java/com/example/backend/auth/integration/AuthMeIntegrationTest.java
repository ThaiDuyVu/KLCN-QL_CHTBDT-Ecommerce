package com.example.backend.auth.integration;

import com.example.backend.common.security.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class AuthMeIntegrationTest {

    private static final UUID ACTIVE_USER_ID = UUID.fromString(
            "706e5305-ed7f-49d7-9073-af57907d7a12"
    );
    private static final UUID ROLE_ID = UUID.fromString(
            "c59fa5cf-13be-4da2-b85c-d3b9b949a88a"
    );

    private static final String USERNAME = "tranthiminh";
    private static final String PASSWORD = "Password123!";
    private static final String DISPLAY_NAME = "Trần Thị Minh";
    private static final String ROLE_NAME = "ROLE_TEST";

    @Container
    private static final PostgreSQLContainer<?> postgresql =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("auth_me_test")
                    .withUsername("auth_me_test")
                    .withPassword("auth_me_test");

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
                () -> "test-jwt-secret-for-auth-me-integration-tests"
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
                "Vai trò chỉ dùng cho kiểm thử người dùng hiện tại"
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
                "tranthiminh@example.test",
                DISPLAY_NAME,
                "0902345678",
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
    void getCurrentUserWithoutAccessToken_shouldReturnUnauthorized()
            throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUserAfterLogin_shouldReturnSafeCurrentUserSession()
            throws Exception {
        Cookie accessTokenCookie = loginAndGetAccessTokenCookie();

        mockMvc.perform(get("/api/auth/me").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId")
                        .value(ACTIVE_USER_ID.toString()))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.displayName").value(DISPLAY_NAME))
                .andExpect(jsonPath("$.roleName").value(ROLE_NAME))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenType").doesNotExist());
    }

    @Test
    void getCurrentUserWithRefreshTokenOnly_shouldReturnUnauthorized()
            throws Exception {
        Cookie refreshTokenCookie = loginAndGetRefreshTokenCookie();

        mockMvc.perform(get("/api/auth/me").cookie(refreshTokenCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUserWithMalformedAccessToken_shouldReturnUnauthorized()
            throws Exception {
        Cookie malformedAccessTokenCookie = new Cookie(
                authCookieProperties.getAccessTokenCookieName(),
                "not-a-valid-jwt"
        );

        mockMvc.perform(get("/api/auth/me").cookie(malformedAccessTokenCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUserWithOldAccessTokenAfterUserIsLocked_shouldReturnUnauthorized()
            throws Exception {
        Cookie accessTokenCookie = loginAndGetAccessTokenCookie();

        jdbcTemplate.update(
                "UPDATE users SET status = ? WHERE user_id = ?",
                "LOCKED",
                ACTIVE_USER_ID
        );

        mockMvc.perform(get("/api/auth/me").cookie(accessTokenCookie))
                .andExpect(status().isUnauthorized());
    }

    private Cookie loginAndGetAccessTokenCookie() throws Exception {
        MvcResult loginResult = login();
        Cookie accessTokenCookie = loginResult.getResponse().getCookie(
                authCookieProperties.getAccessTokenCookieName()
        );

        assertThat(accessTokenCookie).isNotNull();
        return accessTokenCookie;
    }

    private Cookie loginAndGetRefreshTokenCookie() throws Exception {
        MvcResult loginResult = login();
        Cookie refreshTokenCookie = loginResult.getResponse().getCookie(
                authCookieProperties.getRefreshTokenCookieName()
        );

        assertThat(refreshTokenCookie).isNotNull();
        return refreshTokenCookie;
    }

    private MvcResult login() throws Exception {
        Cookie csrfCookie = obtainCsrfCookie();

        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest())
                        .cookie(csrfCookie)
                        .header(
                                authCookieProperties.getCsrfHeaderName(),
                                csrfCookie.getValue()
                        ))
                .andExpect(status().isOk())
                .andReturn();
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

    private String loginRequest() {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(USERNAME, PASSWORD);
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
}
