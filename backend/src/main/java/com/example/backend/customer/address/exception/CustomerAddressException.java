package com.example.backend.customer.address.exception;

public class CustomerAddressException extends RuntimeException {
    private final int status;
    public CustomerAddressException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
