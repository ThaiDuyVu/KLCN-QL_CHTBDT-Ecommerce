package com.example.backend.warranty.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWarrantyTicketRequest(
        @NotBlank(message = "Mô tả lỗi là bắt buộc") String issueDescription
) {}
