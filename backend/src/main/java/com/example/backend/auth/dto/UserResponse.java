package com.example.backend.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class UserResponse {

    private UUID userId;
    private String username;
    private String email;
    private String phone;
    private String displayName;
    private String status;
    private OffsetDateTime createdAt;

    public UserResponse() {
    }

    public UserResponse(
            UUID userId,
            String username,
            String email,
            String phone,
            String displayName,
            String status,
            OffsetDateTime createdAt
    ) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.displayName = displayName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }
    public String getDisplayName() {
        return displayName;
    }
    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}