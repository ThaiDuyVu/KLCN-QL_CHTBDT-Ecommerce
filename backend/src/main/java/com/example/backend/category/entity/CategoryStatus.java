package com.example.backend.category.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CategoryStatus {
    ACTIVE,
    INACTIVE;

    @JsonCreator
    public static CategoryStatus fromValue(String value) {
        // Preserve the optional status contract for an omitted or empty value.
        return value == null || value.isEmpty() ? null : valueOf(value.trim());
    }
}
