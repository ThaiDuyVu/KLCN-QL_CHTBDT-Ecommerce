package com.example.backend.auth.service;

import com.example.backend.auth.dto.LoginSessionResponse;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class LoginAuthenticationResult {

    private final LoginSessionResponse response;
    private final String accessToken;
    private final String refreshToken;

    public LoginAuthenticationResult(
            LoginSessionResponse response,
            String accessToken,
            String refreshToken
    ) {
        this.response = response;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public LoginSessionResponse getResponse() {
        return response;
    }

    @JsonIgnore
    public String getAccessToken() {
        return accessToken;
    }

    @JsonIgnore
    public String getRefreshToken() {
        return refreshToken;
    }
}
