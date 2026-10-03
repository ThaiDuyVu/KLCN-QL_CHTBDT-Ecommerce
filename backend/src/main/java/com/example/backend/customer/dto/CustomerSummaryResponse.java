package com.example.backend.customer.dto;

import java.util.UUID;
import com.example.backend.auth.entity.UserStatus;
import java.time.OffsetDateTime;

public class CustomerSummaryResponse {
    private final UUID customerId;
    private final UUID userId;
    private final String fullName;
    private final String address;
    private final int loyaltyPoint;
    private final String username;
    private final String displayName;
    private final String email;
    private final String phone;
    private final UserStatus accountStatus;
    private final OffsetDateTime createdAt;
    private CustomerOrderSummaryResponse orderSummary;

    public CustomerSummaryResponse(UUID customerId, UUID userId, String fullName, String address, int loyaltyPoint, String username, String displayName, String email, String phone, UserStatus accountStatus, OffsetDateTime createdAt, CustomerOrderSummaryResponse orderSummary) {
        this.customerId = customerId;
        this.userId = userId;
        this.fullName = fullName;
        this.address = address;
        this.loyaltyPoint = loyaltyPoint;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.accountStatus = accountStatus;
        this.createdAt = createdAt;
        this.orderSummary = orderSummary;
    }

    public UUID getCustomerId() { return customerId; }
    public UUID getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getAddress() { return address; }
    public int getLoyaltyPoint() { return loyaltyPoint; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public UserStatus getAccountStatus() { return accountStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public CustomerOrderSummaryResponse getOrderSummary() { return orderSummary; }
    public void setOrderSummary(CustomerOrderSummaryResponse orderSummary) { this.orderSummary = orderSummary; }
}
