package com.example.backend.auth.service;

import com.example.backend.auth.dto.LoginRequest;
import com.example.backend.auth.dto.LoginSessionResponse;
import com.example.backend.auth.entity.User;

public interface AuthService {

    User authenticate(String username, String password);

    LoginAuthenticationResult login(LoginRequest request);

    LoginAuthenticationResult refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);

    LoginSessionResponse getCurrentSession(
            AuthenticatedUserPrincipal principal
    );
}
