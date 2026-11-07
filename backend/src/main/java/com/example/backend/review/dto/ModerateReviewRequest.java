package com.example.backend.review.dto;

import com.example.backend.review.entity.ReviewStatus;
import jakarta.validation.constraints.Size;

/** Either field may be omitted: reply to the customer and/or hide or show the review. */
public record ModerateReviewRequest(@Size(max = 2000, message = "Phản hồi tối đa 2000 ký tự") String replyText, ReviewStatus status) {}
