package com.example.backend.warranty.exception;

public class WarrantyException extends RuntimeException {
    private final int status;
    public WarrantyException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
