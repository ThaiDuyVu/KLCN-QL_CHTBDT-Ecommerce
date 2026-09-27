package com.example.backend.goodsreceipt.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import com.example.backend.product.entity.ProductTrackingType;

public class GoodsReceiptItemResponse {

    private UUID receiptItemId;
    private UUID variantId;
    private Integer quantity;
    private BigDecimal unitCost;
    private String sku;
    private String productName;
    private ProductTrackingType trackingType;
    private List<ReceiptDeviceResponse> devices;

    public GoodsReceiptItemResponse() {
    }

    public GoodsReceiptItemResponse(
            UUID receiptItemId,
            UUID variantId,
            Integer quantity,
            BigDecimal unitCost,
            String sku,
            String productName,
            ProductTrackingType trackingType,
            List<ReceiptDeviceResponse> devices
    ) {
        this.receiptItemId = receiptItemId;
        this.variantId = variantId;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.sku = sku;
        this.productName = productName;
        this.trackingType = trackingType;
        this.devices = devices;
    }

    public GoodsReceiptItemResponse(UUID receiptItemId, UUID variantId, Integer quantity, BigDecimal unitCost) {
        this(receiptItemId, variantId, quantity, unitCost, null, null, null, List.of());
    }

    public UUID getReceiptItemId() { return receiptItemId; }
    public UUID getVariantId() { return variantId; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public ProductTrackingType getTrackingType() { return trackingType; }
    public List<ReceiptDeviceResponse> getDevices() { return devices; }
}
