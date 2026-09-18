package com.example.backend.auth.controller;
import com.example.backend.auth.dto.*;
import com.example.backend.auth.entity.UserStatus;
import com.example.backend.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    public UserController(UserService userService) { this.userService = userService; }
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER_VIEW')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) { return ResponseEntity.ok(userService.getUserById(userId)); }
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER_VIEW')")
    public ResponseEntity<UserPageResponse> getUsers(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) UserStatus status, @RequestParam(required=false) UUID roleId) {
        return ResponseEntity.ok(userService.getUsers(page, size, keyword, status, roleId));
    }
    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER_UPDATE')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) { return ResponseEntity.ok(userService.updateUser(userId, request)); }
    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('USER_DISABLE')")
    public ResponseEntity<UserResponse> updateStatus(@PathVariable UUID userId, @Valid @RequestBody UpdateUserStatusRequest request) { return ResponseEntity.ok(userService.updateUserStatus(userId, request)); }
}
