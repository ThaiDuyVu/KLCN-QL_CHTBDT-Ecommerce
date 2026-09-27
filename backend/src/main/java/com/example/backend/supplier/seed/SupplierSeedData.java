package com.example.backend.supplier.seed;

import com.example.backend.common.seed.DevSeedData;
import com.example.backend.supplier.entity.Supplier;
import com.example.backend.supplier.entity.SupplierStatus;
import com.example.backend.supplier.repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(36)
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.supplier.enabled", havingValue = "true")
public class SupplierSeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SupplierSeedData.class);
    private final SupplierRepository suppliers;

    public SupplierSeedData(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (suppliers.findBySupplierCode(DevSeedData.SUPPLIER_CODE).isPresent()) {
            log.info("Development supplier seed already exists: {}", DevSeedData.SUPPLIER_CODE);
            return;
        }
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(DevSeedData.SUPPLIER_CODE);
        supplier.setSupplierName("Nhà cung cấp thiết bị Điện Việt");
        supplier.setPhone("0900000001");
        supplier.setEmail("nhacungcap.dev@example.test");
        supplier.setAddress("Thành phố Hồ Chí Minh");
        supplier.setStatus(SupplierStatus.ACTIVE);
        suppliers.save(supplier);
        log.info("Development supplier seed created: {}", DevSeedData.SUPPLIER_CODE);
    }
}
