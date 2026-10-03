package com.example.backend.customer.controller;

import com.example.backend.auth.entity.UserStatus;
import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.customer.dto.*;
import com.example.backend.customer.service.CustomerManagementService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerManagementController {
    private final CustomerManagementService service;
    public CustomerManagementController(CustomerManagementService service) { this.service = service; }

    @GetMapping
    @RequireAnyAuthority({"ADMIN", "CUSTOMER_VIEW"})
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'STAFF')")
    public CustomerPageResponse<CustomerSummaryResponse> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(keyword, status, page, size);
    }

    @GetMapping("/{customerId}")
    @RequireAnyAuthority({"ADMIN", "CUSTOMER_VIEW"})
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'STAFF')")
    public CustomerSummaryResponse detail(@PathVariable UUID customerId) { return service.detail(customerId); }

    @GetMapping("/{customerId}/orders")
    @RequireAnyAuthority({"ADMIN", "CUSTOMER_VIEW"})
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'STAFF')")
    public CustomerPageResponse<CustomerOrderResponse> orders(@PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.orders(customerId, page, size);
    }

    @GetMapping("/{customerId}/warranties")
    @RequireAnyAuthority({"ADMIN", "CUSTOMER_VIEW"})
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MANAGER', 'STAFF')")
    public CustomerPageResponse<CustomerWarrantyResponse> warranties(@PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.warranties(customerId, page, size);
    }

    @GetMapping("/{customerId}/installments")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public CustomerPageResponse<CustomerInstallmentResponse> installments(@PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.installments(customerId, page, size);
    }
}
