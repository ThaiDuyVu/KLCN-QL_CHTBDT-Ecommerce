package com.example.backend.warranty.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "warranty_tickets")
public class WarrantyTicket {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;
    @Column(name = "ticket_code", nullable = false, unique = true, length = 50)
    private String ticketCode;
    @Column(name = "warranty_id", nullable = false)
    private UUID warrantyId;
    @Column(name = "serial_id", nullable = false)
    private UUID serialId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "employee_id")
    private UUID employeeId;
    @Column(name = "issue_description", nullable = false, columnDefinition = "text")
    private String issueDescription;
    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WarrantyTicketStatus status;

    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID ticketId) { this.ticketId = ticketId; }
    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }
    public UUID getWarrantyId() { return warrantyId; }
    public void setWarrantyId(UUID warrantyId) { this.warrantyId = warrantyId; }
    public UUID getSerialId() { return serialId; }
    public void setSerialId(UUID serialId) { this.serialId = serialId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public String getIssueDescription() { return issueDescription; }
    public void setIssueDescription(String issueDescription) { this.issueDescription = issueDescription; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public WarrantyTicketStatus getStatus() { return status; }
    public void setStatus(WarrantyTicketStatus status) { this.status = status; }
}
