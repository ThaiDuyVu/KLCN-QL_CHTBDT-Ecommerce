package com.example.backend.auth.controller;

import com.example.backend.auth.dto.LoginRequest;
import com.example.backend.auth.dto.LoginSessionResponse;
import com.example.backend.auth.exception.InvalidCredentialsException;
import com.example.backend.auth.exception.InvalidJwtTokenException;
import com.example.backend.auth.exception.InvalidRefreshTokenException;
import com.example.backend.auth.exception.RefreshTokenReuseDetectedException;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.auth.service.AuthService;
import com.example.backend.auth.service.LoginAuthenticationResult;
import com.example.backend.common.security.AuthCookieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    public AuthController(
            AuthService authService,
            AuthCookieService authCookieService
    ) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginSessionResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        LoginAuthenticationResult result = authService.login(request);

        authCookieService.writeAccessTokenCookie(
                response,
                result.getAccessToken()
        );
        authCookieService.writeRefreshTokenCookie(
                response,
                result.getRefreshToken()
        );

        return ResponseEntity.ok(result.getResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginSessionResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            LoginAuthenticationResult result = authService.refresh(
                    authCookieService.readRefreshTokenCookie(request)
            );

            authCookieService.writeAccessTokenCookie(
                    response,
                    result.getAccessToken()
            );
            authCookieService.writeRefreshTokenCookie(
                    response,
                    result.getRefreshToken()
            );

            return ResponseEntity.ok(result.getResponse());
        } catch (
                InvalidCredentialsException
                        | InvalidJwtTokenException
                        | InvalidRefreshTokenException
                        | RefreshTokenReuseDetectedException exception
        ) {
            authCookieService.clearAuthenticationCookies(response);
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(
                authCookieService.readRefreshTokenCookie(request)
        );
        authCookieService.clearAuthenticationCookies(response);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<LoginSessionResponse> getCurrentUser(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(
                authService.getCurrentSession(principal)
        );
    }
}
