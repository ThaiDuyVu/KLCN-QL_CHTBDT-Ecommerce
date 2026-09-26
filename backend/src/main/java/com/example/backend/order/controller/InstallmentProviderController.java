package com.example.backend.order.controller;

import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.order.dto.*;
import com.example.backend.order.service.InstallmentService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/installment-providers")
public class InstallmentProviderController {
    private final InstallmentService service;

    public InstallmentProviderController(InstallmentService service) { this.service = service; }

    @GetMapping @RequireAnyAuthority({"CUSTOMER", "ADMIN", "MANAGER"})
    public List<InstallmentProviderResponse> active() { return service.providers(true); }

    @GetMapping("/manage") @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public List<InstallmentProviderResponse> all() { return service.providers(false); }

    @PostMapping @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<InstallmentProviderResponse> create(@Valid @RequestBody InstallmentProviderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saveProvider(null, request));
    }

    @PutMapping("/{id}") @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public InstallmentProviderResponse update(@PathVariable UUID id, @Valid @RequestBody InstallmentProviderRequest request) {
        return service.saveProvider(id, request);
    }
}
