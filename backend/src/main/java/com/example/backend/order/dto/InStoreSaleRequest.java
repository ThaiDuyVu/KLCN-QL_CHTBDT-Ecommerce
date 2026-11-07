package com.example.backend.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/**
 * Counter sale (V13). warehouseId may be omitted by branch staff (their branch is used).
 * serialNumbers: optional per line for serial/IMEI-tracked goods, the units actually handed over;
 * when omitted the system picks available units of the branch.
 */
public record InStoreSaleRequest(
        @NotNull(message = "Vui lòng chọn khách hàng") UUID customerId,
        UUID warehouseId,
        @NotEmpty(message = "Chưa có sản phẩm nào") @Size(max = 100) List<@Valid Line> items,
        @Size(max = 1000) String note) {
    public record Line(@NotNull UUID variantId,
                       @NotNull @Min(value = 1, message = "Số lượng phải lớn hơn 0") @Max(100) Integer quantity,
                       @Size(max = 100) List<@NotBlank String> serialNumbers) {}
}
