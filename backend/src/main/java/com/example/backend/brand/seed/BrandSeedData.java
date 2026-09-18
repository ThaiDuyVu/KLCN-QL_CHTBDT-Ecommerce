package com.example.backend.brand.seed;

import com.example.backend.product.entity.BrandStatus;
import com.example.backend.product.BrandRepository;
import com.example.backend.product.entity.Brand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.brand.enabled", havingValue = "true")
public class BrandSeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BrandSeedData.class);
    private final BrandRepository brandRepository;

    public BrandSeedData(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        created += createIfMissing("Apple", "Điện thoại, máy tính bảng và máy tính Apple");
        created += createIfMissing("Samsung", "Điện thoại, máy tính bảng và thiết bị điện tử Samsung");
        created += createIfMissing("Xiaomi", "Điện thoại, thiết bị thông minh và phụ kiện Xiaomi");
        created += createIfMissing("Dell", "Laptop, máy tính để bàn và màn hình Dell");
        created += createIfMissing("ASUS", "Laptop, linh kiện máy tính và thiết bị ASUS");
        brandRepository.flush();
        log.info("Development brand seed finished: {} new brands", created);
    }

    private int createIfMissing(String name, String description) {
        if (brandRepository.existsByBrandName(name)) {
            return 0;
        }
        Brand brand = new Brand();
        brand.setBrandName(name);
        brand.setDescription(description);
        brand.setStatus(BrandStatus.ACTIVE);
        brandRepository.save(brand);
        return 1;
    }
}
