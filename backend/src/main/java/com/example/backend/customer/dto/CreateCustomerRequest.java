package com.example.backend.customer.dto;

import jakarta.validation.constraints.*;

/** A customer registered by staff at the store counter. The phone number identifies the customer. */
public class CreateCustomerRequest {
    @NotBlank(message = "Vui lòng nhập họ và tên")
    @Size(max = 255, message = "Họ và tên tối đa 255 ký tự")
    private String fullName;
    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "^(?:0[0-9]{9}|\\+84[0-9]{9})$", message = "Số điện thoại không hợp lệ")
    private String phone;
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;
    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String address;
    /** Optional; defaults to the phone number so the customer can sign in with it. */
    @Pattern(regexp = "^[A-Za-z0-9._-]{4,50}$",
            message = "Tên đăng nhập gồm 4–50 ký tự: chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang")
    private String username;
    @NotBlank(message = "Vui lòng nhập mật khẩu ban đầu")
    @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).{8,}$", message = "Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số")
    private String password;

    public String getFullName() { return fullName; }
    public void setFullName(String value) { this.fullName = value == null ? null : value.trim(); }
    public String getPhone() { return phone; }
    public void setPhone(String value) { this.phone = value == null ? null : value.trim(); }
    public String getEmail() { return email; }
    public void setEmail(String value) { this.email = value == null || value.isBlank() ? null : value.trim(); }
    public String getAddress() { return address; }
    public void setAddress(String value) { this.address = value == null || value.isBlank() ? null : value.trim(); }
    public String getUsername() { return username; }
    public void setUsername(String value) { this.username = value == null || value.isBlank() ? null : value.trim(); }
    public String getPassword() { return password; }
    public void setPassword(String value) { this.password = value; }
}
