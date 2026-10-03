package com.example.backend.warranty.entity;

import java.time.LocalDate;

public enum WarrantyStatus {
    ACTIVE,
    EXPIRED;

    public WarrantyStatus effectiveOn(LocalDate endDate, LocalDate today) {
        return this == EXPIRED || today.isAfter(endDate) ? EXPIRED : ACTIVE;
    }
}
