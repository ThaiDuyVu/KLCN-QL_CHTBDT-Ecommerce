package com.example.backend.inventory.serial.service;

import com.example.backend.inventory.serial.dto.*;
import com.example.backend.inventory.serial.entity.SerialNumber;
import com.example.backend.inventory.serial.entity.SerialStatus;
import com.example.backend.inventory.serial.exception.SerialNotFoundException;
import com.example.backend.inventory.serial.repository.SerialNumberRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class SerialQueryServiceImpl implements SerialQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private final SerialNumberRepository repository;
    public SerialQueryServiceImpl(SerialNumberRepository repository) { this.repository = repository; }

    @Override
    public SerialPageResponse search(String keyword, UUID variantId, UUID warehouseId, SerialStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Phân trang không hợp lệ; size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        Page<SerialNumber> result = repository.findAll(spec(keyword, variantId, warehouseId, status),
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("serialNumber"))));
        return new SerialPageResponse(result.getContent().stream().map(this::map).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public SerialResponse getById(UUID serialId) {
        return map(repository.findById(serialId).orElseThrow(() ->
                new SerialNotFoundException("Không tìm thấy serial với ID: " + serialId)));
    }

    @Override
    public SerialResponse lookup(String code) {
        String normalized = code == null ? "" : code.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Serial/IMEI không được để trống");
        SerialNumber serial = repository.findBySerialNumber(normalized)
                .or(() -> repository.findFirstByImeis_ImeiNumber(normalized))
                .orElseThrow(() -> new SerialNotFoundException("Không tìm thấy thiết bị với serial/IMEI: " + normalized));
        return map(serial);
    }

    private Specification<SerialNumber> spec(String keyword, UUID variantId, UUID warehouseId, SerialStatus status) {
        return (root, query, cb) -> {
            List<Predicate> values = new ArrayList<>();
            if (variantId != null) values.add(cb.equal(root.get("variant").get("variantId"), variantId));
            if (warehouseId != null) values.add(cb.equal(root.get("warehouse").get("warehouseId"), warehouseId));
            if (status != null) values.add(cb.equal(root.get("status"), status));
            if (keyword != null && !keyword.isBlank()) {
                query.distinct(true);
                String term = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                values.add(cb.or(
                        cb.like(cb.lower(root.get("serialNumber")), term),
                        cb.like(cb.lower(root.join("imeis", JoinType.LEFT).get("imeiNumber")), term),
                        cb.like(cb.lower(root.get("variant").get("sku")), term),
                        cb.like(cb.lower(root.get("variant").get("product").get("productName")), term)
                ));
            }
            return cb.and(values.toArray(Predicate[]::new));
        };
    }

    private SerialResponse map(SerialNumber serial) {
        return new SerialResponse(serial.getSerialId(), serial.getSerialNumber(), serial.getStatus(),
                serial.getVariant().getVariantId(), serial.getVariant().getSku(), serial.getVariant().getProduct().getProductName(),
                serial.getVariant().getTrackingType(), serial.getWarehouse().getWarehouseId(), serial.getWarehouse().getWarehouseName(),
                serial.getImeis().stream().map(i -> new SerialImeiResponse(i.getImeiId(), i.getImeiNumber())).toList());
    }
}
