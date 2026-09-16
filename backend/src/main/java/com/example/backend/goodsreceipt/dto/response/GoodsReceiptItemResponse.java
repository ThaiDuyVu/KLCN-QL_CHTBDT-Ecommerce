package com.example.backend.goodsreceipt.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public class GoodsReceiptItemResponse {

    private UUID receiptItemId;
    private UUID variantId;
    private Integer quantity;
    private BigDecimal unitCost;

    public GoodsReceiptItemResponse() {
    }

    public GoodsReceiptItemResponse(
            UUID receiptItemId,
            UUID variantId,
            Integer quantity,
            BigDecimal unitCost
    ) {
        this.receiptItemId = receiptItemId;
        this.variantId = variantId;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    public UUID getReceiptItemId() { return receiptItemId; }
    public UUID getVariantId() { return variantId; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
}
