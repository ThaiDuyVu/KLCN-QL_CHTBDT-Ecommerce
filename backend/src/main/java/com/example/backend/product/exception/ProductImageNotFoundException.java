package com.example.backend.product.exception;

public class ProductImageNotFoundException extends RuntimeException {

    public ProductImageNotFoundException(String message) {
        super(message);
    }
}
