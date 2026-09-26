package com.example.backend.order.dto;

import com.example.backend.order.entity.InstallmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateInstallmentStatusRequest(@NotNull InstallmentStatus status) {}
