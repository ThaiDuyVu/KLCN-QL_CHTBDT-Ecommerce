package com.example.backend.auth.repository;

import com.example.backend.auth.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByUser_UserId(UUID userId);

    Optional<Employee> findByEmployeeCode(String employeeCode);
}
