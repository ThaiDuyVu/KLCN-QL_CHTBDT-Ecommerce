package com.example.backend.auth.service;

import com.example.backend.auth.exception.InvalidJwtTokenException;
import com.example.backend.common.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtServiceImpl implements JwtService {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ROLE_CLAIM = "role";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtServiceImpl(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = createSigningKey(jwtProperties.getSecret());
        validateExpiration(jwtProperties.getAccessTokenExpiration());
        validateExpiration(jwtProperties.getRefreshTokenExpiration());
    }

    @Override
    public String generateAccessToken(UUID userId, String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("Role name must not be blank");
        }

        return buildToken(
                userId,
                ACCESS_TOKEN_TYPE,
                jwtProperties.getAccessTokenExpiration(),
                roleName
        );
    }

    @Override
    public String generateRefreshToken(UUID userId) {
        return buildToken(
                userId,
                REFRESH_TOKEN_TYPE,
                jwtProperties.getRefreshTokenExpiration(),
                null
        );
    }

    @Override
    public Claims parseAndValidate(String token, String expectedTokenType) {
        validateExpectedTokenType(expectedTokenType);

        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(jwtProperties.getIssuer())
                    .requireAudience(jwtProperties.getAudience())
                    .build()
                    .parseSignedClaims(token);

            if (!Jwts.SIG.HS256.getId().equals(jws.getHeader().getAlgorithm())) {
                throw new InvalidJwtTokenException();
            }

            Claims claims = jws.getPayload();
            validateClaims(claims, expectedTokenType);

            return claims;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidJwtTokenException();
        }
    }

    @Override
    public UUID extractUserId(String token, String expectedTokenType) {
        Claims claims = parseAndValidate(token, expectedTokenType);

        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new InvalidJwtTokenException();
        }
    }

    @Override
    public String extractTokenType(String token) {
        Claims claims = parseAndValidateWithoutExpectedTokenType(token);

        return getValidatedTokenType(claims);
    }

    @Override
    public Optional<String> extractRole(String token) {
        Claims claims = parseAndValidate(token, ACCESS_TOKEN_TYPE);

        return Optional.ofNullable(claims.get(ROLE_CLAIM, String.class));
    }

    @Override
    public Duration getAccessTokenExpiration() {
        return jwtProperties.getAccessTokenExpiration();
    }

    @Override
    public Duration getRefreshTokenExpiration() {
        return jwtProperties.getRefreshTokenExpiration();
    }

    private String buildToken(
            UUID userId,
            String tokenType,
            Duration expiration,
            String roleName
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(expiration);

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(jwtProperties.getIssuer())
                .audience()
                    .add(jwtProperties.getAudience())
                    .and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(TOKEN_TYPE_CLAIM, tokenType);

        if (roleName != null) {
            builder.claim(ROLE_CLAIM, roleName);
        }

        return builder
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private Claims parseAndValidateWithoutExpectedTokenType(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(jwtProperties.getIssuer())
                    .requireAudience(jwtProperties.getAudience())
                    .build()
                    .parseSignedClaims(token);

            if (!Jwts.SIG.HS256.getId().equals(jws.getHeader().getAlgorithm())) {
                throw new InvalidJwtTokenException();
            }

            Claims claims = jws.getPayload();
            validateRequiredClaims(claims);

            return claims;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidJwtTokenException();
        }
    }

    private void validateClaims(Claims claims, String expectedTokenType) {
        validateRequiredClaims(claims);

        if (!expectedTokenType.equals(getValidatedTokenType(claims))) {
            throw new InvalidJwtTokenException();
        }
    }

    private void validateRequiredClaims(Claims claims) {
        if (claims.getSubject() == null || claims.getExpiration() == null) {
            throw new InvalidJwtTokenException();
        }
    }

    private String getValidatedTokenType(Claims claims) {
        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);

        if (!ACCESS_TOKEN_TYPE.equals(tokenType)
                && !REFRESH_TOKEN_TYPE.equals(tokenType)) {
            throw new InvalidJwtTokenException();
        }

        return tokenType;
    }

    private void validateExpectedTokenType(String expectedTokenType) {
        if (!ACCESS_TOKEN_TYPE.equals(expectedTokenType)
                && !REFRESH_TOKEN_TYPE.equals(expectedTokenType)) {
            throw new IllegalArgumentException("Unsupported token type");
        }
    }

    private SecretKey createSigningKey(String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 32 bytes for HS256"
            );
        }

        return Keys.hmacShaKeyFor(secretBytes);
    }

    private void validateExpiration(Duration expiration) {
        if (expiration.isNegative() || expiration.isZero()) {
            throw new IllegalArgumentException(
                    "JWT expiration must be greater than zero"
            );
        }
    }
}
