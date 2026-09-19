package com.example.backend.cart.controller;
import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.cart.dto.*;
import com.example.backend.cart.service.CartService;
import com.example.backend.common.security.RequireAnyAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.UUID;
@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService service;
    public CartController(CartService service) { this.service = service; }
    @GetMapping @RequireAnyAuthority({"CUSTOMER"})
    public CartResponse get(@AuthenticationPrincipal AuthenticatedUserPrincipal user) { return service.get(user.getUserId()); }
    @PostMapping("/items") @RequireAnyAuthority({"CUSTOMER"})
    public CartResponse add(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @Valid @RequestBody CartItemRequest request) { return service.add(user.getUserId(), request); }
    @PatchMapping("/items/{itemId}") @RequireAnyAuthority({"CUSTOMER"})
    public CartResponse quantity(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID itemId, @Valid @RequestBody UpdateCartQuantityRequest request) { return service.quantity(user.getUserId(), itemId, request); }
    @DeleteMapping("/items/{itemId}") @RequireAnyAuthority({"CUSTOMER"})
    public CartResponse remove(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID itemId) { return service.remove(user.getUserId(), itemId); }
}
