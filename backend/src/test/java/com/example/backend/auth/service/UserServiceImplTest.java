package com.example.backend.auth.service;

import com.example.backend.auth.dto.UserPageResponse;
import com.example.backend.auth.dto.UserResponse;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.exception.EmailAlreadyExistsException;
import com.example.backend.auth.exception.UserNotFoundException;
import com.example.backend.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.example.backend.auth.dto.UpdateUserRequest;

import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.example.backend.auth.repository.UserRoleRepository userRoleRepository;

    @InjectMocks
    private UserServiceImpl userService;



    @Test
    void getUserById_shouldReturnUser_whenUserExists() {

        UUID userId = UUID.randomUUID();

        User user = new User();
        user.setUserId(userId);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPhone("0123456789");
        user.setStatus("ACTIVE");

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(userId);

        assertEquals(userId, response.getUserId());
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("0123456789", response.getPhone());
        assertEquals("ACTIVE", response.getStatus());

        verify(userRepository).findById(userId);
    }

    @Test
    void getUserById_shouldThrowException_whenUserDoesNotExist() {

        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.getUserById(userId)
        );

        verify(userRepository).findById(userId);
    }

    @Test
    void getUsers_shouldReturnUsersWithPagination_whenUsersExist() {

        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        User user1 = new User();
        user1.setUserId(userId1);
        user1.setUsername("user01");
        user1.setEmail("user01@example.com");
        user1.setPhone("0123456789");
        user1.setStatus("ACTIVE");
        user1.setCreatedAt(OffsetDateTime.now());

        User user2 = new User();
        user2.setUserId(userId2);
        user2.setUsername("user02");
        user2.setEmail("user02@example.com");
        user2.setPhone("0987654321");
        user2.setStatus("ACTIVE");
        user2.setCreatedAt(OffsetDateTime.now());

        PageRequest pageable = PageRequest.of(0, 2, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt"), org.springframework.data.domain.Sort.Order.desc("userId")));

        when(userRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(
                        List.of(user1, user2),
                        pageable,
                        2
                ));

        UserPageResponse response = userService.getUsers(0, 2);

        assertEquals(2, response.getContent().size());
        assertEquals(0, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(2, response.getTotalElements());
        assertEquals(1, response.getTotalPages());

        assertEquals("user01", response.getContent().get(0).getUsername());
        assertEquals("user02", response.getContent().get(1).getUsername());

        verify(userRepository).findAll(pageable);
    }

    @Test
    void getUsers_shouldReturnEmptyPage_whenNoUsersExist() {

        PageRequest pageable = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt"), org.springframework.data.domain.Sort.Order.desc("userId")));

        when(userRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                ));

        UserPageResponse response = userService.getUsers(0, 20);

        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getPage());
        assertEquals(20, response.getSize());
        assertEquals(0, response.getTotalElements());
        assertEquals(0, response.getTotalPages());

        verify(userRepository).findAll(pageable);
    }

    @Test
    void updateUser_shouldUpdateAllowedFields_whenUserExists() {

        UUID userId = UUID.randomUUID();

        User user = new User();
        user.setUserId(userId);
        user.setUsername("testuser");
        user.setPassword("encoded-password");
        user.setEmail("old@example.com");
        user.setPhone("0900000000");
        user.setDisplayName("Old Name");
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());

        UpdateUserRequest request = new UpdateUserRequest();
        request.setDisplayName("New Name");
        request.setEmail("new@example.com");
        request.setPhone("0911111111");

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        when(userRepository.saveAndFlush(user))
                .thenReturn(user);

        UserResponse result = userService.updateUser(userId, request);

        assertEquals("New Name", result.getDisplayName());
        assertEquals("new@example.com", result.getEmail());
        assertEquals("0911111111", result.getPhone());

        assertEquals("testuser", user.getUsername());
        assertEquals("encoded-password", user.getPassword());
        assertEquals("ACTIVE", user.getStatus());

        verify(userRepository).findByIdForUpdate(userId);
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void updateUser_shouldThrowException_whenUserDoesNotExist() {

        UUID userId = UUID.randomUUID();

        UpdateUserRequest request = new UpdateUserRequest();
        request.setDisplayName("New Name");
        request.setEmail("new@example.com");
        request.setPhone("0911111111");

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.updateUser(userId, request)
        );

        verify(userRepository).findByIdForUpdate(userId);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }
    @Test
    void updateUser_shouldThrowEmailAlreadyExists_whenEmailBelongsToAnotherUser() {

        UUID userId = UUID.randomUUID();

        User user = new User();
        user.setUserId(userId);
        user.setUsername("testuser");
        user.setEmail("old@example.com");
        user.setDisplayName("Old Name");
        user.setPhone("0123456789");
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());

        UpdateUserRequest request = new UpdateUserRequest();
        request.setDisplayName("New Name");
        request.setEmail("existing@example.com");
        request.setPhone("0911111111");

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        when(userRepository.existsByEmailAndUserIdNot(
                "existing@example.com",
                userId
        )).thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.updateUser(userId, request)
        );

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }
    @Test
    void updateStatus_shouldLockUserAndReturnRole() {
        UUID userId = UUID.randomUUID();
        var user = new User(); user.setUserId(userId); user.setStatus("ACTIVE");
        var role = new com.example.backend.auth.entity.Role(); role.setRoleId(UUID.randomUUID()); role.setRoleName("STAFF");
        var request = new com.example.backend.auth.dto.UpdateUserStatusRequest();
        request.setStatus(com.example.backend.auth.entity.UserStatus.LOCKED);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(userRoleRepository.findByUser_UserId(userId)).thenReturn(Optional.of(new com.example.backend.auth.entity.UserRole(user, role)));
        var result = userService.updateUserStatus(userId, request);
        assertEquals("LOCKED", result.getStatus());
        assertEquals(role.getRoleId(), result.getRoleId());
        assertEquals("STAFF", result.getRoleName());
        assertTrue(user.getUpdatedAt() != null);
    }
    @Test
    void getUsers_shouldUseSpecificationAndBatchLoadRolesForFilters() {
        UUID roleId = UUID.randomUUID();
        var user = new User(); user.setUserId(UUID.randomUUID());
        var role = new com.example.backend.auth.entity.Role(); role.setRoleId(roleId); role.setRoleName("MANAGER");
        var page = new PageImpl<User>(List.of(user), PageRequest.of(0, 20), 1);
        when(userRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        when(userRoleRepository.findByUser_UserIdIn(List.of(user.getUserId()))).thenReturn(List.of(new com.example.backend.auth.entity.UserRole(user, role)));
        var result = userService.getUsers(0, 20, "  admin  ", com.example.backend.auth.entity.UserStatus.ACTIVE, roleId);
        assertEquals(roleId, result.getContent().get(0).getRoleId());
        verify(userRoleRepository, never()).findByUser_UserId(any());
    }
    @Test
    void updateRequest_shouldTrimFields() {
        var request = new UpdateUserRequest();
        request.setEmail("  a@example.com  "); request.setDisplayName("  Alice  "); request.setPhone("  0901234567  ");
        assertEquals("a@example.com", request.getEmail()); assertEquals("Alice", request.getDisplayName()); assertEquals("0901234567", request.getPhone());
    }
}