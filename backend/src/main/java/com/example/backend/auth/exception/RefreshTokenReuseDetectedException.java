package com.example.backend.auth.exception;

public class RefreshTokenReuseDetectedException extends RuntimeException {

    public RefreshTokenReuseDetectedException() {
        super("Refresh token reuse detected");
    }
}
