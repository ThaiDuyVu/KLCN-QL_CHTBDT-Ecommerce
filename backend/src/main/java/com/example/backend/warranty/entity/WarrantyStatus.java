package com.example.backend.warranty.entity;

import java.time.LocalDate;

/** VOIDED: the device was returned and refunded, so its warranty no longer applies (V10). */
public enum WarrantyStatus {
    ACTIVE,
    EXPIRED,
    VOIDED;

    public WarrantyStatus effectiveOn(LocalDate endDate, LocalDate today) {
        if (this == VOIDED) return VOIDED;
        return this == EXPIRED || today.isAfter(endDate) ? EXPIRED : ACTIVE;
    }
}
