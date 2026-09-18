package com.example.backend.goodsreceipt.exception;

public class ReceiptCodeAlreadyExistsException extends RuntimeException {

    public ReceiptCodeAlreadyExistsException(String message) {
        super(message);
    }
}
