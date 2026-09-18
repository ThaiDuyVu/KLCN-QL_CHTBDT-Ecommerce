package com.example.backend.auth.service;

import com.example.backend.auth.dto.PermissionResponse;
import com.example.backend.auth.dto.RoleResponse;
import com.example.backend.auth.dto.UpdateRolePermissionsRequest;

import java.util.List;
import java.util.UUID;

public interface RoleService {

    List<RoleResponse> getRoles();
    List<PermissionResponse> getPermissions();
    List<PermissionResponse> getPermissionsByRole(UUID roleId);
    void updateRolePermissions(
            UUID roleId,
            UpdateRolePermissionsRequest request
    );
}