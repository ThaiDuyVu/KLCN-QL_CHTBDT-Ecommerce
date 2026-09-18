package com.example.backend.auth.dto;

import java.util.UUID;

public class PermissionResponse {

    private boolean protectedPermission;
    public boolean isProtectedPermission() { return protectedPermission; }
    public void setProtectedPermission(boolean value) { this.protectedPermission = value; }
    private UUID permissionId;
    private String permissionName;
    private String description;

    public PermissionResponse(
            UUID permissionId,
            String permissionName,
            String description
    ) {
        this.permissionId = permissionId;
        this.permissionName = permissionName;
        this.description = description;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public String getDescription() {
        return description;
    }
}