package com.example.backend.auth.exception;

public class ProtectedPermissionException extends RuntimeException {

    public ProtectedPermissionException(String message) {
        super(message);
    }
}