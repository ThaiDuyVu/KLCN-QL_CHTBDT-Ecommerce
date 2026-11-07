package com.example.backend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    /** Login account; null for a walk-in customer registered at the counter (V13). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            unique = true
    )
    private User user;

    /** Phone of a walk-in customer (customers with an account keep their phone on users). */
    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "created_at", insertable = false, updatable = false)
    private java.time.OffsetDateTime createdAt;

    @Column(
            name = "full_name",
            nullable = false,
            length = 255
    )
    private String fullName;

    @Column(
            name = "address",
            length = 500
    )
    private String address;

    @Column(
            name = "loyalty_point",
            nullable = false
    )
    private Integer loyaltyPoint = 0;

    public Customer() {
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getLoyaltyPoint() {
        return loyaltyPoint;
    }

    public void setLoyaltyPoint(Integer loyaltyPoint) {
        this.loyaltyPoint = loyaltyPoint;
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public java.time.OffsetDateTime getCreatedAt() { return createdAt; }

}
