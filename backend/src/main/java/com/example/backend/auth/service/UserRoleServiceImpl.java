package com.example.backend.auth.service;

import com.example.backend.auth.dto.UpdateUserRoleRequest;
import com.example.backend.auth.dto.UserRoleResponse;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.entity.UserRole;
import com.example.backend.auth.exception.RoleNotFoundException;
import com.example.backend.auth.exception.UserNotFoundException;
import com.example.backend.auth.exception.UserRoleNotFoundException;
import com.example.backend.auth.repository.RoleRepository;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.auth.repository.UserRoleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public UserRoleServiceImpl(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public UserRoleResponse getUserRole(UUID userId) {

        userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "Không tìm thấy người dùng với ID: " + userId
                        )
                );

        UserRole userRole = userRoleRepository
                .findByUser_UserId(userId)
                .orElseThrow(() ->
                        new UserRoleNotFoundException(
                                "Người dùng chưa được gán role"
                        )
                );

        var role = userRole.getRole();

        return new UserRoleResponse(
                userId,
                role.getRoleId(),
                role.getRoleName(),
                role.getDescription()
        );
    }
    @Override
    @Transactional
    public void updateUserRole(
            UUID userId,
            UpdateUserRoleRequest request
    ) {

        ManagementAuthorization.require("USER_ROLE_ASSIGN");
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "Không tìm thấy người dùng với ID: " + userId
                        )
                );

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() ->
                        new RoleNotFoundException(
                                "Không tìm thấy role với ID: "
                                        + request.getRoleId()
                        )
                );

        if ("ADMIN".equals(role.getRoleName()) && !ManagementAuthorization.has("ADMIN"))
            throw new com.example.backend.auth.exception.ProtectedPermissionException("Chỉ ADMIN được gán role ADMIN");
        UserRole userRole = userRoleRepository.findByUser_UserId(userId).orElse(null);

        if (userRole == null) {
            UserRole newUserRole = new UserRole(user, role);
            userRoleRepository.saveAndFlush(newUserRole);
            return;
        }

        if (userRole.getRole().getRoleId().equals(role.getRoleId())) return;
        ManagementAuthorization.require("USER_ROLE_REMOVE");
        if ("ADMIN".equals(userRole.getRole().getRoleName()) && !ManagementAuthorization.has("ADMIN"))
            throw new com.example.backend.auth.exception.ProtectedPermissionException("Chỉ ADMIN được đổi role của ADMIN");
        userRoleRepository.delete(userRole);
        userRoleRepository.flush();
        userRoleRepository.saveAndFlush(new UserRole(user, role));
    }
}
