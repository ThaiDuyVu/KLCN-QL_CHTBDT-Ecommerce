package com.example.backend.category.exception;

public class InvalidCategoryParentException extends RuntimeException {

    public InvalidCategoryParentException(String message) {
        super(message);
    }
}
