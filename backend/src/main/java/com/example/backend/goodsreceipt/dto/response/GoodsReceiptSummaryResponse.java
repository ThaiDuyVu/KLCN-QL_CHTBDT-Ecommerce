package com.example.backend.goodsreceipt.dto.response;

import com.example.backend.goodsreceipt.entity.GoodsReceiptStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class GoodsReceiptSummaryResponse {

    private UUID receiptId;
    private String receiptCode;
    private UUID supplierId;
    private UUID warehouseId;
    private UUID employeeId;
    private OffsetDateTime receiptDate;
    private BigDecimal totalAmount;
    private GoodsReceiptStatus status;

    public GoodsReceiptSummaryResponse() {
    }

    public GoodsReceiptSummaryResponse(
            UUID receiptId,
            String receiptCode,
            UUID supplierId,
            UUID warehouseId,
            UUID employeeId,
            OffsetDateTime receiptDate,
            BigDecimal totalAmount,
            GoodsReceiptStatus status
    ) {
        this.receiptId = receiptId;
        this.receiptCode = receiptCode;
        this.supplierId = supplierId;
        this.warehouseId = warehouseId;
        this.employeeId = employeeId;
        this.receiptDate = receiptDate;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public UUID getReceiptId() { return receiptId; }
    public String getReceiptCode() { return receiptCode; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getEmployeeId() { return employeeId; }
    public OffsetDateTime getReceiptDate() { return receiptDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public GoodsReceiptStatus getStatus() { return status; }
}
