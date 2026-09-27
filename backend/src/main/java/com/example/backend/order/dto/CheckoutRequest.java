package com.example.backend.order.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class CheckoutRequest {
    @NotBlank(message = "Tên người nhận là bắt buộc") @Size(max = 255)
    private String recipientName;
    @NotBlank(message = "Số điện thoại là bắt buộc") @Size(max = 30)
    private String recipientPhone;
    @NotBlank(message = "Địa chỉ giao hàng là bắt buộc")
    private String shippingAddress;
    private String note;
    @NotNull(message = "paymentMethod là bắt buộc")
    private com.example.backend.order.entity.PaymentMethod paymentMethod;
    private UUID providerId;
    @Min(value = 1, message = "termMonths phải lớn hơn 0")
    private Integer termMonths;
    @DecimalMin(value = "0.00", message = "downPayment không được âm")
    @Digits(integer = 13, fraction = 2, message = "downPayment vượt giới hạn tiền tệ")
    private BigDecimal downPayment;
    public CheckoutRequest() {}
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String value) { this.recipientName = value == null ? null : value.trim(); }
    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String value) { this.recipientPhone = value == null ? null : value.trim(); }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String value) { this.shippingAddress = value == null ? null : value.trim(); }
    public String getNote() { return note; }
    public void setNote(String value) { this.note = value == null ? null : value.trim(); }
    public com.example.backend.order.entity.PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(com.example.backend.order.entity.PaymentMethod value) { this.paymentMethod = value; }
    public UUID getProviderId() { return providerId; }
    public void setProviderId(UUID value) { this.providerId = value; }
    public Integer getTermMonths() { return termMonths; }
    public void setTermMonths(Integer value) { this.termMonths = value; }
    public BigDecimal getDownPayment() { return downPayment; }
    public void setDownPayment(BigDecimal value) { this.downPayment = value; }
}
