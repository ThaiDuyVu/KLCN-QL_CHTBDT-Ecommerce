package com.example.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Vui lòng nhập tên đăng nhập")
    @Pattern(
            regexp = "^\\s*[A-Za-z0-9._-]{4,50}\\s*$",
            message = "Tên đăng nhập gồm 4–50 ký tự: chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang"
    )
    private String username;

    @NotBlank(message = "Vui lòng nhập họ và tên")
    @Size(max = 255, message = "Họ và tên tối đa 255 ký tự")
    private String fullName;

    // At least 8 characters with at least one letter and one digit.
    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Pattern(
            regexp = "^(?=.*\\p{L})(?=.*\\d).{8,}$",
            message = "Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số"
    )
    private String password;

    public RegisterRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
