package com.example.backend.brand.exception;

public class BrandInUseException extends RuntimeException {

    public BrandInUseException(String message) {
        super(message);
    }

    public BrandInUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
