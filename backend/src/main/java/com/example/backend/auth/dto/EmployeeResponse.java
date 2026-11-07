package com.example.backend.auth.dto;

import java.util.UUID;

public class EmployeeResponse {
    private final UUID employeeId;
    private final UUID userId;
    private final String employeeCode;
    private final String fullName;
    private final String position;
    private final String username;
    private final String email;
    private final String phone;
    private final String status;
    private final UUID roleId;
    private final String roleName;
    private final UUID warehouseId;
    private final String warehouseName;

    public EmployeeResponse(UUID employeeId, UUID userId, String employeeCode, String fullName, String position,
                            String username, String email, String phone, String status, UUID roleId, String roleName,
                            UUID warehouseId, String warehouseName) {
        this.warehouseId = warehouseId; this.warehouseName = warehouseName;
        this.employeeId = employeeId; this.userId = userId; this.employeeCode = employeeCode; this.fullName = fullName;
        this.position = position; this.username = username; this.email = email; this.phone = phone; this.status = status;
        this.roleId = roleId; this.roleName = roleName;
    }

    public UUID getEmployeeId() { return employeeId; }
    public UUID getUserId() { return userId; }
    public String getEmployeeCode() { return employeeCode; }
    public String getFullName() { return fullName; }
    public String getPosition() { return position; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getStatus() { return status; }
    public UUID getRoleId() { return roleId; }
    public String getRoleName() { return roleName; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getWarehouseName() { return warehouseName; }
}
