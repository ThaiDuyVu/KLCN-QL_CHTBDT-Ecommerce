package com.example.backend.auth.repository;

import com.example.backend.auth.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByUser_UserId(UUID userId);

    java.util.List<Employee> findByUser_UserIdIn(java.util.Collection<UUID> userIds);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    @org.springframework.data.jpa.repository.Query("select e from Employee e join fetch e.user u, UserRole ur"
            + " where ur.user = u and ur.role.roleName = :role and u.status = 'ACTIVE' order by e.fullName, e.employeeId")
    java.util.List<Employee> findActiveByRoleName(@org.springframework.data.repository.query.Param("role") String roleName);

    @org.springframework.data.jpa.repository.Query("select count(ur) > 0 from UserRole ur where ur.user.userId = :userId and ur.role.roleName = :role")
    boolean hasRole(@org.springframework.data.repository.query.Param("userId") UUID userId, @org.springframework.data.repository.query.Param("role") String roleName);

    @org.springframework.data.jpa.repository.Query("select e.employeeCode from Employee e where e.employeeCode like 'NV%'")
    java.util.List<String> findGeneratedCodes();
}
