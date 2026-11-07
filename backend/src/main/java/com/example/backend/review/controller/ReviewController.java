package com.example.backend.review.controller;

import com.example.backend.auth.service.AuthenticatedUserPrincipal;
import com.example.backend.common.security.RequireAnyAuthority;
import com.example.backend.review.dto.*;
import com.example.backend.review.entity.ReviewStatus;
import com.example.backend.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) { this.service = service; }

    /** Product page: visible reviews and rating summary, for any signed-in user (same rule as product reads). */
    @GetMapping("/api/v1/products/{productId}/reviews")
    public ReviewPageResponse forProduct(@PathVariable UUID productId, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return service.forProduct(productId, page, size);
    }

    @PostMapping("/api/reviews") @ResponseStatus(HttpStatus.CREATED) @RequireAnyAuthority({"CUSTOMER"})
    public ReviewResponse create(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @Valid @RequestBody CreateReviewRequest request) {
        return service.create(user.getUserId(), request);
    }

    @GetMapping("/api/reviews/mine/by-order/{orderId}") @RequireAnyAuthority({"CUSTOMER"})
    public List<ReviewResponse> mineForOrder(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID orderId) {
        return service.mineForOrder(user.getUserId(), orderId);
    }

    @GetMapping("/api/reviews") @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF", "SALES_STAFF"})
    public ReviewPageResponse list(@RequestParam(required = false) ReviewStatus status, @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    @PatchMapping("/api/reviews/{reviewId}") @RequireAnyAuthority({"ADMIN", "MANAGER", "STAFF", "SALES_STAFF"})
    public ReviewResponse moderate(@AuthenticationPrincipal AuthenticatedUserPrincipal user, @PathVariable UUID reviewId,
                                   @Valid @RequestBody ModerateReviewRequest request) {
        return service.moderate(user.getUserId(), reviewId, request);
    }
}
