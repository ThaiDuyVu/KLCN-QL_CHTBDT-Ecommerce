package com.example.backend.promotion.exception;
public class PromotionException extends RuntimeException {
    private final int status;
    public PromotionException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
