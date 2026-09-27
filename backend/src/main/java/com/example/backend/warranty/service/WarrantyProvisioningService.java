package com.example.backend.warranty.service;

import com.example.backend.order.entity.Order;
import com.example.backend.order.entity.OrderItem;
import java.util.List;

public interface WarrantyProvisioningService {
    void createForDeliveredOrder(Order order, List<OrderItem> items);
}
