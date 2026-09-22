package com.example.backend.inventory.service;

import com.example.backend.inventory.dto.InventoryPageResponse;
import com.example.backend.inventory.dto.InventoryResponse;
import com.example.backend.inventory.entity.Inventory;
import com.example.backend.inventory.exception.InventoryNotFoundException;
import com.example.backend.inventory.repository.InventoryRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {
    private static final int MAX_PAGE_SIZE = 100;
    private final InventoryRepository inventoryRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public InventoryPageResponse getInventory(String keyword, UUID warehouseId, UUID variantId, int page, int size) {
        validatePage(page, size);
        Page<Inventory> result = inventoryRepository.findAll(specification(keyword, warehouseId, variantId),
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("warehouse.warehouseName"), Sort.Order.asc("variant.sku"))));
        return new InventoryPageResponse(result.getContent().stream().map(this::map).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public InventoryResponse getInventoryById(UUID inventoryId) {
        return map(inventoryRepository.findById(inventoryId).orElseThrow(() ->
                new InventoryNotFoundException("Không tìm thấy tồn kho với ID: " + inventoryId)));
    }

    private Specification<Inventory> specification(String keyword, UUID warehouseId, UUID variantId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (warehouseId != null) predicates.add(cb.equal(root.get("warehouse").get("warehouseId"), warehouseId));
            if (variantId != null) predicates.add(cb.equal(root.get("variant").get("variantId"), variantId));
            if (keyword != null && !keyword.isBlank()) {
                String term = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("variant").get("sku")), term),
                        cb.like(cb.lower(root.get("variant").get("product").get("productName")), term),
                        cb.like(cb.lower(root.get("warehouse").get("warehouseName")), term)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Phân trang không hợp lệ; size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
    }

    private InventoryResponse map(Inventory item) {
        return new InventoryResponse(item.getInventoryId(), item.getWarehouse().getWarehouseId(),
                item.getWarehouse().getWarehouseName(), item.getVariant().getVariantId(), item.getVariant().getSku(),
                item.getVariant().getProduct().getProductName(), item.getVariant().getTrackingType(),
                item.getQuantity(), item.getReservedQuantity(), item.getQuantity() - item.getReservedQuantity(), item.getUpdatedAt());
    }
}
