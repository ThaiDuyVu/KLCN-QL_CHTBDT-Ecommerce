package com.example.backend.cart.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public class UpdateCartQuantityRequest {
    @NotNull @Min(value = 1, message = "quantity phải >= 1")
    private Integer quantity;
    public UpdateCartQuantityRequest() {}
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { this.quantity = value; }
}
