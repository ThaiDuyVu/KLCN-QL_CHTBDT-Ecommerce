package com.example.backend.returns.controller;

import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.returns.dto.*;
import com.example.backend.returns.entity.ReturnStatus;
import com.example.backend.returns.service.ReturnService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/returns")
public class ReturnController {
    private final ReturnService service;

    public ReturnController(ReturnService service) { this.service = service; }

    @PostMapping("/mine") @RequireAnyAuthority({"CUSTOMER"})
    public ResponseEntity<ReturnResponse> create(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @Valid @RequestBody CreateReturnRequest request) {
        ReturnResponse response = service.create(user.getUserId(), request);
        return ResponseEntity.created(URI.create("/api/returns/mine/" + response.returnId())).body(response);
    }

    @GetMapping("/mine") @RequireAnyAuthority({"CUSTOMER"})
    public ReturnPageResponse mine(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.mine(user.getUserId(), page, size);
    }

    @GetMapping("/mine/{id}") @RequireAnyAuthority({"CUSTOMER"})
    public ReturnResponse myReturn(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID id) {
        return service.myReturn(user.getUserId(), id);
    }

    @GetMapping("/mine/by-order/{orderId}") @RequireAnyAuthority({"CUSTOMER"})
    public List<ReturnResponse> forOrder(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID orderId) {
        return service.forOrder(user.getUserId(), orderId);
    }

    @GetMapping @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF", "SALES_STAFF", "WAREHOUSE_KEEPER"})
    public ReturnPageResponse list(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @RequestParam(required = false) ReturnStatus status,
                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size, authorities(user));
    }

    @GetMapping("/{id}") @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF", "SALES_STAFF", "WAREHOUSE_KEEPER"})
    public ReturnResponse detail(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID id) {
        return service.detail(id, authorities(user));
    }

    @PatchMapping("/{id}/status") @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF", "SALES_STAFF", "WAREHOUSE_KEEPER"})
    public ReturnResponse status(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID id,
                                 @Valid @RequestBody UpdateReturnStatusRequest request) {
        return service.updateStatus(user.getUserId(), id, request, authorities(user));
    }

    private static List<String> authorities(AuthenticatedUserPrincipal user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
