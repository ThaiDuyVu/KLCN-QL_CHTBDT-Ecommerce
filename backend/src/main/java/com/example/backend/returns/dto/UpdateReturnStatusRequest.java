package com.example.backend.returns.dto;

import com.example.backend.returns.entity.ReturnCondition;
import com.example.backend.returns.entity.ReturnStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** note: required when rejecting. conditions: required for RECEIVED, one per returned line. */
public record UpdateReturnStatusRequest(
        @NotNull ReturnStatus status,
        @Size(max = 500) String note,
        List<@Valid LineCondition> conditions) {
    public record LineCondition(@NotNull UUID returnItemId, @NotNull ReturnCondition condition) {}
}
