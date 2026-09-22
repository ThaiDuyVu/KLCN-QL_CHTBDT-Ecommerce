package com.example.backend.product.exception;

public class InvalidProductPaginationException extends RuntimeException {

    public InvalidProductPaginationException(String message) {
        super(message);
    }
}
