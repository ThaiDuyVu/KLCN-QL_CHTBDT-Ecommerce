package com.example.backend.inventory.serial.service;

import java.util.List;
import java.util.UUID;

/** Transactional building block for the future Order flow. */
public interface SerialAllocationService {
    List<UUID> reserveAvailable(UUID variantId, UUID warehouseId, int quantity);
    void releaseReserved(List<UUID> serialIds);
    void markSold(List<UUID> serialIds);
}
