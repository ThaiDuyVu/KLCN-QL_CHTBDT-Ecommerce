package com.example.backend.auth.controller;
import com.example.backend.auth.dto.PermissionResponse;
import com.example.backend.auth.service.RoleService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {
    private final RoleService roleService;
    public PermissionController(RoleService roleService) { this.roleService = roleService; }
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('ROLE_PERMISSION_VIEW')")
    public List<PermissionResponse> getPermissions() { return roleService.getPermissions(); }
}
