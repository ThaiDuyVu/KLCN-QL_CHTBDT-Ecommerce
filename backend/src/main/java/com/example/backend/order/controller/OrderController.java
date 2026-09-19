package com.example.backend.order.controller;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.order.dto.*;
import com.example.backend.order.entity.OrderStatus;
import com.example.backend.order.service.OrderService;
import com.example.backend.common.security.RequireAnyAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }
    @PostMapping("/checkout") @RequireAnyAuthority({"CUSTOMER"})
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @Valid @RequestBody CheckoutRequest request) {
        var response = service.checkout(user.getUserId(), request);
        return ResponseEntity.created(URI.create("/api/orders/mine/" + response.getOrderId())).body(response);
    }
    @GetMapping("/mine") @RequireAnyAuthority({"CUSTOMER"})
    public OrderPageResponse mine(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return service.list(user.getUserId(), true, page, size); }
    @GetMapping("/mine/{id}") @RequireAnyAuthority({"CUSTOMER"})
    public OrderResponse mineDetail(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID id) { return service.detail(user.getUserId(), true, id); }
    @PostMapping("/mine/{id}/cancel") @RequireAnyAuthority({"CUSTOMER"})
    public OrderResponse cancel(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID id) { return service.status(user.getUserId(), true, id, OrderStatus.CANCELLED); }
    @GetMapping @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public OrderPageResponse list(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return service.list(user.getUserId(), false, page, size); }
    @GetMapping("/{id}") @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public OrderResponse detail(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID id) { return service.detail(user.getUserId(), false, id); }
    @PatchMapping("/{id}/status") @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public OrderResponse status(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) { return service.status(user.getUserId(), false, id, request.getStatus()); }
}
