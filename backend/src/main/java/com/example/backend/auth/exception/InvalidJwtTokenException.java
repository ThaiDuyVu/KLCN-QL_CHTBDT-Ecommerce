package com.example.backend.auth.exception;

public class InvalidJwtTokenException extends RuntimeException {

    public InvalidJwtTokenException() {
        super("Invalid or expired JWT");
    }
}
