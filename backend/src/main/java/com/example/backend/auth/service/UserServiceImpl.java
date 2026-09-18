package com.example.backend.auth.service;

import com.example.backend.auth.dto.*;
import com.example.backend.auth.entity.*;
import com.example.backend.auth.exception.*;
import com.example.backend.auth.repository.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    public UserServiceImpl(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }
    public UserResponse getUserById(UUID userId) { return response(find(userId)); }
    public UserPageResponse getUsers(int page, int size) { return getUsers(page, size, null, null, null); }
    public UserPageResponse getUsers(int page, int size, String keyword, UserStatus status, UUID roleId) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new InvalidUserManagementRequestException("Page phải >= 0, size từ 1–100 và offset trong giới hạn hỗ trợ");
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("userId")));
        var users = keyword == null && status == null && roleId == null ? userRepository.findAll(pageable)
                : userRepository.findAll(UserSpecifications.filter(keyword, status, roleId), pageable);
        List<User> content = users.getContent();
        Map<UUID, Role> roles = content.isEmpty() ? Map.of() : userRoleRepository.findByUser_UserIdIn(
                content.stream().map(User::getUserId).toList()).stream().collect(Collectors.toMap(
                        link -> link.getUser().getUserId(), UserRole::getRole));
        return new UserPageResponse(content.stream().map(u -> map(u, roles.get(u.getUserId()))).toList(),
                users.getNumber(), users.getSize(), users.getTotalElements(), users.getTotalPages());
    }
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new UserNotFoundException("Không tìm thấy người dùng: " + userId));
        if (userRepository.existsByEmailAndUserIdNot(request.getEmail(), userId))
            throw new EmailAlreadyExistsException("Email đã được sử dụng bởi người dùng khác: " + request.getEmail());
        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setUpdatedAt(OffsetDateTime.now());
        try {
            return response(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause())
                if (cause instanceof ConstraintViolationException c && "uq_users_email".equals(c.getConstraintName()))
                    throw new EmailAlreadyExistsException("Email đã được sử dụng bởi người dùng khác: " + request.getEmail());
            throw e;
        }
    }
    @Transactional
    public UserResponse updateUserStatus(UUID userId, UpdateUserStatusRequest request) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new UserNotFoundException("Không tìm thấy người dùng: " + userId));
        user.setStatus(request.getStatus().name());
        user.setUpdatedAt(OffsetDateTime.now());
        return response(userRepository.saveAndFlush(user));
    }
    private User find(UUID id) { return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("Không tìm thấy người dùng: " + id)); }
    private UserResponse response(User user) {
        return map(user, userRoleRepository.findByUser_UserId(user.getUserId()).map(UserRole::getRole).orElse(null));
    }
    private UserResponse map(User user, Role role) {
        var response = new UserResponse(user.getUserId(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getDisplayName(), user.getStatus(), user.getCreatedAt());
        if (role != null) response.setRole(role.getRoleId(), role.getRoleName());
        return response;
    }
}
