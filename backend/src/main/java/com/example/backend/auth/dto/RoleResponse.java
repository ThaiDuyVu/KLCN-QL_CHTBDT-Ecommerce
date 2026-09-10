package com.example.backend.auth.dto;

import java.util.UUID;

public class RoleResponse {

    private UUID roleId;
    private String roleName;
    private String description;

    public RoleResponse(
            UUID roleId,
            String roleName,
            String description
    ) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.description = description;
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