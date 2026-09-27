package com.example.backend.order.payment.controller;

import com.example.backend.order.payment.dto.VnpayIpnResponse;
import com.example.backend.order.payment.service.VnpayService;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/payments/vnpay")
public class VnpayCallbackController {
    private final VnpayService service;
    public VnpayCallbackController(VnpayService service) { this.service = service; }
    @GetMapping("/ipn")
    public VnpayIpnResponse ipn(@RequestParam MultiValueMap<String, String> parameters) { return service.notifyPayment(parameters); }
    @GetMapping("/return")
    public ResponseEntity<Void> returned(@RequestParam MultiValueMap<String, String> parameters) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(service.returnRedirect(parameters))).build();
    }
}
