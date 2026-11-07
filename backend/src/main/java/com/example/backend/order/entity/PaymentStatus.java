package com.example.backend.order.entity;
/** REFUNDED: a PAID payment whose order was cancelled by a manager and refunded outside the system. */
public enum PaymentStatus { PENDING, PAID, FAILED, REFUNDED }
