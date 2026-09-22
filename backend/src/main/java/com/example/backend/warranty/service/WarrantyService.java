package com.example.backend.warranty.service;

import com.example.backend.warranty.dto.*;
import com.example.backend.warranty.entity.WarrantyTicketStatus;
import java.util.UUID;

public interface WarrantyService {
    WarrantyPageResponse myWarranties(UUID userId, int page, int size);
    WarrantyResponse myWarranty(UUID userId, UUID warrantyId);
    WarrantyResponse myLookup(UUID userId, String code);
    WarrantyPageResponse warranties(int page, int size);
    WarrantyResponse warranty(UUID warrantyId);
    WarrantyResponse lookup(String code);
    WarrantyTicketResponse createTicket(UUID userId, UUID warrantyId, CreateWarrantyTicketRequest request);
    WarrantyTicketPageResponse myTickets(UUID userId, int page, int size);
    WarrantyTicketResponse myTicket(UUID userId, UUID ticketId);
    WarrantyTicketPageResponse tickets(String keyword, WarrantyTicketStatus status, int page, int size);
    WarrantyTicketResponse ticket(UUID ticketId);
    WarrantyTicketResponse updateTicket(UUID userId, UUID ticketId, UpdateWarrantyTicketRequest request);
}
