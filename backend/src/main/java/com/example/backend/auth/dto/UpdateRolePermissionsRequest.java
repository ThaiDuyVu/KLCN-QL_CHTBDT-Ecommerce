package com.example.backend.auth.dto;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class UpdateRolePermissionsRequest {

    @NotNull(message = "permissionIds là bắt buộc; dùng [] để gỡ toàn bộ quyền")
    private List<@NotNull UUID> permissionIds;

    public UpdateRolePermissionsRequest() {
    }

    public List<UUID> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<UUID> permissionIds) {
        this.permissionIds = permissionIds;
    }
}