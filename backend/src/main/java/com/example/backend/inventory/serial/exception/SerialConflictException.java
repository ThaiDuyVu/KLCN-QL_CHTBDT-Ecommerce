package com.example.backend.inventory.serial.exception;
public class SerialConflictException extends RuntimeException {
    public SerialConflictException(String message) { super(message); }
    public SerialConflictException(String message, Throwable cause) { super(message, cause); }
}
