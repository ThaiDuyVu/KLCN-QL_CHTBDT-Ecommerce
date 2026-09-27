package com.example.backend.warranty.dto;

import com.example.backend.warranty.entity.WarrantyStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WarrantyResponse(
        UUID warrantyId, UUID serialId, UUID orderItemId, UUID customerId, String customerName,
        UUID orderId, String orderCode, UUID variantId, String productName, String sku,
        String serialNumber, List<String> imeiNumbers, LocalDate startDate, LocalDate endDate,
        WarrantyStatus status, boolean eligible, List<WarrantyTicketResponse> tickets
) {}
