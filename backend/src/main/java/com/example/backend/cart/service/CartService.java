package com.example.backend.cart.service;
import com.example.backend.cart.dto.*;
import java.util.UUID;
public interface CartService {
    CartResponse get(UUID userId);
    CartResponse selectWarehouse(UUID userId, SelectCartWarehouseRequest request);
    CartResponse add(UUID userId, CartItemRequest request);
    CartResponse quantity(UUID userId, UUID itemId, UpdateCartQuantityRequest request);
    CartResponse remove(UUID userId, UUID itemId);
}
