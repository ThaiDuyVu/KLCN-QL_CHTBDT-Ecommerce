package com.example.backend.auth.controller;

import com.example.backend.auth.dto.PermissionResponse;
import com.example.backend.auth.dto.RoleResponse;
import com.example.backend.auth.service.RoleService;
import org.springframework.http.ResponseEntity;
import com.example.backend.auth.dto.UpdateRolePermissionsRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN') or hasAnyAuthority('USER_VIEW', 'USER_ROLE_VIEW', 'ROLE_PERMISSION_VIEW')")
    public ResponseEntity<List<RoleResponse>> getRoles() {

        return ResponseEntity.ok(
                roleService.getRoles()
        );
    }
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('ROLE_PERMISSION_VIEW')")
    public ResponseEntity<List<PermissionResponse>> getPermissionsByRole(
            @PathVariable UUID roleId
    ) {
        return ResponseEntity.ok(
                roleService.getPermissionsByRole(roleId)
        );
    }
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ADMIN') or hasAnyAuthority('ROLE_PERMISSION_ASSIGN', 'ROLE_PERMISSION_REMOVE')")
    public ResponseEntity<Void> updateRolePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request
    ) {
        roleService.updateRolePermissions(roleId, request);

        return ResponseEntity.noContent().build();
    }
}