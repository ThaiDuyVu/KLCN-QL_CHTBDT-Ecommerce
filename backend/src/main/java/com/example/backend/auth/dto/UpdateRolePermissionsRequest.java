package com.example.backend.auth.dto;

import java.util.List;
import java.util.UUID;

public class UpdateRolePermissionsRequest {

    private List<UUID> permissionIds;

    public UpdateRolePermissionsRequest() {
    }

    public List<UUID> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<UUID> permissionIds) {
        this.permissionIds = permissionIds;
    }
}