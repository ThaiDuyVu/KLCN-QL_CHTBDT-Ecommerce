package com.example.backend.auth.service;

import com.example.backend.auth.entity.Permission;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.RolePermission;
import com.example.backend.auth.repository.RolePermissionRepository;
import com.example.backend.auth.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.backend.auth.dto.UpdateRolePermissionsRequest;
import com.example.backend.auth.exception.PermissionNotFoundException;
import com.example.backend.auth.exception.ProtectedPermissionException;
import com.example.backend.auth.exception.RoleNotFoundException;
import com.example.backend.auth.repository.PermissionRepository;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @InjectMocks
    private RoleServiceImpl roleService;

    @Test
    void getRoles_shouldReturnRoles() {

        UUID adminId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        Role admin = new Role();
        admin.setRoleId(adminId);
        admin.setRoleName("ADMIN");
        admin.setDescription("Administrator");

        Role manager = new Role();
        manager.setRoleId(managerId);
        manager.setRoleName("MANAGER");
        manager.setDescription("Manager");

        when(roleRepository.findAll())
                .thenReturn(List.of(admin, manager));

        var result = roleService.getRoles();

        assertEquals(2, result.size());

        assertEquals(adminId, result.get(0).getRoleId());
        assertEquals("ADMIN", result.get(0).getRoleName());
        assertEquals("Administrator", result.get(0).getDescription());

        assertEquals(managerId, result.get(1).getRoleId());
        assertEquals("MANAGER", result.get(1).getRoleName());
        assertEquals("Manager", result.get(1).getDescription());
    }

    @Test
    void getRoles_shouldReturnEmptyList_whenNoRolesExist() {

        when(roleRepository.findAll())
                .thenReturn(List.of());

        var result = roleService.getRoles();

        assertEquals(0, result.size());
    }

    @Test
    void getPermissionsByRole_shouldReturnPermissions() {

        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();

        Role role = new Role();
        role.setRoleId(roleId);
        role.setRoleName("MANAGER");

        Permission permission = new Permission();
        permission.setPermissionId(permissionId);
        permission.setPermissionName("USER_VIEW");
        permission.setDescription("View users");

        RolePermission rolePermission =
                new RolePermission(role, permission);

        when(rolePermissionRepository.findByRole_RoleId(roleId))
                .thenReturn(List.of(rolePermission));

        var result = roleService.getPermissionsByRole(roleId);

        assertEquals(1, result.size());

        assertEquals(
                permissionId,
                result.get(0).getPermissionId()
        );

        assertEquals(
                "USER_VIEW",
                result.get(0).getPermissionName()
        );

        assertEquals(
                "View users",
                result.get(0).getDescription()
        );
    }
    @Test
    void getPermissionsByRole_shouldReturnEmptyList_whenNoPermissionsExist() {

        UUID roleId = UUID.randomUUID();

        when(rolePermissionRepository.findByRole_RoleId(roleId))
                .thenReturn(List.of());

        var result = roleService.getPermissionsByRole(roleId);

        assertEquals(0, result.size());
    }

    @Test
    void updateRolePermissions_shouldUpdatePermissionsSuccessfully() {

        UUID roleId = UUID.randomUUID();
        UUID permissionId1 = UUID.randomUUID();
        UUID permissionId2 = UUID.randomUUID();

        Role role = new Role();
        role.setRoleId(roleId);
        role.setRoleName("MANAGER");

        Permission permission1 = new Permission();
        permission1.setPermissionId(permissionId1);
        permission1.setPermissionName("USER_VIEW");

        Permission permission2 = new Permission();
        permission2.setPermissionId(permissionId2);
        permission2.setPermissionName("EMPLOYEE_VIEW");

        UpdateRolePermissionsRequest request =
                new UpdateRolePermissionsRequest();

        request.setPermissionIds(
                List.of(permissionId1, permissionId2)
        );

        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(role));

        when(permissionRepository.findAllById(
                Set.of(permissionId1, permissionId2)
        )).thenReturn(List.of(permission1, permission2));

        when(rolePermissionRepository.findByRole_RoleId(roleId))
                .thenReturn(List.of());

        roleService.updateRolePermissions(roleId, request);

        verify(rolePermissionRepository)
                .deleteAll(List.of());

        verify(rolePermissionRepository)
                .saveAll(any());
    }

    @Test
    void updateRolePermissions_shouldRemoveAllPermissions_whenPermissionIdsEmpty() {

        UUID roleId = UUID.randomUUID();

        Role role = new Role();
        role.setRoleId(roleId);
        role.setRoleName("CUSTOMER");

        UpdateRolePermissionsRequest request =
                new UpdateRolePermissionsRequest();

        request.setPermissionIds(List.of());

        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(role));

        when(rolePermissionRepository.findByRole_RoleId(roleId))
                .thenReturn(List.of());

        roleService.updateRolePermissions(roleId, request);

        verify(rolePermissionRepository)
                .deleteAll(List.of());

        verify(rolePermissionRepository)
                .saveAll(List.of());
    }

    @Test
    void updateRolePermissions_shouldIgnoreDuplicatePermissionIds() {

        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();

        Role role = new Role();
        role.setRoleId(roleId);
        role.setRoleName("STAFF");

        Permission permission = new Permission();
        permission.setPermissionId(permissionId);
        permission.setPermissionName("EMPLOYEE_VIEW");

        UpdateRolePermissionsRequest request =
                new UpdateRolePermissionsRequest();

        request.setPermissionIds(
                List.of(permissionId, permissionId)
        );

        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(role));

        when(permissionRepository.findAllById(
                Set.of(permissionId)
        )).thenReturn(List.of(permission));

        when(rolePermissionRepository.findByRole_RoleId(roleId))
                .thenReturn(List.of());

        roleService.updateRolePermissions(roleId, request);

        verify(permissionRepository)
                .findAllById(Set.of(permissionId));

        verify(rolePermissionRepository)
                .saveAll(any());
    }
    @Test
    void updateRolePermissions_shouldThrowRoleNotFound_whenRoleDoesNotExist() {

        UUID roleId = UUID.randomUUID();

        UpdateRolePermissionsRequest request =
                new UpdateRolePermissionsRequest();

        request.setPermissionIds(List.of());

        when(roleRepository.findById(roleId))
                .thenReturn(Optional.empty());

        assertThrows(
                RoleNotFoundException.class,
                () -> roleService.updateRolePermissions(roleId, request)
        );

        verify(rolePermissionRepository, never())
                .deleteAll(any());

        verify(rolePermissionRepository, never())
                .saveAll(any());
    }
    @Test
    void updateRolePermissions_shouldThrowPermissionNotFound_whenPermissionDoesNotExist() {

        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();

        Role role = new Role();
        role.setRoleId(roleId);
        role.setRoleName("MANAGER");

        UpdateRolePermissionsRequest request =
                new UpdateRolePermissionsRequest();

        request.setPermissionIds(List.of(permissionId));

        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(role));

        when(permissionRepository.findAllById(
                Set.of(permissionId)
        )).thenReturn(List.of());

        assertThrows(
                PermissionNotFoundException.class,
                () -> roleService.updateRolePermissions(roleId, request)
        );

        verify(rolePermissionRepository, never())
                .deleteAll(any());

        verify(rolePermissionRepository, never())
                .saveAll(any());
    }
    @Test
    void updateRolePermissions_shouldThrowProtectedPermission_whenRoleIsAdmin() {

        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();

        Role role = new Role();
        role.setRoleId(roleId);
        role.setRoleName("ADMIN");

        UpdateRolePermissionsRequest request =
                new UpdateRolePermissionsRequest();

        request.setPermissionIds(List.of(permissionId));

        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(role));

        assertThrows(
                ProtectedPermissionException.class,
                () -> roleService.updateRolePermissions(roleId, request)
        );

        verify(permissionRepository, never())
                .findAllById(any());

        verify(rolePermissionRepository, never())
                .deleteAll(any());

        verify(rolePermissionRepository, never())
                .saveAll(any());
    }
    @Test
    void updateRolePermissions_shouldRejectUserRolePermission() {

        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();

        Role role = new Role();
        role.setRoleId(roleId);
        role.setRoleName("MANAGER");

        Permission permission = new Permission();
        permission.setPermissionId(permissionId);
        permission.setPermissionName("USER_ROLE_ASSIGN");

        UpdateRolePermissionsRequest request =
                new UpdateRolePermissionsRequest();

        request.setPermissionIds(List.of(permissionId));

        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(role));

        when(permissionRepository.findAllById(
                Set.of(permissionId)
        )).thenReturn(List.of(permission));

        assertThrows(
                ProtectedPermissionException.class,
                () -> roleService.updateRolePermissions(roleId, request)
        );

        verify(rolePermissionRepository, never())
                .deleteAll(any());

        verify(rolePermissionRepository, never())
                .saveAll(any());
    }
    @Test
    void updateRolePermissions_shouldRejectRolePermissionPermission() {

        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();

        Role role = new Role();
        role.setRoleId(roleId);
        role.setRoleName("STAFF");

        Permission permission = new Permission();
        permission.setPermissionId(permissionId);
        permission.setPermissionName("ROLE_PERMISSION_ASSIGN");

        UpdateRolePermissionsRequest request =
                new UpdateRolePermissionsRequest();

        request.setPermissionIds(List.of(permissionId));

        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(role));

        when(permissionRepository.findAllById(
                Set.of(permissionId)
        )).thenReturn(List.of(permission));

        assertThrows(
                ProtectedPermissionException.class,
                () -> roleService.updateRolePermissions(roleId, request)
        );

        verify(rolePermissionRepository, never())
                .deleteAll(any());

        verify(rolePermissionRepository, never())
                .saveAll(any());
    }
}