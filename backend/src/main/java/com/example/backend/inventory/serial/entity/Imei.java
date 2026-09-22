package com.example.backend.inventory.serial.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "imei")
public class Imei {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "imei_id", nullable = false, updatable = false)
    private UUID imeiId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "serial_id", nullable = false)
    private SerialNumber serial;
    @Column(name = "imei_number", nullable = false, unique = true, length = 50)
    private String imeiNumber;
    public UUID getImeiId() { return imeiId; }
    public void setImeiId(UUID imeiId) { this.imeiId = imeiId; }
    public SerialNumber getSerial() { return serial; }
    public void setSerial(SerialNumber serial) { this.serial = serial; }
    public String getImeiNumber() { return imeiNumber; }
    public void setImeiNumber(String imeiNumber) { this.imeiNumber = imeiNumber; }
}
