package com.example.backend.auth.controller;

import com.example.backend.auth.dto.PermissionResponse;
import com.example.backend.auth.dto.RoleResponse;
import com.example.backend.auth.service.CustomUserDetailsService;
import com.example.backend.auth.service.JwtService;
import com.example.backend.auth.service.RoleService;
import com.example.backend.common.security.AuthCookieProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.example.backend.auth.dto.UpdateRolePermissionsRequest;
import com.example.backend.auth.exception.PermissionNotFoundException;
import com.example.backend.auth.exception.ProtectedPermissionException;
import com.example.backend.auth.exception.RoleNotFoundException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoleController.class)
@WithMockUser
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private AuthCookieProperties authCookieProperties;

    @Test
    void getRoles_shouldReturn200_whenRolesExist() throws Exception {

        UUID adminId = UUID.randomUUID();

        RoleResponse admin = new RoleResponse(
                adminId,
                "ADMIN",
                "Administrator"
        );

        when(roleService.getRoles())
                .thenReturn(List.of(admin));

        mockMvc.perform(
                        get("/api/roles")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleId").value(adminId.toString()))
                .andExpect(jsonPath("$[0].roleName").value("ADMIN"))
                .andExpect(jsonPath("$[0].description").value("Administrator"));
    }

    @Test
    void getRoles_shouldReturnEmptyList_whenNoRolesExist() throws Exception {

        when(roleService.getRoles())
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/api/roles")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getPermissionsByRole_shouldReturn200_whenPermissionsExist()
            throws Exception {

        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();

        PermissionResponse permission = new PermissionResponse(
                permissionId,
                "USER_VIEW",
                "View users"
        );

        when(roleService.getPermissionsByRole(roleId))
                .thenReturn(List.of(permission));

        mockMvc.perform(
                        get("/api/roles/{roleId}/permissions", roleId)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[0].permissionId")
                                .value(permissionId.toString())
                )
                .andExpect(
                        jsonPath("$[0].permissionName")
                                .value("USER_VIEW")
                )
                .andExpect(
                        jsonPath("$[0].description")
                                .value("View users")
                );
    }
    @Test
    void getPermissionsByRole_shouldReturnEmptyList_whenNoPermissionsExist()
            throws Exception {

        UUID roleId = UUID.randomUUID();

        when(roleService.getPermissionsByRole(roleId))
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/api/roles/{roleId}/permissions", roleId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
    @Test
    void updateRolePermissions_shouldReturn204_whenUpdateSuccessful()
            throws Exception {

        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();

        String requestJson = """
            {
                "permissionIds": ["%s"]
            }
            """.formatted(permissionId);

        mockMvc.perform(
                        put("/api/roles/{roleId}/permissions", roleId)
                                .with(csrf())
                                .contentType("application/json")
                                .content(requestJson)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(roleService)
                .updateRolePermissions(
                        any(UUID.class),
                        any(UpdateRolePermissionsRequest.class)
                );
    }
    @Test
    void updateRolePermissions_shouldReturn404_whenRoleNotFound()
            throws Exception {

        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();

        String requestJson = """
            {
                "permissionIds": ["%s"]
            }
            """.formatted(permissionId);

        doThrow(
                new RoleNotFoundException(
                        "Không tìm thấy role với ID: " + roleId
                )
        ).when(roleService)
                .updateRolePermissions(
                        any(UUID.class),
                        any(UpdateRolePermissionsRequest.class)
                );

        mockMvc.perform(
                        put("/api/roles/{roleId}/permissions", roleId)
                                .with(csrf())
                                .contentType("application/json")
                                .content(requestJson)
                )
                .andExpect(status().isNotFound());
    }
    @Test
    void updateRolePermissions_shouldReturn404_whenPermissionNotFound()
            throws Exception {

        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();

        String requestJson = """
            {
                "permissionIds": ["%s"]
            }
            """.formatted(permissionId);

        doThrow(
                new PermissionNotFoundException(
                        "Không tìm thấy permission với ID: " + permissionId
                )
        ).when(roleService)
                .updateRolePermissions(
                        any(UUID.class),
                        any(UpdateRolePermissionsRequest.class)
                );

        mockMvc.perform(
                        put("/api/roles/{roleId}/permissions", roleId)
                                .with(csrf())
                                .contentType("application/json")
                                .content(requestJson)
                )
                .andExpect(status().isNotFound());
    }
    @Test
    void updateRolePermissions_shouldReturn403_whenProtectedPermission()
            throws Exception {

        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();

        String requestJson = """
            {
                "permissionIds": ["%s"]
            }
            """.formatted(permissionId);

        doThrow(
                new ProtectedPermissionException(
                        "Không được phép gán permission quản trị phân quyền cho role này"
                )
        ).when(roleService)
                .updateRolePermissions(
                        any(UUID.class),
                        any(UpdateRolePermissionsRequest.class)
                );

        mockMvc.perform(
                        put("/api/roles/{roleId}/permissions", roleId)
                                .with(csrf())
                                .contentType("application/json")
                                .content(requestJson)
                )
                .andExpect(status().isForbidden());
    }
}
