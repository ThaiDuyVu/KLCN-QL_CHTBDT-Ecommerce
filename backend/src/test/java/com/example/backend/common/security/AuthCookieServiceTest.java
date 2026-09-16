package com.example.backend.common.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCookieServiceTest {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "ACCESS_TOKEN";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String SAME_SITE = "Strict";

    private AuthCookieProperties authCookieProperties;
    private AuthCookieService authCookieService;

    @BeforeEach
    void setUp() {
        authCookieProperties = new AuthCookieProperties();
        authCookieProperties.setAccessTokenCookieName(
                ACCESS_TOKEN_COOKIE_NAME
        );
        authCookieProperties.setRefreshTokenCookieName(
                REFRESH_TOKEN_COOKIE_NAME
        );
        authCookieProperties.setCsrfCookieName(CSRF_COOKIE_NAME);
        authCookieProperties.setCsrfHeaderName("X-XSRF-TOKEN");
        authCookieProperties.setSecure(true);
        authCookieProperties.setSameSite(SAME_SITE);
        authCookieProperties.setAccessTokenPath("/");
        authCookieProperties.setRefreshTokenPath("/api/auth");
        authCookieProperties.setAccessTokenMaxAge(Duration.ofMinutes(15));
        authCookieProperties.setRefreshTokenMaxAge(Duration.ofDays(7));

        authCookieService = new AuthCookieService(authCookieProperties);
    }

    @Test
    void writeAccessTokenCookie_shouldAddConfiguredHttpOnlyCookie() {
        String accessToken = "access-token-value";
        MockHttpServletResponse response = new MockHttpServletResponse();

        authCookieService.writeAccessTokenCookie(response, accessToken);

        List<String> cookieHeaders = response.getHeaders(
                HttpHeaders.SET_COOKIE
        );
        assertEquals(1, cookieHeaders.size());

        assertTokenCookieHeader(
                cookieHeaders.getFirst(),
                ACCESS_TOKEN_COOKIE_NAME,
                accessToken,
                authCookieProperties.getAccessTokenPath(),
                authCookieProperties.getAccessTokenMaxAge()
        );
    }

    @Test
    void writeRefreshTokenCookie_shouldAddConfiguredHttpOnlyCookie() {
        String refreshToken = "refresh-token-value";
        MockHttpServletResponse response = new MockHttpServletResponse();

        authCookieService.writeRefreshTokenCookie(response, refreshToken);

        List<String> cookieHeaders = response.getHeaders(
                HttpHeaders.SET_COOKIE
        );
        assertEquals(1, cookieHeaders.size());

        assertTokenCookieHeader(
                cookieHeaders.getFirst(),
                REFRESH_TOKEN_COOKIE_NAME,
                refreshToken,
                authCookieProperties.getRefreshTokenPath(),
                authCookieProperties.getRefreshTokenMaxAge()
        );
    }

    @Test
    void clearAuthenticationCookies_shouldAddConfiguredClearingCookies() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authCookieService.clearAuthenticationCookies(response);

        List<String> cookieHeaders = response.getHeaders(
                HttpHeaders.SET_COOKIE
        );
        assertEquals(3, cookieHeaders.size());

        String accessCookieHeader = findCookieHeader(
                cookieHeaders,
                ACCESS_TOKEN_COOKIE_NAME
        );
        String refreshCookieHeader = findCookieHeader(
                cookieHeaders,
                REFRESH_TOKEN_COOKIE_NAME
        );
        String csrfCookieHeader = findCookieHeader(
                cookieHeaders,
                CSRF_COOKIE_NAME
        );

        assertClearingCookieHeader(accessCookieHeader, true);
        assertClearingCookieHeader(refreshCookieHeader, true);
        assertClearingCookieHeader(csrfCookieHeader, false);
    }

    @Test
    void readRefreshTokenCookie_shouldReturnValueWhenConfiguredCookieIsPresent() {
        String refreshToken = "refresh-token-value";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("OTHER_COOKIE", "other-value"),
                new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
        );

        String result = authCookieService.readRefreshTokenCookie(request);

        assertEquals(refreshToken, result);
    }

    @Test
    void readRefreshTokenCookie_shouldReturnNullWhenRequestHasNoCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertNull(authCookieService.readRefreshTokenCookie(request));
    }

    @Test
    void readRefreshTokenCookie_shouldReturnNullWhenRefreshCookieIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OTHER_COOKIE", "other-value"));

        assertNull(authCookieService.readRefreshTokenCookie(request));
    }

    private void assertTokenCookieHeader(
            String cookieHeader,
            String cookieName,
            String tokenValue,
            String path,
            Duration maxAge
    ) {
        assertTrue(cookieHeader.startsWith(cookieName + "=" + tokenValue));
        assertTrue(cookieHeader.contains("HttpOnly"));
        assertTrue(cookieHeader.contains("Secure"));
        assertTrue(cookieHeader.contains("SameSite=" + SAME_SITE));
        assertTrue(cookieHeader.contains("Path=" + path));
        assertTrue(
                cookieHeader.contains("Max-Age=" + maxAge.toSeconds())
        );
    }

    private String findCookieHeader(
            List<String> cookieHeaders,
            String cookieName
    ) {
        return cookieHeaders.stream()
                .filter(header -> header.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow();
    }

    private void assertClearingCookieHeader(
            String cookieHeader,
            boolean httpOnly
    ) {
        assertTrue(cookieHeader.contains("Max-Age=0"));
        assertEquals(httpOnly, cookieHeader.contains("HttpOnly"));
    }
}
