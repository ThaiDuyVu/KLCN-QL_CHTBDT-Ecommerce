package com.example.backend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class RolePermissionId implements Serializable {
    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    public RolePermissionId() {
    }

    public RolePermissionId(UUID roleId, UUID permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(UUID permissionId) {
        this.permissionId = permissionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof RolePermissionId)) {
            return false;
        }

        RolePermissionId that = (RolePermissionId) o;

        return roleId != null
                && roleId.equals(that.roleId)
                && permissionId != null
                && permissionId.equals(that.permissionId);
    }

    @Override
    public int hashCode() {
        int result = roleId != null ? roleId.hashCode() : 0;
        result = 31 * result + (permissionId != null ? permissionId.hashCode() : 0);
        return result;
    }

}
