package com.example.backend.warranty.dto;

import com.example.backend.warranty.entity.WarrantyTicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateWarrantyTicketRequest(
        @NotNull(message = "Trạng thái ticket là bắt buộc") WarrantyTicketStatus status,
        String resolutionNote
) {}
