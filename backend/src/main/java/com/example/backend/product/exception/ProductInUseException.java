package com.example.backend.product.exception;

public class ProductInUseException extends RuntimeException {

    public ProductInUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
