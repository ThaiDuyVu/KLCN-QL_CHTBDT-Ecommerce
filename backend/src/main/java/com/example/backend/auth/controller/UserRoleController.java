package com.example.backend.auth.controller;

import com.example.backend.auth.dto.UserRoleResponse;
import com.example.backend.auth.service.UserRoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.backend.auth.dto.UpdateUserRoleRequest;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserRoleController {

    private final UserRoleService userRoleService;

    public UserRoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @GetMapping("/{userId}/role")
    public ResponseEntity<UserRoleResponse> getUserRole(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(
                userRoleService.getUserRole(userId)
        );
    }
    @PutMapping("/{userId}/role")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable UUID userId,
            @RequestBody UpdateUserRoleRequest request
    ) {
        userRoleService.updateUserRole(userId, request);

        return ResponseEntity.noContent().build();
    }
}