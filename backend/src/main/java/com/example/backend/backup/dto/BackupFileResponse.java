package com.example.backend.backup.dto;

import java.time.OffsetDateTime;

public record BackupFileResponse(String name, long sizeBytes, OffsetDateTime createdAt, boolean automatic) {}
