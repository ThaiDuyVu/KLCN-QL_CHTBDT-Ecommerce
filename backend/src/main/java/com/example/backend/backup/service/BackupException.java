package com.example.backend.backup.service;

public class BackupException extends RuntimeException {
    private final int status;
    public BackupException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
