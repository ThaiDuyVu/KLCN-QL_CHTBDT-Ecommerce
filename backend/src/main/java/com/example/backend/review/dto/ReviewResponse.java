package com.example.backend.review.dto;

import com.example.backend.review.entity.ReviewStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewResponse(UUID reviewId, UUID productId, String productName, UUID orderItemId, String customerName,
                             int rating, String reviewText, String replyText, String repliedBy, OffsetDateTime createdAt,
                             OffsetDateTime repliedAt, ReviewStatus status) {}
