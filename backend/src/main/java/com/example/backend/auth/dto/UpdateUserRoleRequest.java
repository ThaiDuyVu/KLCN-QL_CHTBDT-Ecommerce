package com.example.backend.auth.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public class UpdateUserRoleRequest {

    @NotNull(message = "roleId là bắt buộc")
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