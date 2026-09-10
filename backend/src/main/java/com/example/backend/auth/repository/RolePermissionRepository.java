package com.example.backend.auth.repository;

import com.example.backend.auth.entity.RolePermission;
import com.example.backend.auth.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RolePermissionRepository
        extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findByRole_RoleId(UUID roleId);

    Optional<RolePermission> findByRole_RoleIdAndPermission_PermissionId(
            UUID roleId,
            UUID permissionId
    );

    boolean existsByRole_RoleIdAndPermission_PermissionId(
            UUID roleId,
            UUID permissionId
    );
}