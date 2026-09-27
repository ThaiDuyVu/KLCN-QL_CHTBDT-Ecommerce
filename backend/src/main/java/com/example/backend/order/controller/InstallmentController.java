package com.example.backend.order.controller;

import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.order.dto.*;
import com.example.backend.order.entity.InstallmentStatus;
import com.example.backend.order.service.InstallmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/installments")
public class InstallmentController {
    private final InstallmentService service;

    public InstallmentController(InstallmentService service) { this.service = service; }

    @GetMapping @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public InstallmentPageResponse list(@RequestParam(required = false) InstallmentStatus status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    @GetMapping("/{id}") @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public InstallmentResponse detail(@PathVariable UUID id) { return service.detail(id); }

    @PatchMapping("/{id}/status") @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public InstallmentResponse status(@PathVariable UUID id, @Valid @RequestBody UpdateInstallmentStatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
