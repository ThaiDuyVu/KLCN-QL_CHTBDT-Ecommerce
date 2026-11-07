package com.example.backend.invoice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "invoice_id")
    private UUID invoiceId;
    @Column(name = "order_id")
    private UUID orderId;
    @Column(name = "invoice_number")
    private String invoiceNumber;
    @Column(name = "issued_date")
    private OffsetDateTime issuedDate;
    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;
    @Enumerated(EnumType.STRING) @Column(name = "status")
    private InvoiceStatus status;

    public UUID getInvoiceId() { return invoiceId; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID value) { this.orderId = value; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String value) { this.invoiceNumber = value; }
    public OffsetDateTime getIssuedDate() { return issuedDate; }
    public void setIssuedDate(OffsetDateTime value) { this.issuedDate = value; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal value) { this.totalAmount = value; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus value) { this.status = value; }
}
