package com.example.backend.goodsreceipt.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "goods_receipt_item_devices")
public class GoodsReceiptItemDevice {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "receipt_device_id", nullable = false, updatable = false)
    private UUID receiptDeviceId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_item_id", nullable = false)
    private GoodsReceiptItem receiptItem;
    @Column(name = "serial_number", nullable = false, length = 255)
    private String serialNumber;
    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<GoodsReceiptItemDeviceImei> imeis = new ArrayList<>();

    public void addImei(GoodsReceiptItemDeviceImei imei) { imeis.add(imei); imei.setDevice(this); }
    public UUID getReceiptDeviceId() { return receiptDeviceId; }
    public void setReceiptDeviceId(UUID receiptDeviceId) { this.receiptDeviceId = receiptDeviceId; }
    public GoodsReceiptItem getReceiptItem() { return receiptItem; }
    public void setReceiptItem(GoodsReceiptItem receiptItem) { this.receiptItem = receiptItem; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public List<GoodsReceiptItemDeviceImei> getImeis() { return imeis; }
}
