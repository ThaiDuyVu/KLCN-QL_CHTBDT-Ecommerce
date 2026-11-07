package com.example.backend.returns.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record CreateReturnRequest(
        @NotNull(message = "Vui lòng chọn đơn hàng") UUID orderId,
        @NotBlank(message = "Vui lòng nhập lý do đổi trả") @Size(max = 1000, message = "Lý do tối đa 1000 ký tự") String reason,
        @NotEmpty(message = "Vui lòng chọn sản phẩm cần trả") @Size(max = 100) List<@Valid Line> items,
        /** Default RETURN (refund). EXCHANGE needs exchangeItems. */
        com.example.backend.returns.entity.ReturnType type,
        @Size(max = 20) List<@Valid ExchangeLine> exchangeItems) {
    public CreateReturnRequest(UUID orderId, String reason, List<Line> items) { this(orderId, reason, items, null, null); }
    public record ExchangeLine(@NotNull UUID variantId,
                               @NotNull @Min(value = 1, message = "Số lượng đổi phải lớn hơn 0") @Max(20) Integer quantity) {}
    public record Line(
            @NotNull UUID orderItemId,
            @NotNull @Min(value = 1, message = "Số lượng trả phải lớn hơn 0") Integer quantity,
            @Size(max = 300, message = "Lý do từng sản phẩm tối đa 300 ký tự") String reason) {}
}
