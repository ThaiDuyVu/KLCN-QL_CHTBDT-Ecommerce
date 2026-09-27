package com.example.backend.order.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class UpdateOrderStatusRequest {
    @NotNull
    private com.example.backend.order.entity.OrderStatus status;
    public UpdateOrderStatusRequest() {}
    public com.example.backend.order.entity.OrderStatus getStatus() { return status; }
    public void setStatus(com.example.backend.order.entity.OrderStatus value) { this.status = value; }
}
