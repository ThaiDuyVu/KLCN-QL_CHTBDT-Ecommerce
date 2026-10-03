package com.example.backend.report.exception;

public class ReportException extends RuntimeException {
    private final int status;
    public ReportException(int status, String message) {
        super(message);
        this.status = status;
    }
    public int getStatus() { return status; }
}
