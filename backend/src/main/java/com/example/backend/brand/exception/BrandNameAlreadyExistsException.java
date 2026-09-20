package com.example.backend.brand.exception;

public class BrandNameAlreadyExistsException extends RuntimeException {

    public BrandNameAlreadyExistsException(String message) {
        super(message);
    }

    public BrandNameAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
