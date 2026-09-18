package com.example.backend.category.exception;

public class CategoryInUseException extends RuntimeException {

    public CategoryInUseException(String message) {
        super(message);
    }

    public CategoryInUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
