package com.example.backend.product.exception;

public class ProductReferenceNotFoundException extends RuntimeException {

    public ProductReferenceNotFoundException(String message) {
        super(message);
    }

    public ProductReferenceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
