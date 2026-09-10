package com.example.backend.auth.controller;

import com.example.backend.auth.dto.UserPageResponse;
import com.example.backend.auth.dto.UserResponse;
import com.example.backend.auth.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import com.example.backend.auth.dto.UpdateUserRequest;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(
                userService.getUserById(userId)
        );
    }

    @GetMapping
    public ResponseEntity<UserPageResponse> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("Page không được nhỏ hơn 0");
        }

        if (size <= 0) {
            throw new IllegalArgumentException("Size phải lớn hơn 0");
        }

        return ResponseEntity.ok(
                userService.getUsers(page, size)
        );
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID userId,
            @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(
                userService.updateUser(userId, request)
        );
    }
}