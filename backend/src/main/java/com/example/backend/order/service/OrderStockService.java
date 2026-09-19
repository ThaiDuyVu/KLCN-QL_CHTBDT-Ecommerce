package com.example.backend.order.service;
import java.util.Map;
import java.util.UUID;
public interface OrderStockService {
    void apply(Map<UUID, Integer> quantities, Action action);
    enum Action { RESERVE, RELEASE, DELIVER }
}
