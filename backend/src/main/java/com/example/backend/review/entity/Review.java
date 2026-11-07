package com.example.backend.review.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reviews")
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "review_id")
    private UUID reviewId;
    @Column(name = "customer_id") private UUID customerId;
    @Column(name = "product_id") private UUID productId;
    @Column(name = "order_item_id") private UUID orderItemId;
    /** Employee who replied or moderated. */
    @Column(name = "employee_id") private UUID employeeId;
    @Column(name = "rating") private Integer rating;
    @Column(name = "review_text") private String reviewText;
    @Column(name = "reply_text") private String replyText;
    @Column(name = "created_at") private OffsetDateTime createdAt;
    @Column(name = "replied_at") private OffsetDateTime repliedAt;
    @Enumerated(EnumType.STRING) @Column(name = "status") private ReviewStatus status;

    public UUID getReviewId() { return reviewId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID value) { this.customerId = value; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID value) { this.productId = value; }
    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID value) { this.orderItemId = value; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID value) { this.employeeId = value; }
    public Integer getRating() { return rating; }
    public void setRating(Integer value) { this.rating = value; }
    public String getReviewText() { return reviewText; }
    public void setReviewText(String value) { this.reviewText = value; }
    public String getReplyText() { return replyText; }
    public void setReplyText(String value) { this.replyText = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt = value; }
    public OffsetDateTime getRepliedAt() { return repliedAt; }
    public void setRepliedAt(OffsetDateTime value) { this.repliedAt = value; }
    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus value) { this.status = value; }
}
