package com.example.backend.auth.service;

import com.example.backend.auth.dto.PermissionResponse;
import com.example.backend.auth.dto.RoleResponse;
import com.example.backend.auth.entity.Permission;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.RolePermission;
import com.example.backend.auth.exception.PermissionNotFoundException;
import com.example.backend.auth.exception.ProtectedPermissionException;
import com.example.backend.auth.exception.RoleNotFoundException;
import com.example.backend.auth.dto.UpdateRolePermissionsRequest;
import com.example.backend.auth.repository.PermissionRepository;
import com.example.backend.auth.repository.RolePermissionRepository;
import com.example.backend.auth.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {

    private static final Set<String> PROTECTED_PERMISSIONS = Set.of("USER_ROLE_VIEW", "USER_ROLE_ASSIGN", "USER_ROLE_REMOVE", "ROLE_PERMISSION_VIEW", "ROLE_PERMISSION_ASSIGN", "ROLE_PERMISSION_REMOVE");
    private PermissionResponse permissionResponse(Permission p) {
        var response = new PermissionResponse(p.getPermissionId(), p.getPermissionName(), p.getDescription());
        response.setProtectedPermission(PROTECTED_PERMISSIONS.contains(p.getPermissionName()));
        return response;
    }
    @Override
    public List<PermissionResponse> getPermissions() {
        return permissionRepository.findAll(org.springframework.data.domain.Sort.by("permissionName")).stream().map(this::permissionResponse).toList();
    }
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    public RoleServiceImpl(
            RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository,
            PermissionRepository permissionRepository
    ) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public List<RoleResponse> getRoles() {

        return roleRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private RoleResponse toResponse(Role role) {

        return new RoleResponse(
                role.getRoleId(),
                role.getRoleName(),
                role.getDescription()
        );
    }
    @Override
    public List<PermissionResponse> getPermissionsByRole(UUID roleId) {

        if (!roleRepository.existsById(roleId)) throw new RoleNotFoundException("Không tìm thấy role: " + roleId);
        return rolePermissionRepository.findByRole_RoleId(roleId).stream()
                .map(link -> permissionResponse(link.getPermission())).toList();
    }

    @Override
    @Transactional
    public void updateRolePermissions(
            UUID roleId,
            UpdateRolePermissionsRequest request
    ) {

        Role role = roleRepository.findByIdForUpdate(roleId)
                .orElseThrow(() ->
                        new RoleNotFoundException(
                                "Không tìm thấy role với ID: " + roleId
                        )
                );

        if ("ADMIN".equals(role.getRoleName())) {
            throw new ProtectedPermissionException(
                    "Không được phép chỉnh sửa permission của ADMIN"
            );
        }

        List<UUID> permissionIds = request.getPermissionIds();

        if (permissionIds == null) {
            permissionIds = List.of();
        }

        Set<UUID> uniquePermissionIds =
                new HashSet<>(permissionIds);

        List<Permission> permissions =
                permissionRepository.findAllById(uniquePermissionIds);

        if (permissions.size() != uniquePermissionIds.size()) {

            Set<UUID> foundPermissionIds = permissions.stream()
                    .map(Permission::getPermissionId)
                    .collect(Collectors.toSet());

            UUID missingPermissionId = uniquePermissionIds.stream()
                    .filter(id -> !foundPermissionIds.contains(id))
                    .findFirst()
                    .orElseThrow();

            throw new PermissionNotFoundException(
                    "Không tìm thấy permission với ID: "
                            + missingPermissionId
            );
        }

        boolean containsProtectedPermission = permissions.stream()
                .anyMatch(permission ->
                        PROTECTED_PERMISSIONS.contains(
                                permission.getPermissionName()
                        )
                );

        if (containsProtectedPermission) {
            throw new ProtectedPermissionException(
                    "Không được phép gán permission quản trị phân quyền "
                            + "cho role này"
            );
        }

        List<RolePermission> existing = rolePermissionRepository.findByRole_RoleId(roleId);
        Set<UUID> current = existing.stream().map(link -> link.getPermission().getPermissionId()).collect(Collectors.toSet());
        if (uniquePermissionIds.stream().anyMatch(id -> !current.contains(id))) ManagementAuthorization.require("ROLE_PERMISSION_ASSIGN");
        if (current.stream().anyMatch(id -> !uniquePermissionIds.contains(id))) ManagementAuthorization.require("ROLE_PERMISSION_REMOVE");
        rolePermissionRepository.deleteAll(existing.stream().filter(link -> !uniquePermissionIds.contains(link.getPermission().getPermissionId())).toList());
        rolePermissionRepository.flush();
        rolePermissionRepository.saveAllAndFlush(permissions.stream().filter(p -> !current.contains(p.getPermissionId())).map(p -> new RolePermission(role, p)).toList());
    }
}
