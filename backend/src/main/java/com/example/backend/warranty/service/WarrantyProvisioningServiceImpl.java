package com.example.backend.warranty.service;

import com.example.backend.order.entity.Order;
import com.example.backend.order.entity.OrderItem;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.warranty.entity.*;
import com.example.backend.warranty.exception.WarrantyException;
import com.example.backend.warranty.repository.WarrantyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WarrantyProvisioningServiceImpl implements WarrantyProvisioningService {
    private final WarrantyRepository warranties;
    private final ProductVariantRepository variants;

    public WarrantyProvisioningServiceImpl(WarrantyRepository warranties, ProductVariantRepository variants) {
        this.warranties = warranties;
        this.variants = variants;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void createForDeliveredOrder(Order order, List<OrderItem> items) {
        Map<UUID, ProductVariant> variantMap = variants.findByVariantIdIn(
                items.stream().map(OrderItem::getVariantId).distinct().toList()
        ).stream().collect(Collectors.toMap(ProductVariant::getVariantId, variant -> variant));
        LocalDate deliveredDate = LocalDate.now();
        List<Warranty> created = new ArrayList<>();
        for (OrderItem item : items) {
            if (item.getSerialId() == null) continue;
            Optional<Warranty> existing = warranties.findBySerialId(item.getSerialId());
            if (existing.isPresent()) {
                Warranty warranty = existing.get();
                if (!warranty.getOrderItemId().equals(item.getOrderItemId())
                        || !warranty.getCustomerId().equals(order.getCustomerId())) {
                    throw new WarrantyException(409, "Serial đã gắn với một Warranty không khớp OrderItem/Customer");
                }
                continue;
            }
            ProductVariant variant = variantMap.get(item.getVariantId());
            if (variant == null) throw new WarrantyException(409, "Không tìm thấy variant để tạo bảo hành");
            int months = variant.getWarrantyMonths() == null ? 0 : variant.getWarrantyMonths();
            if (months <= 0) continue;
            Warranty warranty = new Warranty();
            warranty.setSerialId(item.getSerialId());
            warranty.setOrderItemId(item.getOrderItemId());
            warranty.setCustomerId(order.getCustomerId());
            warranty.setStartDate(deliveredDate);
            warranty.setEndDate(deliveredDate.plusMonths(months));
            warranty.setStatus(WarrantyStatus.ACTIVE);
            created.add(warranty);
        }
        if (!created.isEmpty()) warranties.saveAllAndFlush(created);
    }
}
