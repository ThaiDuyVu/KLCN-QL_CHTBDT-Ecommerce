package com.example.backend.auth.controller;

import com.example.backend.auth.dto.UpdateUserRoleRequest;
import com.example.backend.auth.dto.UserRoleResponse;
import com.example.backend.auth.exception.UserNotFoundException;
import com.example.backend.auth.exception.UserRoleNotFoundException;
import com.example.backend.auth.service.CustomUserDetailsService;
import com.example.backend.auth.service.JwtService;
import com.example.backend.auth.service.UserRoleService;
import com.example.backend.common.security.AuthCookieProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import com.example.backend.auth.dto.UpdateUserRoleRequest;
import com.example.backend.auth.exception.RoleNotFoundException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserRoleController.class)
@org.springframework.context.annotation.Import(UserRoleControllerTest.MethodSecurityConfiguration.class)
@WithMockUser(authorities = "ADMIN")
class UserRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRoleService userRoleService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private AuthCookieProperties authCookieProperties;

    @Test
    void getUserRole_shouldReturn200_whenUserHasRole()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        UserRoleResponse response = new UserRoleResponse(
                userId,
                roleId,
                "MANAGER",
                "Manager"
        );

        when(userRoleService.getUserRole(userId))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/users/{userId}/role", userId)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.userId")
                                .value(userId.toString())
                )
                .andExpect(
                        jsonPath("$.roleId")
                                .value(roleId.toString())
                )
                .andExpect(
                        jsonPath("$.roleName")
                                .value("MANAGER")
                )
                .andExpect(
                        jsonPath("$.description")
                                .value("Manager")
                );
    }

    @Test
    void getUserRole_shouldReturn404_whenUserNotFound()
            throws Exception {

        UUID userId = UUID.randomUUID();

        when(userRoleService.getUserRole(userId))
                .thenThrow(
                        new UserNotFoundException(
                                "Không tìm thấy người dùng với ID: " + userId
                        )
                );

        mockMvc.perform(
                        get("/api/users/{userId}/role", userId)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserRole_shouldReturn404_whenUserHasNoRole()
            throws Exception {

        UUID userId = UUID.randomUUID();

        when(userRoleService.getUserRole(userId))
                .thenThrow(
                        new UserRoleNotFoundException(
                                "Người dùng chưa được gán role"
                        )
                );

        mockMvc.perform(
                        get("/api/users/{userId}/role", userId)
                )
                .andExpect(status().isNotFound());
    }
    @Test
    void updateUserRole_shouldReturn204_whenUpdateSuccess()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setRoleId(roleId);

        mockMvc.perform(
                        put("/api/users/{userId}/role", userId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "roleId": "%s"
                                    }
                                    """.formatted(roleId))
                )
                .andExpect(status().isNoContent());
    }
    @Test
    void updateUserRole_shouldReturn404_whenUserNotFound()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        doThrow(
                new UserNotFoundException(
                        "Không tìm thấy người dùng với ID: " + userId
                )
        ).when(userRoleService)
                .updateUserRole(
                        eq(userId),
                        any(UpdateUserRoleRequest.class)
                );

        mockMvc.perform(
                        put("/api/users/{userId}/role", userId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "roleId": "%s"
                                    }
                                    """.formatted(roleId))
                )
                .andExpect(status().isNotFound());
    }
    @Test
    void updateUserRole_shouldReturn404_whenRoleNotFound()
            throws Exception {

        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        doThrow(
                new RoleNotFoundException(
                        "Không tìm thấy role với ID: " + roleId
                )
        ).when(userRoleService)
                .updateUserRole(
                        eq(userId),
                        any(UpdateUserRoleRequest.class)
                );

        mockMvc.perform(
                        put("/api/users/{userId}/role", userId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "roleId": "%s"
                                    }
                                    """.formatted(roleId))
                )
                .andExpect(status().isNotFound());
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class MethodSecurityConfiguration {}
    @Test
    @WithMockUser(authorities = "USER_VIEW")
    void updateRole_shouldRejectUserWithoutAssignmentPermission() throws Exception {
        mockMvc.perform(put("/api/users/{id}/role", UUID.randomUUID()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }
}
