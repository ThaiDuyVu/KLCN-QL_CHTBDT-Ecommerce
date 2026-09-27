package com.example.backend.product.exception;

public class ProductUploadException extends RuntimeException {
    private final int status;
    public ProductUploadException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
