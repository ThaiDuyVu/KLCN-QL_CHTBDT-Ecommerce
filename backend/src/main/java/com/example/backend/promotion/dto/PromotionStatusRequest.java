package com.example.backend.promotion.dto;
import com.example.backend.promotion.entity.PromotionStatus;
import jakarta.validation.constraints.NotNull;
public class PromotionStatusRequest {
    @NotNull(message="status là bắt buộc")
    private PromotionStatus status;
    public PromotionStatus getStatus() { return status; }
    public void setStatus(PromotionStatus value) { this.status = value; }
}
