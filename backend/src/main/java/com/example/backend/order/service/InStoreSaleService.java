package com.example.backend.order.service;

import com.example.backend.order.dto.InStoreSaleRequest;
import com.example.backend.order.dto.OrderResponse;

import java.util.Collection;
import java.util.UUID;

public interface InStoreSaleService {
    /** Sells at the counter: stock and devices leave the branch, cash is paid, warranty and invoice are issued at once. */
    OrderResponse sell(UUID userId, InStoreSaleRequest request, Collection<String> authorities);
}
