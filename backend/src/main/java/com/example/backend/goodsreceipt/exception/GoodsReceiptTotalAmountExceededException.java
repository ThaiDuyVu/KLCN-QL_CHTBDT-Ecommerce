package com.example.backend.goodsreceipt.exception;

public class GoodsReceiptTotalAmountExceededException extends RuntimeException {

    public GoodsReceiptTotalAmountExceededException(String message) {
        super(message);
    }
}
