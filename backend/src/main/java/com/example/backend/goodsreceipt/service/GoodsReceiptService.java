package com.example.backend.goodsreceipt.service;

import com.example.backend.goodsreceipt.dto.request.CreateGoodsReceiptRequest;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptPageResponse;
import com.example.backend.goodsreceipt.dto.response.GoodsReceiptResponse;
import com.example.backend.goodsreceipt.entity.GoodsReceiptStatus;

import java.util.UUID;

public interface GoodsReceiptService {

    GoodsReceiptPageResponse getGoodsReceipts(int page, int size);

    GoodsReceiptResponse getGoodsReceiptById(UUID receiptId);

    GoodsReceiptResponse createGoodsReceipt(CreateGoodsReceiptRequest request);

    GoodsReceiptResponse updateGoodsReceipt(UUID receiptId, CreateGoodsReceiptRequest request);

    GoodsReceiptResponse updateGoodsReceiptStatus(UUID receiptId, GoodsReceiptStatus targetStatus);
}
