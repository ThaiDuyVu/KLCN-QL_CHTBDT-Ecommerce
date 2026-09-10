package com.example.backend.auth.repository;

import com.example.backend.auth.entity.UserRole;
import com.example.backend.auth.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository
        extends JpaRepository<UserRole, UserRoleId> {

    Optional<UserRole> findByUser_UserId(UUID userId);

}