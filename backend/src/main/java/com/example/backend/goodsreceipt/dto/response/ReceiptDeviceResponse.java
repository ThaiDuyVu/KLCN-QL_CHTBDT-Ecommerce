package com.example.backend.goodsreceipt.dto.response;
import java.util.List;
import java.util.UUID;
public record ReceiptDeviceResponse(UUID receiptDeviceId, String serialNumber, List<String> imeiNumbers) {}
