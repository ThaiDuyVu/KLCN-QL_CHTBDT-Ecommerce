package com.example.backend.inventory.serial.entity;

import com.example.backend.product.entity.ProductVariant;
import com.example.backend.warehouse.entity.Warehouse;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "serial_numbers")
public class SerialNumber {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "serial_id", nullable = false, updatable = false)
    private UUID serialId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;
    @Column(name = "serial_number", nullable = false, unique = true, length = 255)
    private String serialNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SerialStatus status;
    @OneToMany(mappedBy = "serial", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<Imei> imeis = new ArrayList<>();

    public void addImei(Imei imei) { imeis.add(imei); imei.setSerial(this); }
    public UUID getSerialId() { return serialId; }
    public void setSerialId(UUID serialId) { this.serialId = serialId; }
    public ProductVariant getVariant() { return variant; }
    public void setVariant(ProductVariant variant) { this.variant = variant; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public SerialStatus getStatus() { return status; }
    public void setStatus(SerialStatus status) { this.status = status; }
    public List<Imei> getImeis() { return imeis; }
    public void setImeis(List<Imei> imeis) { this.imeis = imeis; }
}
