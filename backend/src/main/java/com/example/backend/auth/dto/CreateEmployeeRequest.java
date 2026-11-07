package com.example.backend.auth.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public class CreateEmployeeRequest {
    @NotBlank(message = "Vui lòng nhập tên đăng nhập")
    @Pattern(regexp = "^\\s*[A-Za-z0-9._-]{4,50}\\s*$",
            message = "Tên đăng nhập gồm 4–50 ký tự: chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang")
    private String username;
    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).{8,}$", message = "Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số")
    private String password;
    @NotBlank(message = "Vui lòng nhập họ và tên")
    @Size(max = 255, message = "Họ và tên tối đa 255 ký tự")
    private String fullName;
    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;
    @Pattern(regexp = "^(?:0[0-9]{9}|\\+84[0-9]{9})$", message = "Số điện thoại không hợp lệ")
    private String phone;
    @NotNull(message = "Vui lòng chọn chức danh")
    private UUID roleId;
    /** Optional; generated as NVxxxx when blank. */
    @Pattern(regexp = "^[A-Za-z0-9-]{2,50}$", message = "Mã nhân viên gồm 2–50 ký tự chữ, số hoặc gạch ngang")
    private String employeeCode;
    @Size(max = 100, message = "Vị trí tối đa 100 ký tự")
    private String position;
    /** Branch the employee works at; null = all branches (V12). */
    private UUID warehouseId;

    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID value) { this.warehouseId = value; }

    public String getUsername() { return username; }
    public void setUsername(String value) { this.username = value == null ? null : value.trim(); }
    public String getPassword() { return password; }
    public void setPassword(String value) { this.password = value; }
    public String getFullName() { return fullName; }
    public void setFullName(String value) { this.fullName = value == null ? null : value.trim(); }
    public String getEmail() { return email; }
    public void setEmail(String value) { this.email = value == null ? null : value.trim(); }
    public String getPhone() { return phone; }
    public void setPhone(String value) { this.phone = value == null || value.isBlank() ? null : value.trim(); }
    public UUID getRoleId() { return roleId; }
    public void setRoleId(UUID value) { this.roleId = value; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String value) { this.employeeCode = value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT); }
    public String getPosition() { return position; }
    public void setPosition(String value) { this.position = value == null || value.isBlank() ? null : value.trim(); }
}
