package com.example.backend.customer.dto;

import jakarta.validation.constraints.*;

public class UpdateCustomerRequest {
    @NotBlank(message = "Vui lòng nhập họ và tên")
    @Size(max = 255, message = "Họ và tên tối đa 255 ký tự")
    private String fullName;
    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "^(?:0[0-9]{9}|\\+84[0-9]{9})$", message = "Số điện thoại không hợp lệ")
    private String phone;
    /** Blank keeps the current email (self-registered customers may have none). */
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;
    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String address;

    public String getFullName() { return fullName; }
    public void setFullName(String value) { this.fullName = value == null ? null : value.trim(); }
    public String getPhone() { return phone; }
    public void setPhone(String value) { this.phone = value == null ? null : value.trim(); }
    public String getEmail() { return email; }
    public void setEmail(String value) { this.email = value == null || value.isBlank() ? null : value.trim(); }
    public String getAddress() { return address; }
    public void setAddress(String value) { this.address = value == null || value.isBlank() ? null : value.trim(); }
}
