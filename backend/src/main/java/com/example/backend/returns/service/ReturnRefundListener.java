package com.example.backend.returns.service;

import com.example.backend.order.entity.Order;

/** Notified inside the refund transaction when every unit of an order has been returned and refunded. */
public interface ReturnRefundListener {
    void orderFullyRefunded(Order order);
}
