package com.example.backend.auth.service;

import com.example.backend.auth.dto.CreateEmployeeRequest;
import com.example.backend.auth.dto.EmployeeResponse;
import com.example.backend.auth.dto.UpdateEmployeeRequest;

import java.util.UUID;

public interface EmployeeService {
    EmployeeResponse create(CreateEmployeeRequest request);
    EmployeeResponse byUser(UUID userId);
    EmployeeResponse update(UUID employeeId, UpdateEmployeeRequest request);
}
