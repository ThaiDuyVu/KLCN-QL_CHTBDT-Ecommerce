package com.example.backend.auth.service;

import io.jsonwebtoken.Claims;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface JwtService {

    String generateAccessToken(UUID userId, String roleName);

    String generateRefreshToken(UUID userId);

    Claims parseAndValidate(String token, String expectedTokenType);

    UUID extractUserId(String token, String expectedTokenType);

    String extractTokenType(String token);

    Optional<String> extractRole(String token);

    Duration getAccessTokenExpiration();

    Duration getRefreshTokenExpiration();
}
