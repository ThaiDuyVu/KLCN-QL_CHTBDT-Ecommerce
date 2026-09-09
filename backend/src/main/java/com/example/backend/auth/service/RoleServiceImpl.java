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

        return rolePermissionRepository.findByRole_RoleId(roleId)
                .stream()
                .map(rolePermission -> {
                    var permission = rolePermission.getPermission();

                    return new PermissionResponse(
                            permission.getPermissionId(),
                            permission.getPermissionName(),
                            permission.getDescription()
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public void updateRolePermissions(
            UUID roleId,
            UpdateRolePermissionsRequest request
    ) {

        Role role = roleRepository.findById(roleId)
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

        Set<String> protectedPermissions = Set.of(
                "USER_ROLE_VIEW",
                "USER_ROLE_ASSIGN",
                "USER_ROLE_REMOVE",
                "ROLE_PERMISSION_VIEW",
                "ROLE_PERMISSION_ASSIGN",
                "ROLE_PERMISSION_REMOVE"
        );

        boolean containsProtectedPermission = permissions.stream()
                .anyMatch(permission ->
                        protectedPermissions.contains(
                                permission.getPermissionName()
                        )
                );

        if (containsProtectedPermission) {
            throw new ProtectedPermissionException(
                    "Không được phép gán permission quản trị phân quyền "
                            + "cho role này"
            );
        }

        rolePermissionRepository
                .deleteAll(
                        rolePermissionRepository
                                .findByRole_RoleId(roleId)
                );

        List<RolePermission> rolePermissions = permissions.stream()
                .map(permission ->
                        new RolePermission(role, permission)
                )
                .toList();

        rolePermissionRepository.saveAll(rolePermissions);
    }
}