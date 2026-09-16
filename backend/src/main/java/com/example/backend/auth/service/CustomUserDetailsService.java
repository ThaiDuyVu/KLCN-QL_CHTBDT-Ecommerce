package com.example.backend.auth.service;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.entity.UserRole;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.auth.repository.UserRoleRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public CustomUserDetailsService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public AuthenticatedUserPrincipal loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found: " + username
                        )
                );

        return createPrincipal(user);
    }

    public AuthenticatedUserPrincipal loadUserByUserId(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found")
                );

        return createPrincipal(user);
    }

    private AuthenticatedUserPrincipal createPrincipal(User user) {
        UserRole userRole = userRoleRepository
                .findByUser_UserId(user.getUserId())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User does not have an assigned role"
                        )
                );

        String roleName = userRole.getRole().getRoleName();

        if (roleName == null || roleName.isBlank()) {
            throw new UsernameNotFoundException(
                    "User does not have an assigned role"
            );
        }

        return new AuthenticatedUserPrincipal(user, roleName);
    }
}
