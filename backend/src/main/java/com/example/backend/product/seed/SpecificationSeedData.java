package com.example.backend.product.seed;

import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.dto.SpecificationRequest;
import com.example.backend.product.entity.Product;
import com.example.backend.product.repository.SpecificationRepository;
import com.example.backend.product.service.SpecificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(60)
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.specification.enabled", havingValue = "true")
public class SpecificationSeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SpecificationSeedData.class);
    private final ProductRepository productRepository;
    private final SpecificationRepository specificationRepository;
    private final SpecificationService specificationService;

    public SpecificationSeedData(ProductRepository productRepository, SpecificationRepository specificationRepository,
                                 SpecificationService specificationService) {
        this.productRepository = productRepository;
        this.specificationRepository = specificationRepository;
        this.specificationService = specificationService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Illustrative dev configurations; no variant-level configuration rules are imposed.
        int created = 0;
        created += seedProduct("iPhone 15", "Apple", "Điện thoại",
                "Apple A16 Bionic", "6 GB", "OLED 6,1 inch", "Pin tích hợp, sạc qua USB-C");
        created += seedProduct("Samsung Galaxy S24", "Samsung", "Điện thoại",
                "CPU 8 nhân", "8 GB", "AMOLED 6,2 inch", "4.000 mAh");
        created += seedProduct("Xiaomi Redmi Note 13", "Xiaomi", "Điện thoại",
                "CPU 8 nhân", "6 GB", "AMOLED 6,67 inch", "5.000 mAh");
        created += seedProduct("MacBook Air M2", "Apple", "Laptop",
                "Apple M2", "8 GB", "13,6 inch", "Pin tích hợp, sạc qua USB-C hoặc MagSafe");
        created += seedProduct("Dell Inspiron 15", "Dell", "Laptop",
                "Intel Core i5", "16 GB", "Full HD 15,6 inch", "Pin tích hợp");
        created += seedProduct("ASUS Vivobook 15", "ASUS", "Laptop",
                "Intel Core i5", "8 GB", "Full HD 15,6 inch", "Pin tích hợp");
        log.info("Development specification seed finished: {} new specifications", created);
    }

    private int seedProduct(String productName, String brandName, String categoryName,
                            String cpu, String ram, String screen, String battery) {
        Product product = productRepository
                .findFirstByProductNameAndBrand_BrandNameAndCategory_CategoryNameAndCategory_Parent_CategoryNameAndCategory_Parent_ParentIsNullOrderByProductIdAsc(
                        productName, brandName, categoryName,
                        "Điện thoại".equals(categoryName) ? "Điện thoại & Máy tính bảng" : "Máy tính & Laptop"
                ).orElse(null);
        if (product == null) {
            log.warn("Specification seed skipped: product '{}' is missing. "
                    + "Enable app.seed.product.enabled and its Category/Brand seeds in dev first", productName);
            return 0;
        }
        int created = createIfMissing(product, "CPU", cpu);
        created += createIfMissing(product, "RAM", ram);
        created += createIfMissing(product, "Màn hình", screen);
        return created + createIfMissing(product, "Pin", battery);
    }

    private int createIfMissing(Product product, String key, String value) {
        if (specificationRepository.existsByProduct_ProductIdAndSpecKey(product.getProductId(), key)) {
            return 0;
        }
        SpecificationRequest request = new SpecificationRequest();
        request.setSpecKey(key);
        request.setSpecValue(value);
        specificationService.createSpecification(product.getProductId(), request);
        return 1;
    }
}
