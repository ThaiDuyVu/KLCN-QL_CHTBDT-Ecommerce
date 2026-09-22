package com.example.backend.common.security;

import com.example.backend.auth.exception.InvalidJwtTokenException;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.auth.service.CustomUserDetailsService;
import com.example.backend.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;
    private final AuthCookieProperties authCookieProperties;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService customUserDetailsService,
            AuthCookieProperties authCookieProperties
    ) {
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
        this.authCookieProperties = authCookieProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken = extractAccessToken(request);

        if (accessToken != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateAccessToken(accessToken, request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateAccessToken(
            String accessToken,
            HttpServletRequest request
    ) {
        try {
            UUID userId = jwtService.extractUserId(
                    accessToken,
                    ACCESS_TOKEN_TYPE
            );

            String tokenRoleName = jwtService.extractRole(accessToken)
                    .orElseThrow(InvalidJwtTokenException::new);

            AuthenticatedUserPrincipal principal = customUserDetailsService
                    .loadUserByUserId(userId);

            if (!"ACTIVE".equals(principal.getStatus())) {
                SecurityContextHolder.clearContext();
                return;
            }

            if (!tokenRoleName.equals(principal.getRoleName())) {
                throw new InvalidJwtTokenException();
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request)
            );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
        } catch (
                InvalidJwtTokenException
                        | UsernameNotFoundException
                        | IllegalArgumentException exception
        ) {
            SecurityContextHolder.clearContext();
        }
    }

    private String extractAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (authCookieProperties.getAccessTokenCookieName()
                    .equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
