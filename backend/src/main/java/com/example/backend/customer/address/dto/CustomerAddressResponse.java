package com.example.backend.customer.address.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerAddressResponse(UUID addressId, UUID customerId, String label,
        String recipientName, String recipientPhone, String addressLine, String ward,
        String district, String province, String fullAddress, boolean isDefault,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
