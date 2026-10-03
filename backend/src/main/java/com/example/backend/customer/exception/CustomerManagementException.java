package com.example.backend.customer.exception;

public class CustomerManagementException extends RuntimeException {
    private final int status;
    public CustomerManagementException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
