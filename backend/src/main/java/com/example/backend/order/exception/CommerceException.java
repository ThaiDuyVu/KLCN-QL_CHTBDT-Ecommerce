package com.example.backend.order.exception;
public class CommerceException extends RuntimeException {
    private final int status;
    public CommerceException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
