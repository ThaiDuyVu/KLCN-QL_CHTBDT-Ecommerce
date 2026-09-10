package com.example.backend.auth.controller;

import com.example.backend.auth.dto.PermissionResponse;
import com.example.backend.auth.dto.RoleResponse;
import com.example.backend.auth.service.RoleService;
import org.springframework.http.ResponseEntity;
import com.example.backend.auth.dto.UpdateRolePermissionsRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ResponseEntity<List<RoleResponse>> getRoles() {

        return ResponseEntity.ok(
                roleService.getRoles()
        );
    }
    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<List<PermissionResponse>> getPermissionsByRole(
            @PathVariable UUID roleId
    ) {
        return ResponseEntity.ok(
                roleService.getPermissionsByRole(roleId)
        );
    }
    @PutMapping("/{roleId}/permissions")
    public ResponseEntity<Void> updateRolePermissions(
            @PathVariable UUID roleId,
            @RequestBody UpdateRolePermissionsRequest request
    ) {
        roleService.updateRolePermissions(roleId, request);

        return ResponseEntity.noContent().build();
    }
}