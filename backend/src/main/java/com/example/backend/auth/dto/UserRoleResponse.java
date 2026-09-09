package com.example.backend.auth.dto;

import java.util.UUID;

public class UserRoleResponse {

    private UUID userId;
    private UUID roleId;
    private String roleName;
    private String description;

    public UserRoleResponse(
            UUID userId,
            UUID roleId,
            String roleName,
            String description
    ) {
        this.userId = userId;
        this.roleId = roleId;
        this.roleName = roleName;
        this.description = description;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getDescription() {
        return description;
    }
}