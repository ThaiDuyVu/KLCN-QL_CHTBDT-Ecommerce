package com.example.backend.auth.controller;

import com.example.backend.auth.dto.CreateEmployeeRequest;
import com.example.backend.auth.dto.EmployeeResponse;
import com.example.backend.auth.dto.UpdateEmployeeRequest;
import com.example.backend.auth.service.EmployeeService;
import com.example.backend.common.security.RequireAnyAuthority;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    private final EmployeeService service;

    public EmployeeController(EmployeeService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireAnyAuthority({"ADMIN", "EMPLOYEE_CREATE"})
    public EmployeeResponse create(@Valid @RequestBody CreateEmployeeRequest request) { return service.create(request); }

    @GetMapping("/by-user/{userId}")
    @RequireAnyAuthority({"ADMIN", "EMPLOYEE_VIEW"})
    public EmployeeResponse byUser(@PathVariable UUID userId) { return service.byUser(userId); }

    @PutMapping("/{employeeId}")
    @RequireAnyAuthority({"ADMIN", "EMPLOYEE_UPDATE"})
    public EmployeeResponse update(@PathVariable UUID employeeId, @Valid @RequestBody UpdateEmployeeRequest request) {
        return service.update(employeeId, request);
    }
}
