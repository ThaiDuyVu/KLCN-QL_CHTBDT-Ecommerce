package com.example.backend.goodsreceipt.dto.response;

import com.example.backend.goodsreceipt.entity.GoodsReceiptStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class GoodsReceiptResponse {

    private UUID receiptId;
    private String receiptCode;
    private UUID supplierId;
    private UUID warehouseId;
    private UUID employeeId;
    private OffsetDateTime receiptDate;
    private BigDecimal totalAmount;
    private GoodsReceiptStatus status;
    private List<GoodsReceiptItemResponse> items;

    public GoodsReceiptResponse() {
    }

    public GoodsReceiptResponse(
            UUID receiptId,
            String receiptCode,
            UUID supplierId,
            UUID warehouseId,
            UUID employeeId,
            OffsetDateTime receiptDate,
            BigDecimal totalAmount,
            GoodsReceiptStatus status,
            List<GoodsReceiptItemResponse> items
    ) {
        this.receiptId = receiptId;
        this.receiptCode = receiptCode;
        this.supplierId = supplierId;
        this.warehouseId = warehouseId;
        this.employeeId = employeeId;
        this.receiptDate = receiptDate;
        this.totalAmount = totalAmount;
        this.status = status;
        this.items = items;
    }

    public UUID getReceiptId() { return receiptId; }
    public String getReceiptCode() { return receiptCode; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getEmployeeId() { return employeeId; }
    public OffsetDateTime getReceiptDate() { return receiptDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public GoodsReceiptStatus getStatus() { return status; }
    public List<GoodsReceiptItemResponse> getItems() { return items; }
}
