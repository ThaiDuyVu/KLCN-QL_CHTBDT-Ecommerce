package com.example.backend.review.service;

import com.example.backend.review.dto.*;
import com.example.backend.review.entity.ReviewStatus;

import java.util.List;
import java.util.UUID;

public interface ReviewService {
    ReviewResponse create(UUID userId, CreateReviewRequest request);
    /** The caller's reviews for the lines of one of their orders (to know what is still reviewable). */
    List<ReviewResponse> mineForOrder(UUID userId, UUID orderId);
    ReviewPageResponse forProduct(UUID productId, int page, int size);
    ReviewPageResponse list(ReviewStatus status, int page, int size);
    ReviewResponse moderate(UUID userId, UUID reviewId, ModerateReviewRequest request);
}
