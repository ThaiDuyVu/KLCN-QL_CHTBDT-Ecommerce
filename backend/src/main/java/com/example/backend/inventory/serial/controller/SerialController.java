package com.example.backend.inventory.serial.controller;

import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.inventory.serial.dto.*;
import com.example.backend.inventory.serial.entity.SerialStatus;
import com.example.backend.inventory.serial.service.SerialQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/serials")
public class SerialController {
    private final SerialQueryService service;
    public SerialController(SerialQueryService service) { this.service = service; }

    @GetMapping
    @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public ResponseEntity<SerialPageResponse> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID variantId, @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) SerialStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.search(keyword, variantId, warehouseId, status, page, size));
    }

    @GetMapping("/lookup")
    @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public ResponseEntity<SerialResponse> lookup(@RequestParam String code) { return ResponseEntity.ok(service.lookup(code)); }

    @GetMapping("/{serialId}")
    @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF"})
    public ResponseEntity<SerialResponse> detail(@PathVariable UUID serialId) { return ResponseEntity.ok(service.getById(serialId)); }
}
