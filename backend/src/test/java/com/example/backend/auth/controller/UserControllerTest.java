package com.example.backend.auth.controller;

import com.example.backend.auth.dto.UpdateUserRequest;
import com.example.backend.auth.dto.UserPageResponse;
import com.example.backend.auth.dto.UserResponse;
import com.example.backend.auth.exception.UserNotFoundException;
import com.example.backend.auth.service.CustomUserDetailsService;
import com.example.backend.auth.service.JwtService;
import com.example.backend.auth.service.UserService;
import com.example.backend.common.security.AuthCookieProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@WithMockUser
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private AuthCookieProperties authCookieProperties;

    @Test
    void getUserById_shouldReturn200_whenUserExists() throws Exception {

        UUID userId = UUID.randomUUID();

        UserResponse response = new UserResponse(
                userId,
                "testuser",
                "test@example.com",
                "0123456789",
                "Test User",
                "ACTIVE",
                OffsetDateTime.now()
        );

        when(userService.getUserById(userId))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/users/{userId}", userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getUserById_shouldReturn404_whenUserDoesNotExist() throws Exception {

        UUID userId = UUID.randomUUID();

        when(userService.getUserById(userId))
                .thenThrow(
                        new UserNotFoundException(
                                "Không tìm thấy người dùng với ID: " + userId
                        )
                );

        mockMvc.perform(
                        get("/api/users/{userId}", userId)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void getUsers_shouldReturn200_whenUsersExist() throws Exception {

        UUID userId = UUID.randomUUID();

        UserResponse user = new UserResponse(
                userId,
                "testuser",
                "test@example.com",
                "0123456789",
                "Test User",
                "ACTIVE",
                OffsetDateTime.now()
        );

        UserPageResponse response = new UserPageResponse(
                List.of(user),
                0,
                20,
                1,
                1
        );

        when(userService.getUsers(0, 20))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/users")
                                .param("page", "0")
                                .param("size", "20")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("testuser"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void updateUser_shouldReturn200_whenUserExists() throws Exception {

        UUID userId = UUID.randomUUID();

        UserResponse response = new UserResponse(
                userId,
                "testuser",
                "new@example.com",
                "0911111111",
                "New Name",
                "ACTIVE",
                OffsetDateTime.now()
        );

        when(userService.updateUser(
                eq(userId),
                any(UpdateUserRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        put("/api/users/{userId}", userId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{"
                                        + "\"displayName\":\"New Name\","
                                        + "\"email\":\"new@example.com\","
                                        + "\"phone\":\"0911111111\""
                                        + "}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.phone").value("0911111111"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void updateUser_shouldReturn404_whenUserDoesNotExist() throws Exception {

        UUID userId = UUID.randomUUID();

        when(userService.updateUser(
                eq(userId),
                any(UpdateUserRequest.class)
        )).thenThrow(
                new UserNotFoundException(
                        "Không tìm thấy người dùng với ID: " + userId
                )
        );

        mockMvc.perform(
                        put("/api/users/{userId}", userId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{"
                                        + "\"displayName\":\"New Name\","
                                        + "\"email\":\"new@example.com\","
                                        + "\"phone\":\"0911111111\""
                                        + "}")
                )
                .andExpect(status().isNotFound());
    }
}
