package com.example.backend.brand.exception;

public class InvalidBrandPaginationException extends RuntimeException {

    public InvalidBrandPaginationException(String message) {
        super(message);
    }
}
