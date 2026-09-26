package com.example.backend.order.dto;

import com.example.backend.order.entity.InstallmentProviderStatus;
import jakarta.validation.constraints.*;

public record InstallmentProviderRequest(
        @NotBlank @Size(max = 255) String providerName,
        @NotBlank @Size(max = 100) String providerCode,
        @Size(max = 30) String contactPhone,
        @Email @Size(max = 255) String contactEmail,
        @NotNull InstallmentProviderStatus status
) {}
