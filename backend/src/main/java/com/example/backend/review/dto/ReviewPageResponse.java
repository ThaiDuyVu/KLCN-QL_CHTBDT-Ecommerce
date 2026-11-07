package com.example.backend.review.dto;

import java.util.List;

/** averageRating/totalReviews describe the product (visible reviews only); null on the staff list. */
public record ReviewPageResponse(List<ReviewResponse> content, int page, int size, long totalElements, int totalPages,
                                 Double averageRating, Long totalReviews) {}
