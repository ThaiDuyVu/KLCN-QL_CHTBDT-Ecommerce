package com.example.backend.invoice.controller;

import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.invoice.dto.InvoicePageResponse;
import com.example.backend.invoice.dto.InvoiceResponse;
import com.example.backend.invoice.service.InvoiceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
    private final InvoiceService service;

    public InvoiceController(InvoiceService service) { this.service = service; }

    @GetMapping @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF", "SALES_STAFF"})
    public InvoicePageResponse list(@RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(keyword, page, size);
    }

    @GetMapping("/{id}") @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF", "SALES_STAFF"})
    public InvoiceResponse detail(@PathVariable UUID id) { return service.detail(id); }

    @GetMapping("/mine/by-order/{orderId}") @RequireAnyAuthority({"CUSTOMER"})
    public InvoiceResponse mine(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID orderId) {
        return service.mineForOrder(user.getUserId(), orderId);
    }
}
