package com.example.backend.auth.service;

import com.example.backend.auth.exception.InvalidJwtTokenException;
import com.example.backend.common.security.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceImplTest {

    private static final String TEST_SECRET =
            "test-secret-for-jwt-unit-tests-with-at-least-32-bytes";
    private static final String ISSUER = "ql-chtbdt-ecommerce";
    private static final String AUDIENCE = "ql-chtbdt-frontend";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private JwtServiceImpl jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(
                createJwtProperties(ISSUER, AUDIENCE)
        );
    }

    @Test
    void generateAccessToken_shouldCreateValidAccessTokenWithRole() {
        UUID userId = UUID.randomUUID();
        String roleName = "ADMIN";

        String token = jwtService.generateAccessToken(userId, roleName);
        Claims claims = jwtService.parseAndValidate(
                token,
                ACCESS_TOKEN_TYPE
        );

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(ISSUER, claims.getIssuer());
        assertEquals(roleName, claims.get("role", String.class));
        assertEquals(
                ACCESS_TOKEN_TYPE,
                jwtService.extractTokenType(token)
        );
        assertEquals(userId, jwtService.extractUserId(
                token,
                ACCESS_TOKEN_TYPE
        ));
        assertEquals(roleName, jwtService.extractRole(token).orElseThrow());
        assertNotNull(claims.getId());
    }

    @Test
    void generateRefreshToken_shouldCreateValidRefreshTokenWithoutRole() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateRefreshToken(userId);
        Claims claims = jwtService.parseAndValidate(
                token,
                REFRESH_TOKEN_TYPE
        );

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(ISSUER, claims.getIssuer());
        assertEquals(REFRESH_TOKEN_TYPE, jwtService.extractTokenType(token));
        assertEquals(userId, jwtService.extractUserId(
                token,
                REFRESH_TOKEN_TYPE
        ));
        assertFalse(claims.containsKey("role"));
        assertNotNull(claims.getId());
    }

    @Test
    void parseAndValidate_shouldRejectTokensWithDifferentIssuerOrAudience() {
        String token = jwtService.generateAccessToken(
                UUID.randomUUID(),
                "ADMIN"
        );

        JwtServiceImpl differentIssuerService = new JwtServiceImpl(
                createJwtProperties("different-issuer", AUDIENCE)
        );
        JwtServiceImpl differentAudienceService = new JwtServiceImpl(
                createJwtProperties(ISSUER, "different-audience")
        );

        assertThrows(
                InvalidJwtTokenException.class,
                () -> differentIssuerService.parseAndValidate(
                        token,
                        ACCESS_TOKEN_TYPE
                )
        );
        assertThrows(
                InvalidJwtTokenException.class,
                () -> differentAudienceService.parseAndValidate(
                        token,
                        ACCESS_TOKEN_TYPE
                )
        );
    }

    @Test
    void parseAndValidate_shouldRejectMismatchedTokenTypes() {
        UUID userId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(userId, "ADMIN");
        String refreshToken = jwtService.generateRefreshToken(userId);

        assertThrows(
                InvalidJwtTokenException.class,
                () -> jwtService.parseAndValidate(
                        accessToken,
                        REFRESH_TOKEN_TYPE
                )
        );
        assertThrows(
                InvalidJwtTokenException.class,
                () -> jwtService.parseAndValidate(
                        refreshToken,
                        ACCESS_TOKEN_TYPE
                )
        );
    }

    @Test
    void generateRefreshToken_shouldCreateDistinctTokensWhenGeneratedRapidly() {
        UUID userId = UUID.randomUUID();
        Set<String> tokens = new HashSet<>();

        for (int index = 0; index < 10; index++) {
            tokens.add(jwtService.generateRefreshToken(userId));
        }

        assertEquals(10, tokens.size());
    }

    @Test
    void parseAndValidate_shouldRejectNullBlankAndMalformedTokens() {
        assertThrows(
                InvalidJwtTokenException.class,
                () -> jwtService.parseAndValidate(null, ACCESS_TOKEN_TYPE)
        );
        assertThrows(
                InvalidJwtTokenException.class,
                () -> jwtService.parseAndValidate(" ", ACCESS_TOKEN_TYPE)
        );
        assertThrows(
                InvalidJwtTokenException.class,
                () -> jwtService.parseAndValidate(
                        "not-a-jwt",
                        ACCESS_TOKEN_TYPE
                )
        );
    }

    private JwtProperties createJwtProperties(
            String issuer,
            String audience
    ) {
        JwtProperties jwtProperties = new JwtProperties();

        jwtProperties.setSecret(TEST_SECRET);
        jwtProperties.setIssuer(issuer);
        jwtProperties.setAudience(audience);
        jwtProperties.setAccessTokenExpiration(Duration.ofMinutes(15));
        jwtProperties.setRefreshTokenExpiration(Duration.ofDays(7));

        return jwtProperties;
    }
}
