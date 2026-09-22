package com.example.backend.goodsreceipt.controller;

import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.goodsreceipt.dto.request.CreateGoodsReceiptRequest;
import com.example.backend.goodsreceipt.dto.request.UpdateGoodsReceiptStatusRequest;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptPageResponse;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptResponse;
import com.example.backend.goodsreceipt.service.GoodsReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/goods-receipts")
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;

    public GoodsReceiptController(GoodsReceiptService goodsReceiptService) {
        this.goodsReceiptService = goodsReceiptService;
    }

    @GetMapping
    public ResponseEntity<GoodsReceiptPageResponse> getGoodsReceipts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("Page không được nhỏ hơn 0");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size phải lớn hơn 0");
        }
        return ResponseEntity.ok(goodsReceiptService.getGoodsReceipts(page, size));
    }

    @GetMapping("/{receiptId}")
    public ResponseEntity<GoodsReceiptResponse> getGoodsReceiptById(
            @PathVariable UUID receiptId
    ) {
        return ResponseEntity.ok(goodsReceiptService.getGoodsReceiptById(receiptId));
    }

    @PostMapping
    public ResponseEntity<GoodsReceiptResponse> createGoodsReceipt(
            @Valid @RequestBody CreateGoodsReceiptRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(goodsReceiptService.createGoodsReceipt(request));
    }

    @PatchMapping("/{receiptId}/status")
    @RequireAnyAuthority({"ADMIN", "MANAGER"})
    public ResponseEntity<GoodsReceiptResponse> updateGoodsReceiptStatus(
            @PathVariable UUID receiptId,
            @Valid @RequestBody UpdateGoodsReceiptStatusRequest request
    ) {
        return ResponseEntity.ok(
                goodsReceiptService.updateGoodsReceiptStatus(receiptId, request.getStatus())
        );
    }
}
