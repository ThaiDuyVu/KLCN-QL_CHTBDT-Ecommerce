package com.example.backend.auth.dto;

import java.util.UUID;

public class UpdateUserRoleRequest {

    private UUID roleId;

    public UpdateUserRoleRequest() {
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }
}