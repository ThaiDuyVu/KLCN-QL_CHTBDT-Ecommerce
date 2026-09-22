package com.example.backend.warranty.dto;

import com.example.backend.warranty.entity.WarrantyTicketStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WarrantyTicketResponse(
        UUID ticketId, String ticketCode, UUID warrantyId, UUID serialId, UUID customerId,
        UUID employeeId, String employeeName, String issueDescription, String resolutionNote,
        OffsetDateTime createdAt, OffsetDateTime resolvedAt, WarrantyTicketStatus status,
        String serialNumber, List<String> imeiNumbers, String productName, String sku,
        String customerName, String orderCode, List<WarrantyTicketStatus> allowedStatuses
) {}
