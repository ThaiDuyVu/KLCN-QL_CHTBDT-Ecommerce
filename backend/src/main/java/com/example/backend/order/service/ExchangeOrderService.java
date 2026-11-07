package com.example.backend.order.service;

import com.example.backend.order.entity.Order;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface ExchangeOrderService {
    /** difference = replacement total - credit (positive: customer pays, negative: store refunds). */
    record Result(Order order, BigDecimal difference) {}

    /**
     * Creates the replacement order of an exchange, priced today, at the original order's branch.
     * Online originals start CONFIRMED and are delivered as usual (the difference is collected as COD);
     * counter originals are completed at once (difference paid in cash).
     */
    Result create(Order original, String returnCode, BigDecimal credit, Map<UUID, Integer> quantities, UUID actorUserId);
}
