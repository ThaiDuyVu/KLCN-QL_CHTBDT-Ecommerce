package com.example.backend.auth.service;

import com.example.backend.auth.dto.CreateEmployeeRequest;
import com.example.backend.auth.dto.EmployeeResponse;
import com.example.backend.auth.dto.UpdateEmployeeRequest;
import com.example.backend.auth.entity.Employee;
import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.exception.InvalidUserManagementRequestException;
import com.example.backend.auth.exception.ProtectedPermissionException;
import com.example.backend.auth.exception.RoleNotFoundException;
import com.example.backend.auth.exception.UserNotFoundException;
import com.example.backend.auth.repository.EmployeeRepository;
import com.example.backend.auth.repository.RoleRepository;
import com.example.backend.auth.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {
    /** Roles that only ADMIN may hand out; a MANAGER (EMPLOYEE_CREATE) creates job-title staff only. */
    private static final Set<String> MANAGEMENT_ROLES = Set.of("ADMIN", "MANAGER");
    private final EmployeeRepository employees;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final UserAccountFactory accounts;

    public EmployeeServiceImpl(EmployeeRepository employees, RoleRepository roles, UserRoleRepository userRoles,
                               UserAccountFactory accounts) {
        this.employees = employees;
        this.roles = roles;
        this.userRoles = userRoles;
        this.accounts = accounts;
    }

    @Override
    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        Role role = roles.findById(request.getRoleId())
                .orElseThrow(() -> new RoleNotFoundException("Không tìm thấy chức danh: " + request.getRoleId()));
        if ("CUSTOMER".equals(role.getRoleName())) {
            throw new InvalidUserManagementRequestException("Tài khoản khách hàng được tạo ở trang Khách hàng, không phải trang nhân viên");
        }
        if (MANAGEMENT_ROLES.contains(role.getRoleName()) && !ManagementAuthorization.has("ADMIN")) {
            throw new ProtectedPermissionException("Chỉ ADMIN được tạo tài khoản " + role.getRoleName());
        }
        String code = request.getEmployeeCode() == null ? nextCode() : request.getEmployeeCode();
        if (employees.existsByEmployeeCodeIgnoreCase(code)) {
            throw new InvalidUserManagementRequestException("Mã nhân viên đã tồn tại: " + code);
        }
        User user = accounts.create(request.getUsername(), request.getPassword(), request.getEmail(),
                request.getFullName(), request.getPhone(), role);
        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode(code);
        employee.setFullName(request.getFullName());
        employee.setPosition(request.getPosition() == null ? role.getDescription() : request.getPosition());
        return response(employees.saveAndFlush(employee), role);
    }

    @Override
    public EmployeeResponse byUser(UUID userId) {
        Employee employee = employees.findByUser_UserId(userId)
                .orElseThrow(() -> new UserNotFoundException("Tài khoản này không có hồ sơ nhân viên"));
        return response(employee, role(employee));
    }

    @Override
    @Transactional
    public EmployeeResponse update(UUID employeeId, UpdateEmployeeRequest request) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new UserNotFoundException("Không tìm thấy nhân viên: " + employeeId));
        employee.setFullName(request.getFullName());
        employee.setPosition(request.getPosition());
        employee.getUser().setDisplayName(request.getFullName());
        employee.getUser().setUpdatedAt(java.time.OffsetDateTime.now());
        return response(employees.saveAndFlush(employee), role(employee));
    }

    /** NV0001, NV0002…: one above the highest generated code; a race is caught by uq_employees_employee_code. */
    private String nextCode() {
        int max = 0;
        for (String code : employees.findGeneratedCodes()) {
            String digits = code.substring(2);
            if (digits.matches("\\d{1,9}")) max = Math.max(max, Integer.parseInt(digits));
        }
        return String.format("NV%04d", max + 1);
    }

    private Role role(Employee employee) {
        return userRoles.findByUser_UserId(employee.getUser().getUserId()).map(link -> link.getRole()).orElse(null);
    }

    private EmployeeResponse response(Employee employee, Role role) {
        User user = employee.getUser();
        return new EmployeeResponse(employee.getEmployeeId(), user.getUserId(), employee.getEmployeeCode(),
                employee.getFullName(), employee.getPosition(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getStatus(), role == null ? null : role.getRoleId(), role == null ? null : role.getRoleName());
    }
}
