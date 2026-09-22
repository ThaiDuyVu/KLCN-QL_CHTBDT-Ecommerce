package com.example.backend.auth.repository;

import com.example.backend.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Role r where r.roleId = :id")
    Optional<Role> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<Role> findByRoleName(String roleName);

    boolean existsByRoleName(String roleName);
}