package com.example.backend.auth.dto;

import java.util.UUID;

public class LoginSessionResponse {

    private UUID userId;
    private String username;
    private String displayName;
    private String roleName;

    public LoginSessionResponse() {
    }

    public LoginSessionResponse(
            UUID userId,
            String username,
            String displayName,
            String roleName
    ) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.roleName = roleName;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
