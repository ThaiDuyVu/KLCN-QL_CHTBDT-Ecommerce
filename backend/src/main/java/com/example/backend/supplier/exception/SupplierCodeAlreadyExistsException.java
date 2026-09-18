package com.example.backend.supplier.exception;

public class SupplierCodeAlreadyExistsException extends RuntimeException {

    public SupplierCodeAlreadyExistsException(String message) {
        super(message);
    }
}
