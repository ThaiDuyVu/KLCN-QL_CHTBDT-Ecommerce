package com.example.backend.goodsreceipt.exception;

public class InvalidGoodsReceiptStatusTransitionException extends RuntimeException {

    public InvalidGoodsReceiptStatusTransitionException(String message) {
        super(message);
    }
}
