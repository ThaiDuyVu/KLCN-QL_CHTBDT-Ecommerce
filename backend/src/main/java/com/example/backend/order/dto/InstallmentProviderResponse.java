package com.example.backend.order.dto;

import com.example.backend.order.entity.InstallmentProviderStatus;
import java.util.UUID;

public record InstallmentProviderResponse(
        UUID providerId, String providerName, String providerCode,
        String contactPhone, String contactEmail, InstallmentProviderStatus status
) {}
