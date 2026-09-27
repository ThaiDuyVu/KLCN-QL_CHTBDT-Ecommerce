package com.example.backend.inventory.serial.exception;
public class SerialNotFoundException extends RuntimeException {
    public SerialNotFoundException(String message) { super(message); }
}
