package com.example.backend.auth.integration;

import com.example.backend.common.security.AuthCookieProperties;
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

import jakarta.servlet.http.Cookie;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthLoginIntegrationTest {

    private static final UUID ACTIVE_USER_ID = UUID.fromString(
            "c44c2f11-543f-4f3e-9787-7f35d08b0ec9"
    );
    private static final UUID USER_WITHOUT_ROLE_ID = UUID.fromString(
            "348ef0f0-4ce9-4d63-ac68-074829ecd721"
    );
    private static final UUID ROLE_ID = UUID.fromString(
            "4e9d384a-8c5d-4db9-9a7e-a8025423b42f"
    );

    private static final String ACTIVE_USERNAME = "nguyenvana";
    private static final String USER_WITHOUT_ROLE_USERNAME = "nguyenvanb";
    private static final String PASSWORD = "Password123!";
    private static final String DISPLAY_NAME = "Nguyễn Văn A";
    private static final String ROLE_NAME = "ROLE_TEST";

    @Container
    private static final PostgreSQLContainer<?> postgresql =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("auth_login_test")
                    .withUsername("auth_login_test")
                    .withPassword("auth_login_test");

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
                () -> "test-jwt-secret-for-auth-login-integration-tests"
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
                "Vai trò chỉ dùng cho kiểm thử đăng nhập"
        );

        insertUser(
                ACTIVE_USER_ID,
                ACTIVE_USERNAME,
                "nguyenvana@example.test",
                DISPLAY_NAME
        );
        insertUser(
                USER_WITHOUT_ROLE_ID,
                USER_WITHOUT_ROLE_USERNAME,
                "nguyenvanb@example.test",
                "Nguyễn Văn B"
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
    void getCsrf_shouldIssueReadableCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie(
                authCookieProperties.getCsrfCookieName()
        );

        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isFalse();
        assertThat(hasSetCookieAttribute(
                result,
                authCookieProperties.getCsrfCookieName(),
                "HttpOnly"
        )).isFalse();
    }

    @Test
    void loginWithoutCsrf_shouldReturnForbiddenWithoutAuthenticationCookies()
            throws Exception {
        MvcResult result = performLogin(ACTIVE_USERNAME, PASSWORD, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(hasSetCookie(
                result,
                authCookieProperties.getAccessTokenCookieName()
        )).isFalse();
        assertThat(hasSetCookie(
                result,
                authCookieProperties.getRefreshTokenCookieName()
        )).isFalse();
    }

    @Test
    void loginWithCsrfAndValidCredentials_shouldReturnSessionAndAuthenticationCookies()
            throws Exception {
        Cookie csrfCookie = obtainCsrfCookie();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(ACTIVE_USERNAME, PASSWORD))
                        .cookie(csrfCookie)
                        .header(
                                authCookieProperties.getCsrfHeaderName(),
                                csrfCookie.getValue()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId")
                        .value(ACTIVE_USER_ID.toString()))
                .andExpect(jsonPath("$.username").value(ACTIVE_USERNAME))
                .andExpect(jsonPath("$.displayName").value(DISPLAY_NAME))
                .andExpect(jsonPath("$.roleName").value(ROLE_NAME))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenType").doesNotExist())
                .andReturn();

        assertAuthenticationCookie(
                result,
                authCookieProperties.getAccessTokenCookieName(),
                authCookieProperties.getAccessTokenPath()
        );
        assertAuthenticationCookie(
                result,
                authCookieProperties.getRefreshTokenCookieName(),
                authCookieProperties.getRefreshTokenPath()
        );
    }

    @Test
    void loginWithCsrfAndInvalidPassword_shouldReturnUnauthorizedWithoutCookies()
            throws Exception {
        MvcResult result = performLogin(
                ACTIVE_USERNAME,
                "IncorrectPassword123!",
                obtainCsrfCookie()
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString())
                .isEqualTo("Invalid username or password");
        assertThat(hasAuthenticationCookies(result)).isFalse();
    }

    @Test
    void loginWithCsrfAndUnknownUsername_shouldReturnUnauthorizedWithoutCookies()
            throws Exception {
        MvcResult result = performLogin(
                "nguoikhongtontai",
                PASSWORD,
                obtainCsrfCookie()
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString())
                .isEqualTo("Invalid username or password");
        assertThat(hasAuthenticationCookies(result)).isFalse();
    }

    @Test
    void loginWithCsrfAndUserWithoutRole_shouldReturnUnauthorizedWithoutCookies()
            throws Exception {
        MvcResult result = performLogin(
                USER_WITHOUT_ROLE_USERNAME,
                PASSWORD,
                obtainCsrfCookie()
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString())
                .isEqualTo("Invalid username or password");
        assertThat(hasAuthenticationCookies(result)).isFalse();
    }

    private void insertUser(
            UUID userId,
            String username,
            String email,
            String displayName
    ) {
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
                userId,
                username,
                passwordEncoder.encode(PASSWORD),
                email,
                displayName,
                "0901234567",
                "ACTIVE"
        );
    }

    private void clearTestData() {
        jdbcTemplate.update(
                "DELETE FROM refresh_tokens WHERE user_id IN (?, ?)",
                ACTIVE_USER_ID,
                USER_WITHOUT_ROLE_ID
        );
        jdbcTemplate.update(
                "DELETE FROM user_roles WHERE user_id IN (?, ?)",
                ACTIVE_USER_ID,
                USER_WITHOUT_ROLE_ID
        );
        jdbcTemplate.update(
                "DELETE FROM users WHERE user_id IN (?, ?)",
                ACTIVE_USER_ID,
                USER_WITHOUT_ROLE_ID
        );
        jdbcTemplate.update(
                "DELETE FROM roles WHERE role_id = ?",
                ROLE_ID
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

    private MvcResult performLogin(
            String username,
            String password,
            Cookie csrfCookie
    ) throws Exception {
        var request = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest(username, password));

        if (csrfCookie != null) {
            request.cookie(csrfCookie)
                    .header(
                            authCookieProperties.getCsrfHeaderName(),
                            csrfCookie.getValue()
                    );
        }

        return mockMvc.perform(request).andReturn();
    }

    private String loginRequest(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }

    private void assertAuthenticationCookie(
            MvcResult result,
            String cookieName,
            String expectedPath
    ) {
        Cookie cookie = result.getResponse().getCookie(cookieName);

        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure())
                .isEqualTo(authCookieProperties.isSecure());
        assertThat(cookie.getPath()).isEqualTo(expectedPath);
        assertThat(hasSetCookieAttribute(
                result,
                cookieName,
                "SameSite=" + authCookieProperties.getSameSite()
        )).isTrue();
    }

    private boolean hasAuthenticationCookies(MvcResult result) {
        return hasSetCookie(
                result,
                authCookieProperties.getAccessTokenCookieName()
        ) || hasSetCookie(
                result,
                authCookieProperties.getRefreshTokenCookieName()
        );
    }

    private boolean hasSetCookie(MvcResult result, String cookieName) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)
                .stream()
                .anyMatch(header -> header.startsWith(cookieName + "="));
    }

    private boolean hasSetCookieAttribute(
            MvcResult result,
            String cookieName,
            String attribute
    ) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)
                .stream()
                .filter(header -> header.startsWith(cookieName + "="))
                .anyMatch(header -> header.contains(attribute));
    }
}
