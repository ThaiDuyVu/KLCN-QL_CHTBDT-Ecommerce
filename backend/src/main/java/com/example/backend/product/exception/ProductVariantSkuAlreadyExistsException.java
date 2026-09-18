package com.example.backend.product.exception;

public class ProductVariantSkuAlreadyExistsException extends RuntimeException {

    public ProductVariantSkuAlreadyExistsException(String message) {
        super(message);
    }

    public ProductVariantSkuAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
