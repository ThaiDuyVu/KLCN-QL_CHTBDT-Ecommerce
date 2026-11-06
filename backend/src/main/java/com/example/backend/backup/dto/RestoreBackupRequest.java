package com.example.backend.backup.dto;

import jakarta.validation.constraints.NotBlank;

/** The admin retypes the backup name to confirm a restore, which replaces all current data. */
public record RestoreBackupRequest(@NotBlank(message = "Nhập lại tên bản sao lưu để xác nhận") String confirmName) {}
