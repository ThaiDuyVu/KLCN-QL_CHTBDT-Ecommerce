package com.example.backend.common.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(prefix = "app.auth-cookie")
public class AuthCookieProperties {

    @NotBlank
    private String accessTokenCookieName;

    @NotBlank
    private String refreshTokenCookieName;

    @NotBlank
    private String csrfCookieName;

    @NotBlank
    private String csrfHeaderName;

    private boolean secure;

    @NotBlank
    @Pattern(regexp = "(?i)lax|strict|none")
    private String sameSite;

    @NotBlank
    private String accessTokenPath;

    @NotBlank
    private String refreshTokenPath;

    private String domain;

    @NotNull
    private Duration accessTokenMaxAge;

    @NotNull
    private Duration refreshTokenMaxAge;

    @AssertTrue(message = "Cookie max ages must be positive")
    public boolean hasPositiveTokenMaxAges() {
        return accessTokenMaxAge != null
                && !accessTokenMaxAge.isNegative()
                && !accessTokenMaxAge.isZero()
                && refreshTokenMaxAge != null
                && !refreshTokenMaxAge.isNegative()
                && !refreshTokenMaxAge.isZero();
    }

    @AssertTrue(message = "Secure cookies are required when SameSite is None")
    public boolean isSecureWhenSameSiteIsNone() {
        return sameSite == null
                || !"none".equalsIgnoreCase(sameSite)
                || secure;
    }

    public String getAccessTokenCookieName() {
        return accessTokenCookieName;
    }

    public void setAccessTokenCookieName(String accessTokenCookieName) {
        this.accessTokenCookieName = accessTokenCookieName;
    }

    public String getRefreshTokenCookieName() {
        return refreshTokenCookieName;
    }

    public void setRefreshTokenCookieName(String refreshTokenCookieName) {
        this.refreshTokenCookieName = refreshTokenCookieName;
    }

    public String getCsrfCookieName() {
        return csrfCookieName;
    }

    public void setCsrfCookieName(String csrfCookieName) {
        this.csrfCookieName = csrfCookieName;
    }

    public String getCsrfHeaderName() {
        return csrfHeaderName;
    }

    public void setCsrfHeaderName(String csrfHeaderName) {
        this.csrfHeaderName = csrfHeaderName;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    public String getAccessTokenPath() {
        return accessTokenPath;
    }

    public void setAccessTokenPath(String accessTokenPath) {
        this.accessTokenPath = accessTokenPath;
    }

    public String getRefreshTokenPath() {
        return refreshTokenPath;
    }

    public void setRefreshTokenPath(String refreshTokenPath) {
        this.refreshTokenPath = refreshTokenPath;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Duration getAccessTokenMaxAge() {
        return accessTokenMaxAge;
    }

    public void setAccessTokenMaxAge(Duration accessTokenMaxAge) {
        this.accessTokenMaxAge = accessTokenMaxAge;
    }

    public Duration getRefreshTokenMaxAge() {
        return refreshTokenMaxAge;
    }

    public void setRefreshTokenMaxAge(Duration refreshTokenMaxAge) {
        this.refreshTokenMaxAge = refreshTokenMaxAge;
    }
}
