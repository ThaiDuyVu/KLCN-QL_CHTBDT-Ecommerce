package com.example.backend.auth.service;

import com.example.backend.auth.dto.UpdateUserRoleRequest;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.entity.UserRole;
import com.example.backend.auth.exception.RoleNotFoundException;
import com.example.backend.auth.exception.UserNotFoundException;
import com.example.backend.auth.exception.UserRoleNotFoundException;
import com.example.backend.auth.repository.RoleRepository;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.auth.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @InjectMocks
    private UserRoleServiceImpl userRoleService;
    @Mock
    private RoleRepository roleRepository;
    @Test
    void getUserRole_shouldReturnRole_whenUserHasRole() {

        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        User user = new User();
        user.setUserId(userId);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());

        Role role = new Role();
        role.setRoleId(roleId);
        role.setRoleName("MANAGER");
        role.setDescription("Manager");

        UserRole userRole = new UserRole(user, role);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(userRoleRepository.findByUser_UserId(userId))
                .thenReturn(Optional.of(userRole));

        var result = userRoleService.getUserRole(userId);

        assertEquals(userId, result.getUserId());
        assertEquals(roleId, result.getRoleId());
        assertEquals("MANAGER", result.getRoleName());
        assertEquals("Manager", result.getDescription());
    }

    @Test
    void getUserRole_shouldThrowUserNotFound_whenUserDoesNotExist() {

        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userRoleService.getUserRole(userId)
        );

        verify(userRoleRepository, never())
                .findByUser_UserId(userId);
    }

    @Test
    void getUserRole_shouldThrowUserRoleNotFound_whenUserHasNoRole() {

        UUID userId = UUID.randomUUID();

        User user = new User();
        user.setUserId(userId);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(userRoleRepository.findByUser_UserId(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                UserRoleNotFoundException.class,
                () -> userRoleService.getUserRole(userId)
        );
    }
    @Test
    void updateUserRole_shouldCreateUserRole_whenUserHasNoRole() {

        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        User user = new User();
        user.setUserId(userId);

        Role role = new Role();
        role.setRoleId(roleId);
        role.setRoleName("MANAGER");
        role.setDescription("Manager");

        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setRoleId(roleId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(role));

        when(userRoleRepository.findByUser_UserId(userId))
                .thenReturn(Optional.empty());

        userRoleService.updateUserRole(userId, request);

        verify(userRoleRepository).save(
                org.mockito.ArgumentMatchers.argThat(userRole ->
                        userRole.getUser().equals(user)
                                && userRole.getRole().equals(role)
                )
        );
    }
    @Test
    void updateUserRole_shouldReplaceRole_whenUserAlreadyHasRole() {

        UUID userId = UUID.randomUUID();
        UUID oldRoleId = UUID.randomUUID();
        UUID newRoleId = UUID.randomUUID();

        User user = new User();
        user.setUserId(userId);

        Role oldRole = new Role();
        oldRole.setRoleId(oldRoleId);
        oldRole.setRoleName("STAFF");

        Role newRole = new Role();
        newRole.setRoleId(newRoleId);
        newRole.setRoleName("MANAGER");

        UserRole userRole = new UserRole(user, oldRole);

        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setRoleId(newRoleId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(roleRepository.findById(newRoleId))
                .thenReturn(Optional.of(newRole));

        when(userRoleRepository.findByUser_UserId(userId))
                .thenReturn(Optional.of(userRole));

        userRoleService.updateUserRole(userId, request);

        assertEquals(newRole, userRole.getRole());
        assertEquals(userId, userRole.getId().getUserId());
        assertEquals(newRoleId, userRole.getId().getRoleId());

        verify(userRoleRepository).save(userRole);
    }
    @Test
    void updateUserRole_shouldThrowRoleNotFound_whenRoleDoesNotExist() {

        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        User user = new User();
        user.setUserId(userId);

        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setRoleId(roleId);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(roleRepository.findById(roleId))
                .thenReturn(Optional.empty());

        assertThrows(
                RoleNotFoundException.class,
                () -> userRoleService.updateUserRole(userId, request)
        );

        verify(userRoleRepository, never())
                .findByUser_UserId(userId);
    }
}