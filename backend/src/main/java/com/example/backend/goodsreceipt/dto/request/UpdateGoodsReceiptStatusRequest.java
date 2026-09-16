package com.example.backend.goodsreceipt.dto.request;

import com.example.backend.goodsreceipt.entity.GoodsReceiptStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateGoodsReceiptStatusRequest {

    @NotNull(message = "Status is required")
    private GoodsReceiptStatus status;

    public UpdateGoodsReceiptStatusRequest() {
    }

    public GoodsReceiptStatus getStatus() {
        return status;
    }

    public void setStatus(GoodsReceiptStatus status) {
        this.status = status;
    }
}
