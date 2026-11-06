package com.example.backend.returns.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record CreateReturnRequest(
        @NotNull(message = "Vui lòng chọn đơn hàng") UUID orderId,
        @NotBlank(message = "Vui lòng nhập lý do đổi trả") @Size(max = 1000, message = "Lý do tối đa 1000 ký tự") String reason,
        @NotEmpty(message = "Vui lòng chọn sản phẩm cần trả") @Size(max = 100) List<@Valid Line> items) {
    public record Line(
            @NotNull UUID orderItemId,
            @NotNull @Min(value = 1, message = "Số lượng trả phải lớn hơn 0") Integer quantity,
            @Size(max = 300, message = "Lý do từng sản phẩm tối đa 300 ký tự") String reason) {}
}
