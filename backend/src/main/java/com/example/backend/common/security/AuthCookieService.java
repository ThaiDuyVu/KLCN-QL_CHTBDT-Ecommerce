package com.example.backend.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class AuthCookieService {

    private final AuthCookieProperties authCookieProperties;

    public AuthCookieService(AuthCookieProperties authCookieProperties) {
        this.authCookieProperties = authCookieProperties;
    }

    public void writeAccessTokenCookie(
            HttpServletResponse response,
            String accessToken
    ) {
        addCookieHeader(
                response,
                createTokenCookie(
                        authCookieProperties.getAccessTokenCookieName(),
                        accessToken,
                        authCookieProperties.getAccessTokenPath(),
                        authCookieProperties.getAccessTokenMaxAge()
                )
        );
    }

    public void writeRefreshTokenCookie(
            HttpServletResponse response,
            String refreshToken
    ) {
        addCookieHeader(
                response,
                createTokenCookie(
                        authCookieProperties.getRefreshTokenCookieName(),
                        refreshToken,
                        authCookieProperties.getRefreshTokenPath(),
                        authCookieProperties.getRefreshTokenMaxAge()
                )
        );
    }

    public String readRefreshTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (authCookieProperties.getRefreshTokenCookieName()
                    .equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    public void clearAuthenticationCookies(HttpServletResponse response) {
        addCookieHeader(
                response,
                createTokenCookie(
                        authCookieProperties.getAccessTokenCookieName(),
                        "",
                        authCookieProperties.getAccessTokenPath(),
                        Duration.ZERO
                )
        );

        addCookieHeader(
                response,
                createTokenCookie(
                        authCookieProperties.getRefreshTokenCookieName(),
                        "",
                        authCookieProperties.getRefreshTokenPath(),
                        Duration.ZERO
                )
        );

        addCookieHeader(
                response,
                createCsrfCookie(Duration.ZERO)
        );
    }

    private ResponseCookie createTokenCookie(
            String name,
            String value,
            String path,
            Duration maxAge
    ) {
        return createCookie(name, value, path, maxAge, true);
    }

    private ResponseCookie createCsrfCookie(Duration maxAge) {
        return createCookie(
                authCookieProperties.getCsrfCookieName(),
                "",
                authCookieProperties.getAccessTokenPath(),
                maxAge,
                false
        );
    }

    private ResponseCookie createCookie(
            String name,
            String value,
            String path,
            Duration maxAge,
            boolean httpOnly
    ) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie
                .from(name, value)
                .httpOnly(httpOnly)
                .secure(authCookieProperties.isSecure())
                .sameSite(authCookieProperties.getSameSite())
                .path(path)
                .maxAge(maxAge);

        if (StringUtils.hasText(authCookieProperties.getDomain())) {
            cookie.domain(authCookieProperties.getDomain());
        }

        return cookie.build();
    }

    private void addCookieHeader(
            HttpServletResponse response,
            ResponseCookie cookie
    ) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
