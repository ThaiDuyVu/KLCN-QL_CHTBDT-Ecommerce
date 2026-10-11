package com.example.backend.customer.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerAddressRequest(
        @Size(max = 100) String label,
        @NotBlank @Size(max = 255) String recipientName,
        @NotBlank @Pattern(regexp = "^(?:0[0-9]{9}|\\+84[0-9]{9})$",
                message = "Số điện thoại phải có dạng 0901234567 hoặc +84901234567") String recipientPhone,
        @NotBlank @Size(max = 500) String addressLine,
        @Size(max = 150) String ward,
        @Size(max = 150) String district,
        @Size(max = 150) String province
) {
    public CustomerAddressRequest {
        label = normalize(label);
        recipientName = normalize(recipientName);
        recipientPhone = normalize(recipientPhone);
        addressLine = normalize(addressLine);
        ward = normalize(ward);
        district = normalize(district);
        province = normalize(province);
    }
    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
