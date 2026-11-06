package com.example.backend.backup.service;

import com.example.backend.backup.dto.BackupFileResponse;
import com.example.backend.backup.dto.RestoreBackupResponse;

import java.nio.file.Path;
import java.util.List;

public interface BackupService {
    List<BackupFileResponse> list();
    BackupFileResponse create();
    Path file(String name);
    RestoreBackupResponse restore(String name, String confirmName);
    void delete(String name);
}
