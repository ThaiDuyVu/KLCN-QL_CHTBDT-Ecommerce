package com.example.backend.auth.service;

import com.example.backend.auth.entity.RefreshToken;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.exception.InvalidRefreshTokenException;
import com.example.backend.auth.exception.RefreshTokenReuseDetectedException;
import com.example.backend.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    private RefreshTokenServiceImpl refreshTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(
                refreshTokenRepository,
                jwtService
        );

        user = new User();
        user.setUserId(UUID.randomUUID());
    }

    @Test
    void createRefreshToken_shouldStoreOnlySha256HashAndSetExpiry() {
        String rawRefreshToken = "test-raw-refresh-token";
        Duration refreshTokenExpiration = Duration.ofDays(7);
        OffsetDateTime beforeCreation = OffsetDateTime.now();

        when(jwtService.getRefreshTokenExpiration())
                .thenReturn(refreshTokenExpiration);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken result = refreshTokenService.createRefreshToken(
                user,
                rawRefreshToken
        );

        ArgumentCaptor<RefreshToken> refreshTokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());

        OffsetDateTime afterCreation = OffsetDateTime.now();
        RefreshToken savedToken = refreshTokenCaptor.getValue();

        assertSame(savedToken, result);
        assertSame(user, savedToken.getUser());
        assertEquals(
                sha256Hex(rawRefreshToken),
                savedToken.getTokenHash()
        );
        assertNotEquals(rawRefreshToken, savedToken.getTokenHash());
        assertEquals(64, savedToken.getTokenHash().length());
        assertFalse(
                savedToken.getExpiresAt().isBefore(
                        beforeCreation.plus(refreshTokenExpiration)
                )
        );
        assertFalse(
                savedToken.getExpiresAt().isAfter(
                        afterCreation.plus(refreshTokenExpiration)
                )
        );
        verify(jwtService).getRefreshTokenExpiration();
    }

    @Test
    void validateRefreshToken_shouldReturnMatchingTokenWhenActiveAndUnexpired() {
        String rawRefreshToken = "valid-refresh-token";
        RefreshToken refreshToken = createRefreshToken(
                rawRefreshToken,
                OffsetDateTime.now().plusDays(1)
        );

        when(refreshTokenRepository.findByTokenHash(
                sha256Hex(rawRefreshToken)
        )).thenReturn(Optional.of(refreshToken));

        RefreshToken result = refreshTokenService.validateRefreshToken(
                rawRefreshToken
        );

        assertSame(refreshToken, result);
        verify(refreshTokenRepository).findByTokenHash(
                sha256Hex(rawRefreshToken)
        );
    }

    @Test
    void validateRefreshToken_shouldRejectNullOrBlankRawToken() {
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.validateRefreshToken(null)
        );
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.validateRefreshToken(" ")
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void validateRefreshToken_shouldRejectUnknownToken() {
        String rawRefreshToken = "unknown-refresh-token";

        when(refreshTokenRepository.findByTokenHash(
                sha256Hex(rawRefreshToken)
        )).thenReturn(Optional.empty());

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.validateRefreshToken(rawRefreshToken)
        );
    }

    @Test
    void validateRefreshToken_shouldRejectExpiredToken() {
        String rawRefreshToken = "expired-refresh-token";
        RefreshToken refreshToken = createRefreshToken(
                rawRefreshToken,
                OffsetDateTime.now().minusSeconds(1)
        );

        when(refreshTokenRepository.findByTokenHash(
                sha256Hex(rawRefreshToken)
        )).thenReturn(Optional.of(refreshToken));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.validateRefreshToken(rawRefreshToken)
        );
    }

    @Test
    void validateRefreshToken_shouldRejectRevokedTokenWithoutReplacement() {
        String rawRefreshToken = "revoked-refresh-token";
        RefreshToken refreshToken = createRefreshToken(
                rawRefreshToken,
                OffsetDateTime.now().plusDays(1)
        );
        refreshToken.setRevokedAt(OffsetDateTime.now().minusMinutes(1));

        when(refreshTokenRepository.findByTokenHash(
                sha256Hex(rawRefreshToken)
        )).thenReturn(Optional.of(refreshToken));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.validateRefreshToken(rawRefreshToken)
        );
    }

    @Test
    void rotateRefreshToken_shouldRevokeCurrentTokenAndSaveSuccessor() {
        String currentRawRefreshToken = "current-refresh-token";
        String newRawRefreshToken = "new-refresh-token";
        Duration refreshTokenExpiration = Duration.ofDays(7);
        RefreshToken currentToken = createRefreshToken(
                currentRawRefreshToken,
                OffsetDateTime.now().plusDays(1)
        );

        when(refreshTokenRepository.findByTokenHashForUpdate(
                sha256Hex(currentRawRefreshToken)
        )).thenReturn(Optional.of(currentToken));
        when(jwtService.getRefreshTokenExpiration())
                .thenReturn(refreshTokenExpiration);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken successor = refreshTokenService.rotateRefreshToken(
                currentRawRefreshToken,
                newRawRefreshToken,
                user
        );

        ArgumentCaptor<RefreshToken> refreshTokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2))
                .save(refreshTokenCaptor.capture());

        List<RefreshToken> savedTokens = refreshTokenCaptor.getAllValues();
        RefreshToken savedSuccessor = savedTokens.getFirst();
        RefreshToken savedCurrentToken = savedTokens.get(1);

        assertSame(savedSuccessor, successor);
        assertSame(user, savedSuccessor.getUser());
        assertEquals(
                sha256Hex(newRawRefreshToken),
                savedSuccessor.getTokenHash()
        );
        assertNotEquals(newRawRefreshToken, savedSuccessor.getTokenHash());
        assertTrue(savedSuccessor.getExpiresAt().isAfter(OffsetDateTime.now()));
        assertNull(savedSuccessor.getRevokedAt());
        assertSame(currentToken, savedCurrentToken);
        assertNotNull(currentToken.getRevokedAt());
        assertSame(successor, currentToken.getReplacedByToken());
        verify(refreshTokenRepository).findByTokenHashForUpdate(
                sha256Hex(currentRawRefreshToken)
        );
    }

    @Test
    void rotateRefreshToken_shouldRejectNewTokenMatchingCurrentToken() {
        String rawRefreshToken = "same-refresh-token";
        RefreshToken currentToken = createRefreshToken(
                rawRefreshToken,
                OffsetDateTime.now().plusDays(1)
        );

        when(refreshTokenRepository.findByTokenHashForUpdate(
                sha256Hex(rawRefreshToken)
        )).thenReturn(Optional.of(currentToken));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotateRefreshToken(
                        rawRefreshToken,
                        rawRefreshToken,
                        user
                )
        );

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void validateRefreshToken_shouldRevokeActiveTokensWhenReuseIsDetected() {
        String rawRefreshToken = "reused-refresh-token";
        RefreshToken reusedToken = createRefreshToken(
                rawRefreshToken,
                OffsetDateTime.now().plusDays(1)
        );
        reusedToken.setRevokedAt(OffsetDateTime.now().minusMinutes(1));
        reusedToken.setReplacedByToken(new RefreshToken());

        RefreshToken activeTokenOne = createRefreshToken(
                "active-refresh-token-one",
                OffsetDateTime.now().plusDays(1)
        );
        RefreshToken activeTokenTwo = createRefreshToken(
                "active-refresh-token-two",
                OffsetDateTime.now().plusDays(1)
        );

        when(refreshTokenRepository.findByTokenHash(
                sha256Hex(rawRefreshToken)
        )).thenReturn(Optional.of(reusedToken));
        when(refreshTokenRepository
                .findByUser_UserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        eq(user.getUserId()),
                        any(OffsetDateTime.class)
                ))
                .thenReturn(List.of(activeTokenOne, activeTokenTwo));

        assertThrows(
                RefreshTokenReuseDetectedException.class,
                () -> refreshTokenService.validateRefreshToken(rawRefreshToken)
        );

        assertNotNull(activeTokenOne.getRevokedAt());
        assertNotNull(activeTokenTwo.getRevokedAt());
        verify(refreshTokenRepository).saveAll(
                List.of(activeTokenOne, activeTokenTwo)
        );
    }

    @Test
    void revokeRefreshToken_shouldNotQueryRepositoryForNullOrBlankToken() {
        refreshTokenService.revokeRefreshToken(null);
        refreshTokenService.revokeRefreshToken(" ");

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void revokeRefreshToken_shouldRevokeAndSaveExistingActiveToken() {
        String rawRefreshToken = "active-refresh-token";
        RefreshToken refreshToken = createRefreshToken(
                rawRefreshToken,
                OffsetDateTime.now().plusDays(1)
        );

        when(refreshTokenRepository.findByTokenHashForUpdate(
                sha256Hex(rawRefreshToken)
        )).thenReturn(Optional.of(refreshToken));

        refreshTokenService.revokeRefreshToken(rawRefreshToken);

        assertNotNull(refreshToken.getRevokedAt());
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void revokeRefreshToken_shouldIgnoreMissingToken() {
        String rawRefreshToken = "missing-refresh-token";

        when(refreshTokenRepository.findByTokenHashForUpdate(
                sha256Hex(rawRefreshToken)
        )).thenReturn(Optional.empty());

        assertDoesNotThrow(
                () -> refreshTokenService.revokeRefreshToken(rawRefreshToken)
        );

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    private RefreshToken createRefreshToken(
            String rawRefreshToken,
            OffsetDateTime expiresAt
    ) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(sha256Hex(rawRefreshToken));
        refreshToken.setExpiresAt(expiresAt);

        return refreshToken;
    }

    private String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
