package com.example.backend.auth.service;

import com.example.backend.auth.entity.RefreshToken;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.exception.InvalidRefreshTokenException;
import com.example.backend.auth.exception.RefreshTokenReuseDetectedException;
import com.example.backend.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user, String rawRefreshToken) {
        return createRefreshToken(user, rawRefreshToken, OffsetDateTime.now());
    }

    @Override
    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public RefreshToken validateRefreshToken(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        return validateRefreshTokenRecord(refreshToken, OffsetDateTime.now());
    }

    @Override
    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public RefreshToken rotateRefreshToken(
            String currentRawRefreshToken,
            String newRawRefreshToken,
            User user
    ) {
        String currentTokenHash = hashToken(currentRawRefreshToken);

        RefreshToken currentRefreshToken = refreshTokenRepository
                .findByTokenHashForUpdate(currentTokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        OffsetDateTime now = OffsetDateTime.now();
        validateRefreshTokenRecord(currentRefreshToken, now);
        validateTokenOwner(currentRefreshToken, user);

        String newTokenHash = hashToken(newRawRefreshToken);

        if (currentTokenHash.equals(newTokenHash)) {
            throw new InvalidRefreshTokenException();
        }

        currentRefreshToken.setRevokedAt(now);

        RefreshToken successor = createRefreshToken(user, newRawRefreshToken, now);
        currentRefreshToken.setReplacedByToken(successor);

        refreshTokenRepository.save(currentRefreshToken);

        return successor;
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(rawRefreshToken);

        refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .ifPresent(refreshToken -> {
                    if (refreshToken.getRevokedAt() == null) {
                        refreshToken.setRevokedAt(OffsetDateTime.now());
                        refreshTokenRepository.save(refreshToken);
                    }
                });
    }

    private RefreshToken createRefreshToken(
            User user,
            String rawRefreshToken,
            OffsetDateTime now
    ) {
        validateUser(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(
                now.plus(jwtService.getRefreshTokenExpiration())
        );

        return refreshTokenRepository.save(refreshToken);
    }

    private RefreshToken validateRefreshTokenRecord(
            RefreshToken refreshToken,
            OffsetDateTime now
    ) {
        if (refreshToken.getRevokedAt() != null) {
            if (refreshToken.getReplacedByToken() != null) {
                revokeActiveTokensForUser(refreshToken.getUser(), now);
                throw new RefreshTokenReuseDetectedException();
            }

            throw new InvalidRefreshTokenException();
        }

        if (refreshToken.getExpiresAt() == null
                || !refreshToken.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }

        return refreshToken;
    }

    private void revokeActiveTokensForUser(User user, OffsetDateTime now) {
        List<RefreshToken> activeTokens = refreshTokenRepository
                .findByUser_UserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        user.getUserId(),
                        now
                );

        activeTokens.forEach(token -> token.setRevokedAt(now));
        refreshTokenRepository.saveAll(activeTokens);
    }

    private void validateTokenOwner(RefreshToken refreshToken, User user) {
        validateUser(user);

        if (!refreshToken.getUser().getUserId().equals(user.getUserId())) {
            throw new InvalidRefreshTokenException();
        }
    }

    private void validateUser(User user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User must have an ID");
        }
    }

    private String hashToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    rawRefreshToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
