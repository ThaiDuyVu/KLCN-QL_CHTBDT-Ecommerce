package com.example.backend.product.exception;

public class ProductImageConflictException extends RuntimeException {

    public ProductImageConflictException(String message) {
        super(message);
    }

    public ProductImageConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
