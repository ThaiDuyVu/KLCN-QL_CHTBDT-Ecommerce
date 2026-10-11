package com.example.backend.customer.address.controller;

import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.customer.address.dto.CustomerAddressRequest;
import com.example.backend.customer.address.dto.CustomerAddressResponse;
import com.example.backend.customer.address.service.CustomerAddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers/me/addresses")
public class CustomerAddressController {
    private final CustomerAddressService service;
    public CustomerAddressController(CustomerAddressService service) { this.service = service; }
    @GetMapping @RequireAnyAuthority({"CUSTOMER"}) public List<CustomerAddressResponse> list(@AuthenticationPrincipal AuthenticatedUserPrincipal user) {
        return service.list(user.getUserId());
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @RequireAnyAuthority({"CUSTOMER"})
    public CustomerAddressResponse create(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @Valid @RequestBody CustomerAddressRequest request) { return service.create(user.getUserId(), request); }
    @PutMapping("/{addressId}") @RequireAnyAuthority({"CUSTOMER"})
    public CustomerAddressResponse update(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID addressId, @Valid @RequestBody CustomerAddressRequest request) {
        return service.update(user.getUserId(), addressId, request);
    }
    @DeleteMapping("/{addressId}") @ResponseStatus(HttpStatus.NO_CONTENT) @RequireAnyAuthority({"CUSTOMER"})
    public void delete(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID addressId) {
        service.delete(user.getUserId(), addressId);
    }
    @PatchMapping("/{addressId}/default") @RequireAnyAuthority({"CUSTOMER"})
    public CustomerAddressResponse makeDefault(@AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @PathVariable UUID addressId) { return service.makeDefault(user.getUserId(), addressId); }
}
