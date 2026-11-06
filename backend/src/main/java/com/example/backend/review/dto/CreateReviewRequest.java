package com.example.backend.review.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateReviewRequest(
        @NotNull(message = "Vui lòng chọn sản phẩm đã mua") UUID orderItemId,
        @NotNull @Min(value = 1, message = "Số sao từ 1 đến 5") @Max(value = 5, message = "Số sao từ 1 đến 5") Integer rating,
        @Size(max = 2000, message = "Nội dung tối đa 2000 ký tự") String reviewText) {}
