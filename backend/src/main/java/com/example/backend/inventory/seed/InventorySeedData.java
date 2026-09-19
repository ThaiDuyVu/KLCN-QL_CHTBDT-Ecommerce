package com.example.backend.inventory.seed;

import com.example.backend.inventory.entity.Inventory;
import com.example.backend.inventory.repository.InventoryRepository;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.warehouse.entity.Warehouse;
import com.example.backend.warehouse.entity.WarehouseStatus;
import com.example.backend.warehouse.repository.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(45)
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.inventory.enabled", havingValue = "true")
public class InventorySeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InventorySeedData.class);
    private static final String WAREHOUSE_NAME = "Kho phát triển";
    private static final int INITIAL_QUANTITY = 20;
    private static final List<String> SEEDED_SKUS = List.of(
            "DEV-IP15-BLK-128", "DEV-IP15-BLU-256", "DEV-S24-GRY-256", "DEV-S24-BLK-256",
            "DEV-RN13-BLK-128", "DEV-MBA-M2-256", "DEV-DELL-I15-512", "DEV-ASUS-V15-512"
    );

    private final ProductVariantRepository variants;
    private final WarehouseRepository warehouses;
    private final InventoryRepository inventory;

    public InventorySeedData(ProductVariantRepository variants, WarehouseRepository warehouses,
                             InventoryRepository inventory) {
        this.variants = variants;
        this.warehouses = warehouses;
        this.inventory = inventory;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<ProductVariant> seededVariants = new ArrayList<>();
        for (String sku : SEEDED_SKUS) variants.findBySku(sku).ifPresent(seededVariants::add);
        if (seededVariants.isEmpty()) {
            log.warn("Inventory seed skipped: ProductVariant seed data is missing. Enable app.seed.product-variant.enabled first");
            return;
        }

        Warehouse warehouse = warehouses.findFirstByWarehouseNameOrderByWarehouseIdAsc(WAREHOUSE_NAME)
                .orElseGet(() -> {
                    Warehouse created = new Warehouse();
                    created.setWarehouseName(WAREHOUSE_NAME);
                    created.setAddress("Dữ liệu mẫu cho môi trường phát triển");
                    created.setStatus(WarehouseStatus.ACTIVE);
                    return warehouses.saveAndFlush(created);
                });

        int created = 0;
        for (ProductVariant variant : seededVariants) {
            if (inventory.findByWarehouse_WarehouseIdAndVariant_VariantId(
                    warehouse.getWarehouseId(), variant.getVariantId()).isPresent()) continue;
            Inventory row = new Inventory();
            row.setWarehouse(warehouse);
            row.setVariant(variant);
            row.setQuantity(INITIAL_QUANTITY);
            row.setReservedQuantity(0);
            inventory.save(row);
            created++;
        }
        inventory.flush();
        log.info("Development inventory seed finished: {} new rows with quantity {}", created, INITIAL_QUANTITY);
    }
}
