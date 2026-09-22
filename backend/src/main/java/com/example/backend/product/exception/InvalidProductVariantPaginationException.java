package com.example.backend.product.exception;

public class InvalidProductVariantPaginationException extends RuntimeException {

    public InvalidProductVariantPaginationException(String message) {
        super(message);
    }
}
