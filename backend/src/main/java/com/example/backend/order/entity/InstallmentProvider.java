package com.example.backend.order.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "installment_providers")
public class InstallmentProvider {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "provider_name", nullable = false, length = 255)
    private String providerName;

    @Column(name = "provider_code", nullable = false, length = 100, unique = true)
    private String providerCode;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InstallmentProviderStatus status;

    public UUID getProviderId() { return providerId; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public InstallmentProviderStatus getStatus() { return status; }
    public void setStatus(InstallmentProviderStatus status) { this.status = status; }
}
