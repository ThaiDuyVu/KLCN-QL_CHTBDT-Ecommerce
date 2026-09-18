package com.example.backend.product.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ProductStatus {
    ACTIVE,
    INACTIVE;

    @JsonCreator
    public static ProductStatus fromValue(String value) {
        // Preserve the optional status contract for an omitted or empty value.
        return value == null || value.isEmpty() ? null : valueOf(value.trim());
    }
}
