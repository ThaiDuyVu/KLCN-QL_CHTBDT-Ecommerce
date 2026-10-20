package com.example.backend.auth.service;

import com.example.backend.auth.dto.LoginRequest;
import com.example.backend.cart.repository.CustomerCartRepository;
import com.example.backend.auth.dto.LoginSessionResponse;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.exception.InvalidCredentialsException;
import com.example.backend.auth.exception.InvalidRefreshTokenException;
import com.example.backend.auth.exception.RefreshTokenReuseDetectedException;
import com.example.backend.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final CustomerCartRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CustomUserDetailsService customUserDetailsService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            CustomerCartRepository customers
    ) {
        this.userRepository = userRepository;
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.customUserDetailsService = customUserDetailsService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public User authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return user;
    }

    @Override
    @Transactional
    public LoginAuthenticationResult login(LoginRequest request) {
        User user = authenticate(
                request.getUsername(),
                request.getPassword()
        );

        AuthenticatedUserPrincipal principal = loadPrincipal(user);

        requireActiveUser(principal);

        String accessToken = jwtService.generateAccessToken(
                principal.getUserId(),
                principal.getRoleName()
        );
        String refreshToken = jwtService.generateRefreshToken(
                principal.getUserId()
        );

        refreshTokenService.createRefreshToken(user, refreshToken);

        LoginSessionResponse response = getCurrentSession(principal);

        return new LoginAuthenticationResult(
                response,
                accessToken,
                refreshToken
        );
    }

    @Override
    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public LoginAuthenticationResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        var userId = jwtService.extractUserId(rawRefreshToken, "refresh");
        AuthenticatedUserPrincipal principal = loadPrincipal(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        requireActiveUser(principal);

        String refreshToken = jwtService.generateRefreshToken(
                principal.getUserId()
        );

        refreshTokenService.rotateRefreshToken(
                rawRefreshToken,
                refreshToken,
                user
        );

        String accessToken = jwtService.generateAccessToken(
                principal.getUserId(),
                principal.getRoleName()
        );

        LoginSessionResponse response = getCurrentSession(principal);

        return new LoginAuthenticationResult(
                response,
                accessToken,
                refreshToken
        );
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeRefreshToken(rawRefreshToken);
    }

    @Override
    public LoginSessionResponse getCurrentSession(
            AuthenticatedUserPrincipal principal
    ) {
        var response = new LoginSessionResponse(
                principal.getUserId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getRoleName()
        );
        if ("CUSTOMER".equals(principal.getRoleName())) {
            customers.findByUser_UserId(principal.getUserId())
                    .ifPresent(customer -> response.setCustomerId(customer.getCustomerId()));
        }
        return response;
    }

    private void requireActiveUser(AuthenticatedUserPrincipal principal) {
        if (!"ACTIVE".equals(principal.getStatus())) {
            throw new InvalidCredentialsException();
        }
    }

    private AuthenticatedUserPrincipal loadPrincipal(User user) {
        return loadPrincipal(user.getUserId());
    }

    private AuthenticatedUserPrincipal loadPrincipal(java.util.UUID userId) {
        try {
            return customUserDetailsService.loadUserByUserId(
                    userId
            );
        } catch (UsernameNotFoundException exception) {
            throw new InvalidCredentialsException();
        }
    }
}
