package com.example.backend.order.payment.controller;

import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.order.payment.dto.VnpayPaymentUrlResponse;
import com.example.backend.order.payment.service.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments/vnpay")
public class VnpayController {
    private final VnpayService service;
    public VnpayController(VnpayService service) { this.service = service; }
    @GetMapping("/config") @RequireAnyAuthority({"CUSTOMER"})
    public Map<String, Boolean> config() { return Map.of("enabled", service.enabled()); }
    @PostMapping("/orders/{orderId}/sync") @RequireAnyAuthority({"CUSTOMER"})
    public org.springframework.http.ResponseEntity<Void> synchronize(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
                                                                    @PathVariable UUID orderId) {
        service.synchronize(user.getUserId(), orderId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }
    @PostMapping("/orders/{orderId}/url") @RequireAnyAuthority({"CUSTOMER"})
    public VnpayPaymentUrlResponse paymentUrl(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
                                            @PathVariable UUID orderId, HttpServletRequest request) {
        return service.resume(user.getUserId(), orderId, request.getRemoteAddr());
    }
}
