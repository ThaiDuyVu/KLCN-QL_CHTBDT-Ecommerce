package com.example.backend.goodsreceipt.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "goods_receipt_item_device_imeis")
public class GoodsReceiptItemDeviceImei {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "receipt_device_imei_id", nullable = false, updatable = false)
    private UUID receiptDeviceImeiId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_device_id", nullable = false)
    private GoodsReceiptItemDevice device;
    @Column(name = "imei_number", nullable = false, length = 50)
    private String imeiNumber;
    public UUID getReceiptDeviceImeiId() { return receiptDeviceImeiId; }
    public void setReceiptDeviceImeiId(UUID value) { this.receiptDeviceImeiId = value; }
    public GoodsReceiptItemDevice getDevice() { return device; }
    public void setDevice(GoodsReceiptItemDevice device) { this.device = device; }
    public String getImeiNumber() { return imeiNumber; }
    public void setImeiNumber(String imeiNumber) { this.imeiNumber = imeiNumber; }
}
