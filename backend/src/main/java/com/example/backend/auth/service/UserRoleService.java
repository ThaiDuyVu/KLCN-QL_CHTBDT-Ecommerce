package com.example.backend.auth.service;

import com.example.backend.auth.dto.UpdateUserRoleRequest;
import com.example.backend.auth.dto.UserRoleResponse;

import java.util.UUID;

public interface UserRoleService {

    UserRoleResponse getUserRole(UUID userId);
    void updateUserRole(UUID userId, UpdateUserRoleRequest request);
}