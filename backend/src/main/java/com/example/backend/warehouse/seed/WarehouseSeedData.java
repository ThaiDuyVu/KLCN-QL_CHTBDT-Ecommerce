package com.example.backend.warehouse.seed;

import com.example.backend.common.seed.DevSeedData;
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

@Component
@Order(35)
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.warehouse.enabled", havingValue = "true")
public class WarehouseSeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WarehouseSeedData.class);
    private final WarehouseRepository warehouses;

    public WarehouseSeedData(WarehouseRepository warehouses) {
        this.warehouses = warehouses;
    }

    @Override
    @Transactional
    public void run(String... args) {
        createIfMissing(DevSeedData.WAREHOUSE_NAME, "Kho dữ liệu mẫu tại Thành phố Hồ Chí Minh");
        createIfMissing(DevSeedData.SECONDARY_WAREHOUSE_NAME,
                "Chi nhánh không có tồn mẫu, dùng kiểm thử availability theo warehouse");
    }

    private void createIfMissing(String name, String address) {
        if (warehouses.findFirstByWarehouseNameOrderByWarehouseIdAsc(name).isPresent()) {
            log.info("Development warehouse seed already exists: {}", name);
            return;
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseName(name);
        warehouse.setAddress(address);
        warehouse.setStatus(WarehouseStatus.ACTIVE);
        warehouses.save(warehouse);
        log.info("Development warehouse seed created: {}", name);
    }
}
