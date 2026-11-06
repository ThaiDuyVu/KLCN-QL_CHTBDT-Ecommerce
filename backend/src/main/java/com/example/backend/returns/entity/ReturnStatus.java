package com.example.backend.returns.entity;

/**
 * REQUESTED by the customer → APPROVED or REJECTED by sales → RECEIVED by the warehouse (goods back, inspected)
 * → REFUNDED by management. APPROVED may still be REJECTED if the goods never come back.
 */
public enum ReturnStatus { REQUESTED, APPROVED, REJECTED, RECEIVED, REFUNDED }
