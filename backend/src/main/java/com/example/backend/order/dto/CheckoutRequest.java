package com.example.backend.order.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class CheckoutRequest {
    @NotNull(message = "Vui lòng chọn địa chỉ giao hàng")
    private UUID addressId;
    // Legacy fields are accepted for JSON compatibility but never trusted for a new order.
    private String recipientName;
    private String recipientPhone;
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
    public UUID getAddressId() { return addressId; }
    public void setAddressId(UUID value) { this.addressId = value; }
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
