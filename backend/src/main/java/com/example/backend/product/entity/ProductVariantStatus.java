package com.example.backend.product.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ProductVariantStatus {
    ACTIVE,
    INACTIVE;

    @JsonCreator
    public static ProductVariantStatus fromValue(String value) {
        // Preserve the optional status contract for an omitted or empty value.
        return value == null || value.isEmpty() ? null : valueOf(value.trim());
    }
}
