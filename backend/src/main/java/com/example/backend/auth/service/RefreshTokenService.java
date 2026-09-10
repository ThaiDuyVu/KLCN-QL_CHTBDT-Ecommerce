package com.example.backend.auth.service;

import com.example.backend.auth.entity.RefreshToken;
import com.example.backend.auth.entity.User;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(User user, String rawRefreshToken);

    RefreshToken validateRefreshToken(String rawRefreshToken);

    RefreshToken rotateRefreshToken(
            String currentRawRefreshToken,
            String newRawRefreshToken,
            User user
    );

    void revokeRefreshToken(String rawRefreshToken);
}
