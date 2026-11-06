package com.example.backend.auth.dto;

import jakarta.validation.constraints.*;

/** Profile fields of an employee; email/phone/status/role keep their existing /api/users endpoints. */
public class UpdateEmployeeRequest {
    @NotBlank(message = "Vui lòng nhập họ và tên")
    @Size(max = 255, message = "Họ và tên tối đa 255 ký tự")
    private String fullName;
    @Size(max = 100, message = "Vị trí tối đa 100 ký tự")
    private String position;

    public String getFullName() { return fullName; }
    public void setFullName(String value) { this.fullName = value == null ? null : value.trim(); }
    public String getPosition() { return position; }
    public void setPosition(String value) { this.position = value == null || value.isBlank() ? null : value.trim(); }
}
