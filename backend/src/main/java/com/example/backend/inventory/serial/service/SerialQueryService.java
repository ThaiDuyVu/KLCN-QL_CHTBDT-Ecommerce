package com.example.backend.inventory.serial.service;

import com.example.backend.inventory.serial.dto.SerialPageResponse;
import com.example.backend.inventory.serial.dto.SerialResponse;
import com.example.backend.inventory.serial.entity.SerialStatus;
import java.util.UUID;

public interface SerialQueryService {
    SerialPageResponse search(String keyword, UUID variantId, UUID warehouseId, SerialStatus status, int page, int size);
    SerialResponse getById(UUID serialId);
    SerialResponse lookup(String code);
}
