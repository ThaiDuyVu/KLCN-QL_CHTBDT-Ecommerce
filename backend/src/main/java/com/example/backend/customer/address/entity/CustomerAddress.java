package com.example.backend.customer.address.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "customer_addresses")
public class CustomerAddress {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "address_id", nullable = false, updatable = false)
    private UUID addressId;
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;
    @Column(name = "label", length = 100)
    private String label;
    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;
    @Column(name = "recipient_phone", nullable = false, length = 30)
    private String recipientPhone;
    @Column(name = "address_line", nullable = false, length = 500)
    private String addressLine;
    @Column(name = "ward", length = 150)
    private String ward;
    @Column(name = "district", length = 150)
    private String district;
    @Column(name = "province", length = 150)
    private String province;
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(ZoneOffset.UTC); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }
    public UUID getAddressId() { return addressId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID value) { customerId = value; }
    public String getLabel() { return label; }
    public void setLabel(String value) { label = value; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String value) { recipientName = value; }
    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String value) { recipientPhone = value; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String value) { addressLine = value; }
    public String getWard() { return ward; }
    public void setWard(String value) { ward = value; }
    public String getDistrict() { return district; }
    public void setDistrict(String value) { district = value; }
    public String getProvince() { return province; }
    public void setProvince(String value) { province = value; }
    public boolean isDefault() { return defaultAddress; }
    public void setDefault(boolean value) { defaultAddress = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
