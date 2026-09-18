package com.example.backend.product.exception;

public class ProductVariantInUseException extends RuntimeException {

    public ProductVariantInUseException(String message) {
        super(message);
    }

    public ProductVariantInUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
