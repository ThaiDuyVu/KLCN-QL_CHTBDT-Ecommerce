package com.example.backend.auth.service;

import com.example.backend.auth.dto.UpdateUserRequest;
import com.example.backend.auth.dto.UserPageResponse;
import com.example.backend.auth.dto.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse getUserById(UUID userId);
    UserPageResponse getUsers(int page, int size);
    UserResponse updateUser(UUID userId, UpdateUserRequest request);
}