package com.example.backend.warranty.controller;

import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.warranty.dto.*;
import com.example.backend.warranty.entity.WarrantyTicketStatus;
import com.example.backend.warranty.service.WarrantyService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/warranties")
public class WarrantyController {
    private final WarrantyService service;
    public WarrantyController(WarrantyService service) { this.service = service; }

    @GetMapping("/mine")
    @RequireAnyAuthority({"CUSTOMER"})
    public WarrantyPageResponse mine(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.myWarranties(user.getUserId(), page, size);
    }

    @GetMapping("/mine/lookup")
    @RequireAnyAuthority({"CUSTOMER"})
    public WarrantyResponse myLookup(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @RequestParam String code) { return service.myLookup(user.getUserId(), code); }

    @GetMapping("/mine/tickets")
    @RequireAnyAuthority({"CUSTOMER"})
    public WarrantyTicketPageResponse myTickets(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.myTickets(user.getUserId(), page, size);
    }

    @GetMapping("/mine/tickets/{ticketId}")
    @RequireAnyAuthority({"CUSTOMER"})
    public WarrantyTicketResponse myTicket(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID ticketId) { return service.myTicket(user.getUserId(), ticketId); }

    @PostMapping("/mine/{warrantyId}/tickets")
    @RequireAnyAuthority({"CUSTOMER"})
    public ResponseEntity<WarrantyTicketResponse> createTicket(
            @AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID warrantyId,
            @Valid @RequestBody CreateWarrantyTicketRequest request) {
        WarrantyTicketResponse response = service.createTicket(user.getUserId(), warrantyId, request);
        return ResponseEntity.created(URI.create("/api/warranties/mine/tickets/" + response.ticketId())).body(response);
    }

    @GetMapping("/mine/{warrantyId}")
    @RequireAnyAuthority({"CUSTOMER"})
    public WarrantyResponse myWarranty(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID warrantyId) { return service.myWarranty(user.getUserId(), warrantyId); }

    @GetMapping
    @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public WarrantyPageResponse list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.warranties(page, size); }

    @GetMapping("/lookup")
    @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public WarrantyResponse lookup(@RequestParam String code) { return service.lookup(code); }

    @GetMapping("/tickets")
    @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public WarrantyTicketPageResponse tickets(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) WarrantyTicketStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.tickets(keyword, status, page, size);
    }

    @GetMapping("/tickets/{ticketId}")
    @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public WarrantyTicketResponse ticket(@PathVariable UUID ticketId) { return service.ticket(ticketId); }

    @PatchMapping("/tickets/{ticketId}")
    @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public WarrantyTicketResponse updateTicket(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID ticketId, @Valid @RequestBody UpdateWarrantyTicketRequest request) {
        return service.updateTicket(user.getUserId(), ticketId, request);
    }

    @GetMapping("/{warrantyId}")
    @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public WarrantyResponse warranty(@PathVariable UUID warrantyId) { return service.warranty(warrantyId); }
}
