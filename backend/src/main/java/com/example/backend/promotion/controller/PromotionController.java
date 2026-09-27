package com.example.backend.promotion.controller;
import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.promotion.dto.*;
import com.example.backend.promotion.entity.PromotionStatus;
import com.example.backend.promotion.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/promotions")
public class PromotionController {
    private final PromotionService service;
    public PromotionController(PromotionService service) { this.service=service; }
    @GetMapping @RequireAnyAuthority({"ADMIN","MANAGER"})
    public PromotionPageResponse list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
                                     @RequestParam(required=false) String keyword,@RequestParam(required=false) PromotionStatus status) {
        return service.list(page,size,keyword,status);
    }
    @GetMapping("/{id}") @RequireAnyAuthority({"ADMIN","MANAGER"})
    public PromotionResponse detail(@PathVariable UUID id) { return service.detail(id); }
    @PostMapping @RequireAnyAuthority({"ADMIN","MANAGER"})
    public ResponseEntity<PromotionResponse> create(@Valid @RequestBody PromotionRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request)); }
    @PutMapping("/{id}") @RequireAnyAuthority({"ADMIN","MANAGER"})
    public PromotionResponse update(@PathVariable UUID id,@Valid @RequestBody PromotionRequest request) { return service.update(id,request); }
    @PatchMapping("/{id}/status") @RequireAnyAuthority({"ADMIN","MANAGER"})
    public PromotionResponse status(@PathVariable UUID id,@Valid @RequestBody PromotionStatusRequest request) { return service.status(id,request.getStatus()); }
    @DeleteMapping("/{id}") @RequireAnyAuthority({"ADMIN","MANAGER"})
    public ResponseEntity<Void> delete(@PathVariable UUID id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
