package com.example.backend.backup.dto;

/** safetyBackup: the automatic backup of the data as it was right before the restore. */
public record RestoreBackupResponse(String restored, BackupFileResponse safetyBackup) {}
