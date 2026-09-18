package com.example.backend.auth.service;

import com.example.backend.auth.dto.UpdateUserRequest;
import com.example.backend.auth.dto.UserPageResponse;
import com.example.backend.auth.dto.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse getUserById(UUID userId);
    UserPageResponse getUsers(int page, int size);
    UserPageResponse getUsers(int page, int size, String keyword, com.example.backend.auth.entity.UserStatus status, UUID roleId);
    UserResponse updateUserStatus(UUID userId, com.example.backend.auth.dto.UpdateUserStatusRequest request);
    UserResponse updateUser(UUID userId, UpdateUserRequest request);
}